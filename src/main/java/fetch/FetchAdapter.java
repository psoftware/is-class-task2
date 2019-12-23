package main.java.fetch;

import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
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

    public void fetchForecastData(LocalDate day) throws IOException {
        // 1) Get hourly weather data for specified day
        JSONObject jsonDoc = DarkSkyFetcher.getInstance().getDailyForecast(43.716667,10.40);

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

        // Create BSON Document
        Document mongoDoc = new Document();
        mongoDoc.append("country","IT").append("city", "Pisa")
                .append("coordinates", new Document("type", "point").append("coordinates", Arrays.asList(43.716667, 10.400000)))
                .append("enabled", false)
                .append("periodStart","22-12-19") // TODO: implement per week documents
                .append("periodEnd","29-12-19")
                .append("weatherForecast", mongoHourlyList);


        // 3) Submit into database without duplicates
        mongoDoc.toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build());
    }

    public static void main(String[] args) throws IOException {
        FetchAdapter.getInstance().fetchForecastData(LocalDate.now());
    }
}
