package main.java.gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
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

    @FXML
    private GridPane measurementsPane;


    public PopupController() {
    }

    public void showEnableDisable(ArrayList<User> enabledUserList, ArrayList<User> notenabledUserList) {
        int i = 0;
        for (User u : enabledUserList) {
            Label user = new Label(u.getUsername());
            user.getStyleClass().add("username");
            enableDisablePane.add(user, 0, i);
            enableDisablePane.add(new Button("Disable"), 1, i);
            i++;
        }
        int j = i + 1;
        for (User u : notenabledUserList) {
            Label user = new Label(u.getUsername());
            user.getStyleClass().add("username");
            enableDisablePane.add(user, 0, j);
            enableDisablePane.add(new Button("Enable"), 1, j);
            j++;
        }
    }

    public void showPromoteDemote(ArrayList<User> AdminsList, ArrayList<User> notAdminsList) {
        int i = 0;
        for (User u : AdminsList) {
            Label user = new Label(u.getUsername());
            user.getStyleClass().add("username");
            enableDisablePane.add(user, 0, i);
            enableDisablePane.add(new Button("Demote"), 1, i);
            i++;
        }
        int j = i + 1;
        for (User u : notAdminsList) {
            Label user = new Label(u.getUsername());
            user.getStyleClass().add("username");
            enableDisablePane.add(user, 0, j);
            enableDisablePane.add(new Button("Promote"), 1, j);
            j++;
        }
    }

    public void showAirPollutionForecast() {

    }

    public void showAirPollution(ArrayList<MeasureValue> dailyPollution) {
        final String[] POLLUTION_LIST = {"o3", "no2", "pm10", "pm25", "so2", "co", "bc"};
        HashMap<String, Integer> POLLUTION_MAP = new HashMap<>();
        for(int i=0; i<POLLUTION_LIST.length; i++) {
            Label param = new Label(POLLUTION_LIST[i]);
            param.getStyleClass().add("label-parameter");
            measurementsPane.add(param, 0, i+1);
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
                Label day = new Label(lastDate.toString());
                day.getStyleClass().add("day-date");
                measurementsPane.add(day, j, 0);
            }

            if(!POLLUTION_MAP.containsKey(m.name))
                System.out.println("Invalid key " + m.name);
            else
                measurementsPane.add(new Label(df.format(m.value) + " " + m.unit), j, POLLUTION_MAP.get(m.name)+1);
        }
    }

    public void showWeather(ArrayList<MeasureValue> dailyParameters) {
        final String[] PARAMETERS_LIST = DarkSkyFetcher.MEASURE_UNITS.keySet().toArray(new String[1]);

        HashMap<String, Integer> PARAMETERS_MAP = new HashMap<>();
        for(int i=0; i<PARAMETERS_LIST.length; i++) {
            String input = PARAMETERS_LIST[i];
            input = Character.toUpperCase(input.charAt(0)) + input.substring(1);
            Label param = new Label(input.replaceAll("(\\p{Ll})(\\p{Lu})","$1 $2"));
            param.getStyleClass().add("label-parameter");

            measurementsPane.add(param, 0, i+1);
            PARAMETERS_MAP.put(PARAMETERS_LIST[i], i);
        }

        DecimalFormat df = new DecimalFormat("0.000");

        // order by day
        dailyParameters.sort((c1,c2) -> (c1.datetime.compareTo(c2.datetime) == 0) ?
                c1.name.compareTo(c2.name) : c1.datetime.compareTo(c2.datetime));


        LocalDate lastDate = null;
        int j = 0;
        for(MeasureValue m : dailyParameters) {
            if(!m.datetime.toLocalDate().equals(lastDate)) {
                lastDate = m.datetime.toLocalDate();
                j++;
                Label day = new Label(lastDate.toString());
                day.getStyleClass().add("day-date");
                measurementsPane.add(day, j, 0);
            }

            String valuestring = (m.value instanceof Double) ? df.format(m.value) : m.value.toString();

            if(!PARAMETERS_MAP.containsKey(m.name))
                System.out.println("Invalid key " + m.name);
            else
                if(m.name.equals("sky")) {
                    ImageView iv = new ImageView();
                    iv.getStyleClass().add("weather-icon");
                    Image i;
                    switch (valuestring) {
                        case "clear-day":
                        case "clear-night":
                            i = new Image("/weather-icons/010-sun.png",
                                    30.0, 30.0, true, true);
                            break;
                        case "partly-cloudy-day":
                        case "partly-cloudy-night":
                            i = new Image("/weather-icons/046-cloudy.png",
                                    30.0, 30.0, true, true);
                            break;
                        case "cloudy":
                            i = new Image("/weather-icons/047-cloud.png",
                                    30.0, 30.0, true, true);
                            break;
                        case "rain":
                            i = new Image("/weather-icons/023-rain.png",
                                    30.0, 30.0, true, true);
                            break;
                        case "snow":
                            i = new Image("/weather-icons/019-snowflake.png",
                                    30.0, 30.0, true, true);
                            break;
                        case "sleet":
                            i = new Image("/weather-icons/017-snowy-1.png",
                                    30.0, 30.0, true, true);
                            break;
                        case "wind":
                            i = new Image("/weather-icons/004-wind.png",
                                    30.0, 30.0, true, true);
                            break;
                        default: // fog
                            i = new Image("/weather-icons/041-fog.png",
                                    30.0, 30.0, true, true);
                            break;
                    }
                    iv.setImage(i);
                    measurementsPane.add(iv, j, PARAMETERS_MAP.get(m.name) + 1);
                }else
                    measurementsPane.add(new Label(valuestring + " " + m.unit), j, PARAMETERS_MAP.get(m.name) + 1);

        }
    }
}
