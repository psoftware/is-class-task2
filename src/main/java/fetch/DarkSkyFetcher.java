package main.java.fetch;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class DarkSkyFetcher {
    private static DarkSkyFetcher INSTANCE = new DarkSkyFetcher();
    public static DarkSkyFetcher getInstance() {
        return INSTANCE;
    }

    public static final Map<String, String> MEASURE_UNITS  = new HashMap<String, String>() {
        {
            put("sky", "literal");
            put("precipIntensity", "mm/h");
            put("precipProbability", "");
            put("apparentTemperature", "°C");
            put("temperature", "°C");
            put("dewPoint", "°C");
            put("humidity", "percent");
            put("pressure", "hPa");
            put("windSpeed", "m/s");
            put("windGust", "m/s");
            put("windBearing", "°");
            put("cloudCover", "%");
            put("uvIndex", "");
            put("visibility", "km");
            put("ozone", "");
        }
    };

    private String apiKey = "";

    private static final boolean DEBUG_MODE = true;

    private DarkSkyFetcher() {

    }

    private static String readResource(String filename) {
        return new Scanner(DarkSkyFetcher.class.getClassLoader().getResourceAsStream("darkskyexample.json"),
                "UTF-8").useDelimiter("\\A").next();
    }

    /*private static String readFile(String path) throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        getClass().getClassLoader().getResourceAsStream("asd").rea;
        new String();
        return new String(encoded, Charset.defaultCharset());
    }*/

    private String doGet(String url) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        int responseCode = con.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) { // success
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // print result
            return response.toString();
        } else {
            throw new IllegalStateException("Got HTTP unexpected status");
        }
    }

    public JSONObject getDailyForecast(Double latitude, Double longitude) throws IOException {
        /*if(day.compareTo(LocalDate.now()) < 0)
            throw new IllegalArgumentException("Cannot fetch forecast of past days");*/
        // Do HTTP Request
        String jsonString;
        if(DEBUG_MODE)
            jsonString = readResource("darkskyexample.json");
        else
            jsonString = doGet("https://api.darksky.net/forecast/"+apiKey+"/"+latitude+","+longitude+"?units=si");

        // Parse JSON
        return new JSONObject(jsonString);
    }

    public JSONObject getHistoricalWeather(Double latitude, Double longitude, LocalDate day) throws IOException {
        if(day.compareTo(LocalDate.now()) >= 0)
            throw new IllegalArgumentException("Cannot fetch real weather of future days");

        // Do HTTP Request
        String jsonString;
        if(DEBUG_MODE)
            jsonString = readResource("darkskyexample.json");
        else
            jsonString =
                    doGet("https://api.darksky.net/forecast/"+apiKey+"/"+latitude+","+longitude+","+day.toEpochDay()+"?units=si");

        // Parse JSON
        return new JSONObject(jsonString);
    }
}
