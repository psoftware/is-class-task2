package main.java.fetch;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.TransactionBody;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import main.java.City;
import main.java.db.MongoDBManager;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.time.*;
import java.util.*;

public class FetchAdapter {
    private static FetchAdapter INSTANCE = new FetchAdapter();
    public static FetchAdapter getInstance() {
        return INSTANCE;
    }

    public static final Map<String, String> WEATHERVALUES_NAMEMAP = new HashMap<String, String>() {
        {
            put("icon","sky");
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

    private List<Document> fetchWeatherData(City city, LocalDate day,
                                            JSONObject jsonDoc) throws IOException {

        List<Document> mongoHourlyList = new ArrayList<>();
        TimeZone timezone = TimeZone.getTimeZone(jsonDoc.getString("timezone"));

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

            LocalDateTime hourdatetime =
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(hourdata.getLong("time")), timezone.toZoneId());
            Document hourDoc = new Document("datetime", hourdatetime)
                    .append("measurements", mongoHourMeasureList);

            mongoHourlyList.add(hourDoc);
        }

        return mongoHourlyList;
    }

    public List<Document> fetchHistoricalData(City city, LocalDate day) throws IOException {
        // 1) Get hourly weather data for specified day
        JSONObject jsonDoc = DarkSkyFetcher.getInstance().getHistoricalWeather(city.getCoords().lat, city.getCoords().lon, day);
        return fetchWeatherData(city, day, jsonDoc);
    }

    public List<Document> fetchForecastData(City city, LocalDate day) throws IOException {
        // 1) Get hourly weather data for specified day
        JSONObject jsonDoc = DarkSkyFetcher.getInstance().getForecastWeather(city.getCoords().lat, city.getCoords().lon, day);
        return fetchWeatherData(city, day, jsonDoc);
    }

    public List<Document> fetchPollutionData(City city, LocalDate day) throws IOException {
        JSONObject jsonDoc = OpenAQFetcher.getInstance().getPollutionMeasurements(city.getCountry(), city.getCity(),
                day.atTime(0,0), day.atTime(23,59));

        LocalDateTime[] weekrange = FetchUtils.getWeekPeriod(day);
        LocalDateTime weekStart = weekrange[0];
        LocalDateTime weekEnd = weekrange[1];

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
                Document hourdoc = new Document("location", location).append("datetime", datetime)
                        .append("measurements", dateEntry.getValue());
                finalReadingList.add(hourdoc);
            }
        }

        return finalReadingList;
