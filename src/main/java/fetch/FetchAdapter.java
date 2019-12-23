package main.java.fetch;

import main.java.City;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class FetchAdapter {
    private static FetchAdapter INSTANCE = new FetchAdapter();
    public static FetchAdapter getInstance() {
        return INSTANCE;
    }

    public static final Map<String, String> WEATHERVALUES_NAMEMAP = new HashMap<String, String>() {
        {
            put("sky","sky");
            put("precipIntensity","precipIntensity");
            put("precipProbability","precipProbability");
            put("apparentTemperature","apparentTemperature");
            put("temperature","temperature");
            put("dewPoint","dewPoint");
            put("humidity","humidity");
            put("pressure","pressure");
            put("windSpeed","windSpeed");
            put("windGust","windGust");
            put("windBearing","windBearing");
            put("cloudCover","cloudCover");
            put("uvIndex","uvIndex");
            put("visibility","visibility");
            put("ozone","ozone");
        }
    };

    private <T> Document measureToDocument(String name, T measurement, String uom) {
        return new Document().append("name", name).append("value", measurement).append("unit", uom);
    }

    private Document fetchWeatherData(City city, JSONObject jsonDoc, String arrayname) throws IOException {
        // 2) Covert it into a MongoDB BSON document, formatted as required
        List<Document> mongoHourlyList = new ArrayList<>();

        JSONArray jsonHourlyList = jsonDoc.getJSONObject("hourly").getJSONArray("data");
        for(int i=0; i<jsonHourlyList.length(); i++) {
            JSONObject hourdata = jsonHourlyList.getJSONObject(i);

            // Get all weather measures for current hour
            List<Document> mongoHourMeasureList = new ArrayList<>();
            for(String attributename : hourdata.keySet())
                // use name map also as a filter
                if(WEATHERVALUES_NAMEMAP.containsKey(attributename)) {
                    mongoHourMeasureList.add(measureToDocument(
                            WEATHERVALUES_NAMEMAP.get(attributename),
                            hourdata.get(attributename),
                            DarkSkyFetcher.MEASURE_UNITS.get(attributename)));
                }

            Document hourDoc = new Document("datetime", hourdata.getLong("time"))
                    .append("measurements", mongoHourMeasureList);

            mongoHourlyList.add(hourDoc);
        }

        // TODO: we should check if target coordinates are not too far from response coordinates

        // Create BSON Document
        Document mongoDoc = new Document();
        mongoDoc.append("country",city.getCountry()).append("city", city.getCity())
                .append("coordinates", new Document("type", "point").append("coordinates", city.getCoords().asList()))
                .append("enabled", false)
                .append("periodStart","22-12-19") // TODO: implement per week documents
                .append("periodEnd","29-12-19")
                .append(arrayname, mongoHourlyList);


        // 3) Submit into database without duplicates
        //mongoDoc.toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build());
        return mongoDoc;
    }

    public Document fetchHistoricalData(LocalDate day, City city) throws IOException {
        // 1) Get hourly weather data for specified day
        JSONObject jsonDoc = DarkSkyFetcher.getInstance().getHistoricalWeather(city.getCoords().lat, city.getCoords().lat, day);
        return fetchWeatherData(city, jsonDoc, "weatherCondition");
    }

    public Document fetchForecastData(City city) throws IOException {
        // 1) Get hourly weather data for specified day
        JSONObject jsonDoc = DarkSkyFetcher.getInstance().getDailyForecast(city.getCoords().lat, city.getCoords().lat);
        return fetchWeatherData(city, jsonDoc, "weatherForecast");
    }

    public Document fetchPollutionData(City city, LocalDate day) throws IOException {
        JSONObject jsonDoc = OpenAQFetcher.getInstance().getPollutionMeasurements(city.getCountry(), city.getCity(),
                        day.atTime(0,0), day.atTime(23,59));

        // Map<SensorLocation, <Datetime, List<Measures>>>
        HashMap<String, HashMap<LocalDateTime, List<Document>>> locationToDateMeasureMap
                = new HashMap<>();

        // Iterate over results and group readings by sensorlocation and datetime
        JSONArray jsonMeasureList = jsonDoc.getJSONArray("results");
        for(int i=0; i<jsonMeasureList.length(); i++) {
            JSONObject hourdata = jsonMeasureList.getJSONObject(i);
            String parameter = hourdata.getString("parameter");
            String uof = hourdata.getString("unit");
            Float value = hourdata.getFloat("value");
            String sensorLocation = hourdata.getString("location");
            Document measureDoc = new Document().append("name", parameter)
                    .append("unit", uof)
                    .append("value", value);

            // Insert document into hashmap (with composite key sensorlocation-datetime)
            // create sub-hashmap or list if needed
            LocalDateTime datetime = LocalDateTime.parse(hourdata.getJSONObject("date").getString("utc"),
                    OpenAQFetcher.DATE_TIME_FORMATTER);
            if(!locationToDateMeasureMap.containsKey(sensorLocation))
                locationToDateMeasureMap.put(sensorLocation, new HashMap<>());
            if(!locationToDateMeasureMap.get(sensorLocation).containsKey(datetime))
                locationToDateMeasureMap.get(sensorLocation).put(datetime, new ArrayList<>());
            locationToDateMeasureMap.get(sensorLocation).get(datetime).add(measureDoc);
        }

        // Iterate hashmap by sensorlocation first, then by datetime
        // for each group create one document with all grouped readings
        List<Document> finalReadingList = new ArrayList<>();
        for(Map.Entry<String, HashMap<LocalDateTime, List<Document>>> locationEntry : locationToDateMeasureMap.entrySet()) {
            String location = locationEntry.getKey();
            for (Map.Entry<LocalDateTime, List<Document>> dateEntry : locationEntry.getValue().entrySet()) {
                LocalDateTime datetime = dateEntry.getKey();
                Document hourdoc = new Document("location", location).append("datetime", datetime.toString())
                        .append("measurements", dateEntry.getValue());
                finalReadingList.add(hourdoc);
            }
        }

        // Create BSON Document
        Document mongoDoc = new Document();
        mongoDoc.append("country",city.getCountry()).append("city", city.getCity())
                .append("coordinates", new Document("type", "point").append("coordinates", city.getCoords().asList()))
                .append("enabled", false)
                .append("periodStart", day.toString()) // TODO: implement per week documents
                .append("periodEnd", day.toString())
                .append("pollutionMeasurements", finalReadingList);

        return mongoDoc;
    }

    public static void main(String[] args) throws IOException {
        City cityRome = new City("IT", "Roma", new City.Coords(41.902782,12.4963));

        Document mongoDoc;
        System.out.println("Test 1 (fetchHistoricalData):");
        mongoDoc = FetchAdapter.getInstance().fetchHistoricalData(LocalDate.now().minusDays(1), cityRome);
        System.out.println(mongoDoc.toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build()));

        System.out.println("Test 2 (fetchForecastData):");
        mongoDoc = FetchAdapter.getInstance().fetchForecastData(cityRome);
        System.out.println(mongoDoc.toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build()));

        System.out.println("Test 3 (fetchPollutionData):");
        mongoDoc = FetchAdapter.getInstance().fetchPollutionData(cityRome, LocalDate.now().minusDays(2));
        System.out.println(mongoDoc.toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build()));
    }
}
