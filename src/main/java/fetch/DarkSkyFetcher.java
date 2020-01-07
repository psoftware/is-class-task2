package main.java.fetch;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DarkSkyFetcher {
    private static DarkSkyFetcher INSTANCE = new DarkSkyFetcher();
    public static DarkSkyFetcher getInstance() {
        return INSTANCE;
    }

    public static final Map<String, String> MEASURE_UNITS  = new HashMap<String, String>() {
        {
            put("sky", "");
            put("precipIntensity", "mm/h");
            put("precipProbability", "");
            put("apparentTemperature", "°C");
            put("temperature", "°C");
            put("dewPoint", "°C");
            put("humidity", "%");
            put("pressure", "hPa");
            put("windSpeed", "m/s");
            put("windGust", "m/s");
            put("windBearing", "°");
            put("cloudCover", "%");
            put("uvIndex", "");
            put("visibility", "km");
            put("ozone", "DU");
        }
    };

    private String apiKey = "";
    private boolean debugMode = false;
    private boolean useLocalCache = false;
    private LocalCache localCache;

    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public void setDebugMode(boolean active) { this.debugMode = active; }
    public void enableLocalCache(boolean active) { this.useLocalCache = active; }

    private DarkSkyFetcher() {
        try {
            this.localCache = new LocalCache("target/localcache");
        } catch (IOException e) {
            System.out.println("Cannot create local cache:");
            e.printStackTrace();
        }
    }

    public JSONObject getTodayForecast(Double latitude, Double longitude) throws IOException {
        // Do HTTP Request
        String jsonString;
        if(debugMode)
            jsonString = FetchUtils.readResource("darkskyexample.json");
        else
            jsonString = FetchUtils.doGet("https://api.darksky.net/forecast/"+apiKey+"/"+latitude+","+longitude+"?units=si");

        // Parse JSON
        return new JSONObject(jsonString);
    }

    private JSONObject getTimeMachineData(Double latitude, Double longitude, LocalDate day) throws IOException {
        long timestamp = Timestamp.valueOf(day.atStartOfDay()).toInstant().getEpochSecond();

        // If enabled, get data from cache
        if(useLocalCache && localCache != null) {
            JSONObject result = localCache.getTimeMachineData(latitude, longitude, timestamp);
            if(result != null)
                return result;
        }

        // Do HTTP Request
        String jsonString;
        if(debugMode)
            jsonString = FetchUtils.readResource("darkskyexample.json");
        else {
            jsonString = FetchUtils.doGet("https://api.darksky.net/forecast/" + apiKey + "/" + latitude + "," + longitude + "," + timestamp + "?units=si");
            if(localCache != null)
                localCache.setTimeMachineData(latitude, longitude, timestamp, jsonString);
        }

        // Parse JSON
        return new JSONObject(jsonString);
    }

    public JSONObject getForecastWeather(Double latitude, Double longitude, LocalDate day) throws IOException {
        if(day.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Cannot fetch forecast of past days");

        return getTimeMachineData(latitude, longitude, day);
    }

    public JSONObject getHistoricalWeather(Double latitude, Double longitude, LocalDate day) throws IOException {
        if(day.isAfter(LocalDate.now()) || day.isEqual(LocalDate.now()))
            throw new IllegalArgumentException("Cannot fetch real weather of future days");

        return getTimeMachineData(latitude, longitude, day);
    }

    static class LocalCache {
        private String cachePath;
        public LocalCache(String cachePath) throws IOException {
            this.cachePath = cachePath;
            Files.createDirectories(Paths.get(cachePath));
        }

        public JSONObject getTimeMachineData(Double latitude, Double longitude, long timestamp) throws IOException {
            int hash = Objects.hash(latitude, longitude, timestamp);
            String filename = cachePath + File.separator + hash + ".json";
            if(!Files.exists(Paths.get(filename)))
                return null;
            String filecontent = FetchUtils.readFile(filename);
            return new JSONObject(filecontent);
        }

        public void setTimeMachineData(Double latitude, Double longitude, long timestamp, String json) throws IOException {
            int hash = Objects.hash(latitude, longitude, timestamp);
            // overwrite file if it exists already
            Files.write(Paths.get(cachePath + File.separator + hash + ".json"), json.getBytes(),
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        }
    }
}
