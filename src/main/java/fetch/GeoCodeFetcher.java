package main.java.fetch;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;

public class GeoCodeFetcher {
    private static GeoCodeFetcher INSTANCE = new GeoCodeFetcher();
    public static GeoCodeFetcher getInstance() {
        return INSTANCE;
    }

    private GeoCodeFetcher() {}

    public JSONObject forwardGeocode(String citystring) throws IOException {
        String jsonString = FetchUtils.doGet("https://geocode.xyz/" +
                URLEncoder.encode(citystring,"UTF-8") + "?json=1");
        /*try {
            // Free account limited to 1 req per sec
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/
        return new JSONObject(jsonString);
    }
}