/*
        Document updatedoc = new Document()
                .append("$setOnInsert", new Document()
                        .append("country",city.getCountry()).append("city", city.getCity())
                        .append("coordinates", new Document("type", "point").append("coordinates", city.getCoords().asList()))
                        .append("periodStart", weekStart)
                        .append("periodEnd", weekEnd)
                        .append("enabled", true))  // TODO: Implement city enabling
                .append("$push", new Document()
                        .append("pollutionMeasurements", new Document("$each", finalReadingList)));

        // Create BSON Document
        Document query = new Document();
        query.append("country",city.getCountry()).append("city", city.getCity())
                .append("periodStart", weekStart)
                .append("periodEnd", weekEnd);

        // Update or insert (upsert) collection on MongoDB
        System.out.println(FetchUtils.toJson(query));
        System.out.println(FetchUtils.toJson(updatedoc));
        collection.updateOne(query, updatedoc, new UpdateOptions().upsert(true));

 */
    }

    // Use this method to generate city list to batch on geocode.xyz
    /*
    public List<City> fetchAllCitiesNames() throws IOException {
        List<City> resultList = new ArrayList<>();

        JSONObject jsonDoc = OpenAQFetcher.getInstance().getAllCities();
        JSONArray jsonCityList = jsonDoc.getJSONArray("results");
        for(int i=0; i<jsonCityList.length(); i++) {
            JSONObject jsonCity = jsonCityList.getJSONObject(i);
            String country = jsonCity.getString("country");
            String city = jsonCity.getString("city");

            // filter unuseful locations
            if(city.equals("N/A") || country.equals("N/A") || city.equals("unused"))
                continue;

            // Get coordinates from geoinfo
            // DON'T. It takes 5-10 sec per city and returns http bad errors at some point
            // JSONObject jsonGeoinfo = GeoCodeFetcher.getInstance().forwardGeocode(city +","+country);
            // Float confidence = jsonGeoinfo.getJSONObject("standard").getFloat("confidence");
            // if(confidence <= 0.4)
            //    continue;

            // Double latitude = jsonGeoinfo.getDouble("latt");
            // Double longitude = jsonGeoinfo.getDouble("longt");

            City newcity = new City(country, city, null);
            resultList.add(newcity);

            System.out.println(newcity);
        }

        return resultList;
    }

    public void dumpCitiesNames(String filepath) throws IOException {
        List<City> cityList = fetchAllCitiesNames();

        File fout = new File(filepath);
        FileOutputStream fos = new FileOutputStream(fout);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
        for(City city : cityList) {
            bw.write(city.getCity() + "," + city.getCountry());
            bw.newLine();
        }
        bw.close();
    }
    */

    public List<City> fetchAllCitiesAsList() throws IOException {
        class CoordsAvg {
            private double latSum=0, lonSum=0;
            private int count=0;
            public void add(City.Coords c){ latSum+=c.lat; lonSum+=c.lon; count++;}
            public City.Coords compute() { return new City.Coords(latSum/count, lonSum/count); }
        }

        List<City> resultList = new ArrayList<>();

        JSONObject jsonDoc = OpenAQFetcher.getInstance().getAllLocations();
        JSONArray jsonLocationList = jsonDoc.getJSONArray("results");
        HashMap<City.CityName, CoordsAvg> cityMap = new HashMap<>();
        for(int i=0; i<jsonLocationList.length(); i++) {
            JSONObject jsonLocation = jsonLocationList.getJSONObject(i);
            String country = jsonLocation.getString("country");
            String city = jsonLocation.getString("city");
            Double latitude = jsonLocation.getJSONObject("coordinates").getDouble("latitude");
            Double longitude = jsonLocation.getJSONObject("coordinates").getDouble("longitude");

            // filter unuseful locations
            if(city.equals("N/A") || country.equals("N/A") || city.equals("unused"))
                continue;

            City newcity = new City(country, city, null, new City.Coords(latitude, longitude));

            // TODO: Fix this design error. What coordinate set should we use if there are many locations in the same city?
            // For now lets take a centroid using avg on lat and lon
            if(!cityMap.containsKey(newcity.getCityName()))
                cityMap.put(newcity.getCityName(), new CoordsAvg());

            cityMap.get(newcity.getCityName()).add(newcity.getCoords());
        }

        for(Map.Entry<City.CityName, CoordsAvg> c : cityMap.entrySet())
            resultList.add(new City(c.getKey(), c.getValue().compute(), null));
        return resultList;
    }

    public List<Document> fetchAllCities() throws IOException {
        List<City> cityList = fetchAllCitiesAsList();
        List<Document> resList = new ArrayList<>();
        for(City city : cityList) {
            Document newDoc = new Document()
                    .append("country", city.getCountry())
                    .append("city", city.getCity())
                    .append("coordinates", new Document("type", "point").append("coordinates", city.getCoords().asList()))
                    .append("enabled", false)
                    .append("votes", Collections.emptyList());
            resList.add(newDoc);
        }

        return resList;
    }

    public static void main(String[] args) {
        JsonWriterSettings jsonWriterSettings = JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build();
        try {
            City cityRome = new City("IT", "Roma", null, new City.Coords(41.902782, 12.4963));
            //MongoCollection<Document> collection = MongoDBManager.getInstance().database.getCollection("measureswpoll");

            Document mongoDoc;
            //System.out.println("Test 1 (fetchHistoricalData):");
            //FetchAdapter.getInstance().fetchPollutionData(collection, cityRome, LocalDate.now().minusDays(10));
            //System.out.println(mongoDoc.toJson(jsonWriterSettings));

            System.out.println("Test 2 (fetchForecastData):");
            //FetchAdapter.getInstance().fetchForecastData(collection, cityRome, LocalDate.now());
            //System.out.println(mongoDoc.toJson(jsonWriterSettings));

            //System.out.println("Test 3 (fetchPollutionData):");
            //mongoDoc = FetchAdapter.getInstance().fetchPollutionData(cityRome, LocalDate.now().minusDays(2));
            //System.out.println(mongoDoc.toJson(jsonWriterSettings));

            //System.out.println("Test 4 (fetchAllCities):");
            List<Document> cityList = FetchAdapter.getInstance().fetchAllCities();
            cityList.forEach(d -> System.out.println(d.toJson(jsonWriterSettings)));

        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}
