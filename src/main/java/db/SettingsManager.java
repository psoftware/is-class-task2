package main.java.db;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class SettingsManager {
    public static final SettingsManager MAINSETTINGS;

    static {
        SettingsManager tempSettings = null;
        try {
            tempSettings = new SettingsManager("target/settings.json", " { darksky : { debugMode : true, apiKey: \"\" } }");
        } catch (IOException e) { e.printStackTrace(); }
        MAINSETTINGS = tempSettings;
    }

    private String filepath;
    private JSONObject config;

    public SettingsManager(String filepath) throws IOException {
        this(filepath, "");
    }

    public SettingsManager(String filepath, String defaultJson) throws IOException {
        this.filepath = filepath;
        if(!Files.exists(Paths.get(filepath))) {
            config = new JSONObject(defaultJson);
            saveToFile();
        }
        else
            this.loadFromFile();
    }

    public JSONObject getSection(String key) {
        return ((JSONObject)config.get(key));
    }

    public <T> T get(String key) {
        return (T)config.get(key);
    }

    public <T> T get(String section, String key) {
        return (T)getSection(section).get(key);
    }

    public <T> void set(String key, T value) {
        config.put(key, value);
    }

    public <T> void set(String section, String key, T value) {
        getSection(section).put(key, value);
    }

    public void loadFromFile() throws IOException {
        String filecontent = new String(Files.readAllBytes(Paths.get(this.filepath)));
        this.config = new JSONObject(filecontent);
    }

    public void saveToFile() throws IOException {
        Files.write(Paths.get(this.filepath), this.config.toString(2).getBytes());
    }
}
