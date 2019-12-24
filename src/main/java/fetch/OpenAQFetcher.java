package main.java.fetch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OpenAQFetcher {
    private static OpenAQFetcher INSTANCE = new OpenAQFetcher();
    public static OpenAQFetcher getInstance() {
        return INSTANCE;
    }

    // Format example: 2019-12-22T00:00:00+01:00
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public JSONObject getPollutionMeasurements(String country, String city, LocalDateTime fromDate, LocalDateTime toDate) throws IOException {
        //https://api.openaq.org/v1/measurements?coordinates=41.902782,12.4963&date_from=2019-12-21&date_to=2019-12-23
        String jsonString =
                FetchUtils.doGet("https://api.openaq.org/v1/measurements?"+
                        "country=" + URLEncoder.encode(country, "UTF-8") +
                        "&city=" + URLEncoder.encode(city, "UTF-8") +
                        "&date_from=" + URLEncoder.encode(fromDate.format(DATE_TIME_FORMATTER), "UTF-8") +
                        "&date_to=" + URLEncoder.encode(toDate.format(DATE_TIME_FORMATTER), "UTF-8") +
                        "&limit=" + 10000);
        return new JSONObject(jsonString);
    }

    public JSONObject getPollutionMeasurements(Double lat, Double lon, LocalDateTime fromDate, LocalDateTime toDate) throws IOException {
        //https://api.openaq.org/v1/measurements?country=IT&city=Roma&date_from=2019-12-21&date_to=2019-12-23
        String jsonString =
                FetchUtils.doGet("https://api.openaq.org/v1/measurements?coordinates="+lat+","+lon+
                        "&date_from=" + URLEncoder.encode(fromDate.format(DATE_TIME_FORMATTER), "UTF-8") +
                        "&date_to=" + URLEncoder.encode(toDate.format(DATE_TIME_FORMATTER), "UTF-8") +
                        "&limit=" + 10000);
        return new JSONObject(jsonString);
    }

    public JSONObject getAllCities() throws IOException {
        String jsonString = FetchUtils.doGet("https://api.openaq.org/v1/cities?limit=10000");
        return new JSONObject(jsonString);
    }

    public JSONObject getAllLocations() throws IOException {
        int limit = 10000;

        String jsonString = FetchUtils.doGet("https://api.openaq.org/v1/locations?page=1&limit=" + limit);
        JSONObject jsonDoc = new JSONObject(jsonString);
        JSONArray destinationArray = jsonDoc.getJSONArray("results");

        int found = jsonDoc.getJSONObject("meta").getInt("found");
        int requiredPages = (found + limit - 1) / limit;
        for(int i=2; i<=requiredPages; i++) {
            JSONArray nextJsonArray = new JSONObject(
                    FetchUtils.doGet("https://api.openaq.org/v1/locations?page=" + i + "&limit=" + limit)
            ).getJSONArray("results");

            for (int j=0; j < nextJsonArray.length(); j++)
                destinationArray.put(nextJsonArray.getJSONObject(j));
        }

        return jsonDoc;
    }

    public static void main(String[] args) throws IOException {
        /*OpenAQFetcher.getInstance()
                .getPollutionMeasurements(41.902782,12.4963, LocalDate.now().minusDays(4), LocalDate.now());
        OpenAQFetcher.getInstance()
                .getPollutionMeasurements("IT", "Roma", LocalDate.now().minusDays(4), LocalDate.now());*/
    }
}
