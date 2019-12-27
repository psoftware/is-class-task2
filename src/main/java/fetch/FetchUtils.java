package main.java.fetch;

import main.java.db.MongoDBManager;
import org.bson.Document;
import org.bson.codecs.DocumentCodec;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Scanner;

public class FetchUtils {
    private static final JsonWriterSettings JSON_WRITER_SETTINGS
            = JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build();
    private static final DocumentCodec DOCUMENT_CODEC = new DocumentCodec(MongoDBManager.CODEC_REGISTRY);

    public static String doGet(String url) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        int responseCode = con.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) { // success
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // print result
            return response.toString();
        } else {
            throw new IllegalStateException("Got HTTP unexpected status " + responseCode + ": " + con.getResponseMessage()
                    + "\nfor url " + url);
        }
    }

    public static String readResource(String filename) {
        return new Scanner(FetchUtils.class.getClassLoader().getResourceAsStream(filename),
                "UTF-8").useDelimiter("\\A").next();
    }

    public static LocalDateTime[] getWeekPeriod(LocalDate date) {
        return getWeekPeriod(LocalDateTime.of(date, LocalTime.of(0,0)));
    }

    public static LocalDateTime[] getWeekPeriod(LocalDateTime datetime) {
        return new LocalDateTime[]{ datetime.with(DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0),
                datetime.with(DayOfWeek.SUNDAY).withHour(23).withMinute(59).withSecond(59)};
    }

    public static String toJson(Document d) {
        return d.toJson(JSON_WRITER_SETTINGS, DOCUMENT_CODEC);
    }
}
