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
import main.java.City;
import main.java.Task2;
import main.java.User;
import main.java.db.MongoDBManager;
import main.java.fetch.DarkSkyFetcher;
import main.java.measures.MeasureValue;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class PopupController {

    /**
     * Users Menu
     */
    @FXML
    private GridPane enableDisablePane;

    @FXML
    private GridPane measurementsPane;

    @FXML
    private Label measurementsDialogTitle;


    public PopupController() {
    }

    public <T> void showEnableDisable(ArrayList<T> allList) {

        int i = 0;
        for ( T obj  : allList) {
            if (obj instanceof User) {
                User u = (User) obj;
                Label user = new Label(u.getUsername());
                user.getStyleClass().add("username");
                enableDisablePane.add(user, 0, i);
                Button button = new Button(u.getStatus().equals(User.Status.ENABLED) ? "Disable" : "Enable");
                button.setOnAction((event -> {
                    if (button.getText().equals("Disable")) {
                        button.setText("Enable");
                        MongoDBManager.getInstance().updateUserStatus(u, 1);
                    } else {
                        button.setText("Disable");
                        MongoDBManager.getInstance().updateUserStatus(u, 0);
                    }
                }));
                enableDisablePane.add(button, 1, i);
                i++;
            }else if(obj instanceof City){
                City c = (City) obj;
                Label city = new Label(c.getCity());
                city.getStyleClass().add("city-name");
                enableDisablePane.add(city, 0, i);
                Button button = new Button(c.isEnabled() ? "Disable" : "Enable");
                button.setOnAction((event -> {
                    if (button.getText().equals("Disable")) {
                        button.setText("Enable");
                        MongoDBManager.getInstance().updateCityStatus(c, false);
                    } else {
                        button.setText("Disable");
                        MongoDBManager.getInstance().updateCityStatus(c, true);
                    }
                }));
                enableDisablePane.add(button, 1, i);
                i++;
            }
        }
    }

    public void showPromoteDemote(ArrayList<User> adminsList, ArrayList<User> notAdminsList) {
        adminsList.addAll(notAdminsList);
        ArrayList<User> allUsers = adminsList;

        int i = 0;
        for (User u : allUsers) {
            Label user = new Label(u.getUsername());
            user.getStyleClass().add("username");
            enableDisablePane.add(user, 0, i);
            Button button = new Button(u.getStatus().equals(User.Status.ADMIN) ? "Demote" : "Promote");
            button.setOnAction((event -> {
                if (button.getText().equals("Demote")) {
                    button.setText("Promote");
                    MongoDBManager.getInstance().updateUserStatus(u, 0);
                } else {
                    button.setText("Demote");
                    MongoDBManager.getInstance().updateUserStatus(u, 2);
                }
            }));
            enableDisablePane.add(button, 1, i);
            i++;
        }
    }

    public void showAirPollution(ArrayList<MeasureValue> dailyPollution, boolean forecast) {
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
                if(!forecast) {
                    day.getStyleClass().add("day-date");
                    day.setOnMouseClicked((event -> Task2GUIController.INSTANCE.showHourlyPollution(m.datetime)));
                    measurementsDialogTitle.setText("Daily Average Air Pollution Values");
                }else{
                    day.getStyleClass().add("label-parameter");
                    measurementsDialogTitle.setText("Air Pollution Forecast");
                }
                measurementsPane.add(day, j, 0);
            }

            if(!POLLUTION_MAP.containsKey(m.name))
                System.out.println("Invalid key " + m.name);
            else
                measurementsPane.add(new Label(df.format(m.value) + " " + m.unit), j, POLLUTION_MAP.get(m.name)+1);
        }
    }

    public void showHourlyPollution(ArrayList<MeasureValue> hPollution){
        final String[] POLLUTION_LIST = {"o3", "no2", "pm10", "pm25", "so2", "co", "bc"};
        HashMap<String, Integer> POLLUTION_MAP = new HashMap<>();
        for(int i=0; i<POLLUTION_LIST.length; i++) {
            Label param = new Label(POLLUTION_LIST[i]);
            param.getStyleClass().add("label-parameter");
            measurementsPane.add(param, i+1, 0);
            POLLUTION_MAP.put(POLLUTION_LIST[i], i);
        }

        DecimalFormat df = new DecimalFormat("0.000");

        // order by day
        hPollution.sort((c1,c2) -> (c1.datetime.compareTo(c2.datetime) == 0) ?
                c1.name.compareTo(c2.name) : c1.datetime.compareTo(c2.datetime));

        LocalDateTime lastDate = null;
        int j = 0;
        for(MeasureValue m : hPollution) {
            if(!m.datetime.equals(lastDate)) {
                lastDate = m.datetime;
                j++;
                Label hour = new Label(m.datetime.toString());
                hour.getStyleClass().add("label-parameter");
                measurementsPane.add(hour, 0, j);
            }

            if(!POLLUTION_MAP.containsKey(m.name))
                System.out.println("Invalid key " + m.name);
            else
                measurementsPane.add(new Label(df.format(m.value) + " " + m.unit), POLLUTION_MAP.get(m.name)+1, j);
        }
        measurementsDialogTitle.setText("Hourly Air Pollution Values");
    }

    public void showWeather(ArrayList<MeasureValue> dailyParameters, String collection) {
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
                if(collection.equals("forecast"))
                    day.setOnMouseClicked((event -> Task2GUIController.INSTANCE.showHourlyWeatherForecast(m.datetime)));
                else
                    day.setOnMouseClicked((event -> Task2GUIController.INSTANCE.showHourlyWeatherHistory(m.datetime)));
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
        measurementsDialogTitle.setText("Daily Average Weather Parameters");
    }

    public void showHourlyWeather(ArrayList<MeasureValue> hWeather){
        System.out.println(hWeather);
        final String[] PARAMETERS_LIST = DarkSkyFetcher.MEASURE_UNITS.keySet().toArray(new String[1]);
        HashMap<String, Integer> PARAMETERS_MAP = new HashMap<>();
        for(int i=0; i<PARAMETERS_LIST.length; i++) {
            Label param = new Label(PARAMETERS_LIST[i]);
            param.getStyleClass().add("label-parameter");
            measurementsPane.add(param, i+1, 0);
            PARAMETERS_MAP.put(PARAMETERS_LIST[i], i);
        }

        DecimalFormat df = new DecimalFormat("0.000");

        // order by day
        hWeather.sort((c1,c2) -> (c1.datetime.compareTo(c2.datetime) == 0) ?
                c1.name.compareTo(c2.name) : c1.datetime.compareTo(c2.datetime));

        LocalDateTime lastDate = null;
        int j = 0;
        for(MeasureValue m : hWeather) {
            if(!m.datetime.equals(lastDate)) {
                lastDate = m.datetime;
                j++;
                Label hour = new Label(m.datetime.toString());
                hour.getStyleClass().add("label-parameter");
                measurementsPane.add(hour, 0, j);
            }

            String valuestring =
                    (m.value instanceof Double) ?
                            df.format(m.value) :
                            m.value.toString();

            if(!PARAMETERS_MAP.containsKey(m.name))
                System.out.println("Invalid key " + m.name);
            else
                if(m.name.equals("sky")) {
                    ImageView iv = new ImageView();
                    iv.getStyleClass().add("weather-icon");
                    Image i;
                    switch (valuestring) {
                        case "clear-day":
                            i = new Image("/weather-icons/010-sun.png",
                                    30.0, 30.0, true, true);
                            break;
                        case "clear-night":
                            i = new Image("/weather-icons/048-night.png",
                                    30.0, 30.0, true, true);
                            break;
                        case "partly-cloudy-day":
                            i = new Image("/weather-icons/046-cloudy.png",
                                    30.0, 30.0, true, true);
                            break;
                        case "partly-cloudy-night":
                            i = new Image("/weather-icons/045-cloudy-1.png",
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
                    measurementsPane.add(iv, PARAMETERS_MAP.get(m.name) + 1, j);
                }else
                    measurementsPane.add(new Label(valuestring + " " + m.unit), PARAMETERS_MAP.get(m.name) + 1, j);
        }
        measurementsDialogTitle.setText("Hourly Weather Parameters Values");
    }

    public void showWeatherReliability(ArrayList<MeasureValue> dailyParameters, String collection) {
        final String[] PARAMETERS_LIST = DarkSkyFetcher.MEASURE_UNITS.keySet().toArray(new String[1]);

        HashMap<String, Integer> PARAMETERS_MAP = new HashMap<>();
        for(int i=1; i<PARAMETERS_LIST.length; i++) {
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
                day.getStyleClass().add("label-param");
            }

            String valuestring = (m.value instanceof Double) ? df.format(m.value) : m.value.toString();

            if(!PARAMETERS_MAP.containsKey(m.name))
                System.out.println("Invalid key " + m.name);
            else
                measurementsPane.add(new Label(valuestring + " " + m.unit), j, PARAMETERS_MAP.get(m.name) + 1);

        }
        measurementsDialogTitle.setText("Weather Forecast Reliability");
    }

}
