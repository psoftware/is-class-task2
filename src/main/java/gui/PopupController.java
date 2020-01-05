package main.java.gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import main.java.User;
import main.java.fetch.DarkSkyFetcher;
import main.java.measures.MeasureValue;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;

public class PopupController {

    /**
     * Users Menu
     */
    @FXML
    private GridPane enableDisablePane;


    public PopupController() {
    }

    public void showEnableDisable(ArrayList<User> enabledUserList, ArrayList<User> notenabledUserList) {
        ;
        int i = 0;
        for (User u : enabledUserList) {
            enableDisablePane.add(new Label(u.getUsername()), 0, i);
            enableDisablePane.add(new Button("Disable"), 1, i);
            i++;
        }
        int j = i + 1;
        for (User u : notenabledUserList) {
            enableDisablePane.add(new Label(u.getUsername()), 0, j);
            enableDisablePane.add(new Button("Enable"), 1, j);
            j++;
        }
    }

    public void showAirPollutionForecast() {

    }

    public void showAirPollution(ArrayList<MeasureValue> dailyPollution) {
        final String[] POLLUTION_LIST = {"o3", "no2", "pm10", "pm25", "so2", "co", "bc"};
        HashMap<String, Integer> POLLUTION_MAP = new HashMap<>();
        for(int i=0; i<POLLUTION_LIST.length; i++) {
            enableDisablePane.add(new Label(POLLUTION_LIST[i]), 0, i+1);
            POLLUTION_MAP.put(POLLUTION_LIST[i], i);
        }

        DecimalFormat df = new DecimalFormat("0.000");

        // order by day
        dailyPollution.sort((c1,c2) -> (c1.datetime.compareTo(c2.datetime) == 0) ?
                                            c1.name.compareTo(c2.name) : c1.datetime.compareTo(c2.datetime));


        LocalDate lastDate = null;
        int j = 0;
        for(MeasureValue m : dailyPollution) {
            if(!m.datetime.toLocalDate().equals(lastDate)) {
                lastDate = m.datetime.toLocalDate();
                j++;
                enableDisablePane.add(new Label(lastDate.toString()), j, 0);
            }

            if(!POLLUTION_MAP.containsKey(m.name))
                System.out.println("Invalid key " + m.name);
            else
                enableDisablePane.add(new Label(df.format(m.value) + " " + m.unit), j, POLLUTION_MAP.get(m.name)+1);
        }
    }

    public void showWeatherForecast() {

    }

    public void showWeatherHistory(ArrayList<MeasureValue> dailyPollution) {
        final String[] POLLUTION_LIST = DarkSkyFetcher.MEASURE_UNITS.keySet().toArray(new String[1]);

        HashMap<String, Integer> POLLUTION_MAP = new HashMap<>();
        for(int i=0; i<POLLUTION_LIST.length; i++) {
            enableDisablePane.add(new Label(POLLUTION_LIST[i]), 0, i+1);
            POLLUTION_MAP.put(POLLUTION_LIST[i], i);
        }

        DecimalFormat df = new DecimalFormat("0.000");

        // order by day
        dailyPollution.sort((c1,c2) -> (c1.datetime.compareTo(c2.datetime) == 0) ?
                c1.name.compareTo(c2.name) : c1.datetime.compareTo(c2.datetime));


        LocalDate lastDate = null;
        int j = 0;
        for(MeasureValue m : dailyPollution) {
            if(!m.datetime.toLocalDate().equals(lastDate)) {
                lastDate = m.datetime.toLocalDate();
                j++;
                enableDisablePane.add(new Label(lastDate.toString()), j, 0);
            }

            String valuestring = (m.value instanceof Double) ? df.format(m.value) : m.value.toString();

            if(!POLLUTION_MAP.containsKey(m.name))
                System.out.println("Invalid key " + m.name);
            else
                enableDisablePane.add(new Label(valuestring + " " + m.unit), j, POLLUTION_MAP.get(m.name)+1);
        }
    }
}
