package main.java.gui;

import com.sothawo.mapjfx.*;
import com.sothawo.mapjfx.offline.OfflineCache;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import com.sothawo.mapjfx.event.MapViewEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import main.java.City;
import main.java.User;
import main.java.db.MongoDBManager;
import main.java.db.SettingsManager;
import main.java.fetch.DarkSkyFetcher;
import main.java.measures.MeasureValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.lang.String;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class Task2GUIController {
    @FXML
    private MapView mapView;

    /** Label to display the current center */
    @FXML
    private Label labelCenter;

    /** Label to display the current zoom */
    @FXML
    private Label labelZoom;

    /** Accordion to contain all the use cases*/
    @FXML
    private MenuBar TopMenu;

    /** Accordion to contain all locations and actions on them*/
    @FXML
    private Accordion leftControls;

    /** TitledPane to contain the filtering options*/
    @FXML
    private TitledPane paneFilters;

    /** TitledPane to contain all the locations*/
    @FXML
    private TitledPane paneLocations;

    /** TitledPane to contain all the uses*/
    @FXML
    private TitledPane paneActions;

    /** TitledPane to contain all the uses*/
    @FXML
    private TitledPane paneTimeRange;

    /** VBox to contain dynamic locations*/
    @FXML
    private VBox locationButtons;


    /** Labels */
    @FXML
    private Label labelCountry;

    @FXML
    private Label labelCity;

    /** Text Fields*/
    @FXML
    private TextField textCountry;

    @FXML
    private TextField textCity;

    /** Buttons */
    @FXML
    private Button buttonFilter;

    @FXML
    private Button buttonShowWeatherHistory;

    @FXML
    private Button buttonShowWeatherForecast;

    @FXML
    private Button buttonShowAirPollution;

    @FXML
    private Button buttonWeatherForecastReliability;

    @FXML
    private AnchorPane separatorEnabled;

    @FXML
    private Button buttonShowAirPollutionForecast;

    @FXML
    private AnchorPane separatorAdmin;

    @FXML
    private Button buttonFetchPollution;

    @FXML
    private Button buttonFetchPastWeather;

    @FXML
    private Button buttonFetchForecastWeather;

    @FXML
    private DatePicker datepickerStart;

    @FXML
    private DatePicker datepickerEnd;

    @FXML
    private Button buttonSubmitTimeRange;

    @FXML
    private TitledPane paneSingleDay;

    @FXML
    private DatePicker datepickerSingleDay;

    @FXML
    private Button buttonSubmitSingleDay;

    @FXML
    private MenuItem enableDisableMenu;

    @FXML
    private MenuItem reloadLocationsMenuItem;

    @FXML
    private MenuItem openSettings;

    private ArrayList<City> locations;

    private ArrayList<Marker> markers = new ArrayList<>();

    private ArrayList<MapLabel> labels = new ArrayList<>();

    private City selectedCity;

    private static final int ZOOM_DEFAULT = 4;

    private static final Coordinate coordDefault = new Coordinate(41.0, 16.0);

    public Task2GUIController() {
        locations = MongoDBManager.getInstance().getLocationList();
    }

    public void loadSettingsFromFile() {
        DarkSkyFetcher.getInstance().setApiKey(SettingsManager.MAINSETTINGS.<String>getOrSetDefault("darksky","apiKey", ""));
        DarkSkyFetcher.getInstance().setDebugMode(SettingsManager.MAINSETTINGS.<Boolean>getOrSetDefault("darksky", "debugMode", false));
        DarkSkyFetcher.getInstance().enableLocalCache(SettingsManager.MAINSETTINGS.<Boolean>getOrSetDefault("darksky", "localCache",  false));
    }

    public void initMapAndControls(Projection projection, User user) {
        loadSettingsFromFile();

        // set the custom css file for the MapView
        mapView.setCustomMapviewCssURL(getClass().getResource("/custom_mapview.css"));

        // bind the map's center and zoom properties to the corresponding labels and format them
        //labelCenter.textProperty().bind(Bindings.format("Center: %s", mapView.centerProperty()));
        //labelZoom.textProperty().bind(Bindings.format("Zoom: %.0f", mapView.zoomProperty()));

        final OfflineCache offlineCache = mapView.getOfflineCache();
        final String cacheDir = System.getProperty("java.io.tmpdir") + "/mapjfx-cache";
        try {
            Files.createDirectories(Paths.get(cacheDir));
            offlineCache.setCacheDirectory(cacheDir);
            offlineCache.setActive(true);
        } catch (IOException e) {
            System.out.println("could not activate offline cache:");
            e.printStackTrace();
        }

        mapView.initialize();
        mapView.initializedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                afterMapIsInitialized(user);
            }
        });
        mapView.initialize(Configuration.builder().projection(projection).showZoomControls(true).build());

        changeTimePane(TimePaneType.HIDDEN, null);
    }

    private Callback<DatePicker, DateCell> getFactoryForDatePickerAvalability(Predicate<LocalDate> dateAvailable) {
        return (datePicker) -> new DateCell() {
                    @Override public void updateItem(LocalDate itemDate, boolean empty) {
                        super.updateItem(itemDate, empty);

                        if (empty || itemDate == null) { // on datepicker factory change
                            setTooltip(null);
                            setStyle(null);
                            setDisable(false);
                            return;
                        }

                        if (dateAvailable.test(itemDate) &&
                                !(getStyleClass().contains("next-month") || getStyleClass().contains("previous-month"))
                        ) {
                            setDisable(false);
                            //setStyle("-fx-background-color: #5bff98;");
                        } else {
                            setTooltip(null);
                            setStyle(null);
                            setDisable(true);
                        }
                    }
        };
    }

    private enum TimePaneType{HIDDEN, DATERANGE, SINGLEDATE};

    private void changeTimePane(TimePaneType paneType, BiConsumer<LocalDate, LocalDate> onSubmit) {
        changeTimePane(paneType, onSubmit, (Predicate<LocalDate>)null);
    }

    private void changeTimePane(TimePaneType paneType, BiConsumer<LocalDate, LocalDate> onSubmit, HashSet<LocalDate> dateSet) {
        changeTimePane(paneType, onSubmit, dateSet::contains);
    }

    private void changeTimePane(TimePaneType paneType, BiConsumer<LocalDate, LocalDate> onSubmit,
                                Predicate<LocalDate> dateAvailable)
    {
        if(paneType == TimePaneType.DATERANGE && !leftControls.getPanes().contains(paneTimeRange)) {
            leftControls.getPanes().add(paneTimeRange);
            leftControls.getPanes().remove(paneSingleDay);
        }
        else if(paneType == TimePaneType.SINGLEDATE && !leftControls.getPanes().contains(paneSingleDay)) {
            leftControls.getPanes().add(paneSingleDay);
            leftControls.getPanes().remove(paneTimeRange);
        }
        else if(paneType == TimePaneType.HIDDEN)
            leftControls.getPanes().removeAll(paneTimeRange, paneSingleDay);

        switch(paneType) {
            case DATERANGE:
                leftControls.setExpandedPane(paneTimeRange);
                buttonSubmitTimeRange.setOnAction(ev -> {
                    if(datepickerStart.getValue() == null || datepickerEnd.getValue() == null)
                        SimpleDialog.showErrorDialog("Invalid start or end date");
                    else
                        onSubmit.accept(datepickerStart.getValue(), datepickerEnd.getValue());
                });
                break;
            case SINGLEDATE:
                leftControls.setExpandedPane(paneSingleDay);
                buttonSubmitSingleDay.setOnAction(ev -> {
                    if(datepickerSingleDay.getValue() == null)
                        SimpleDialog.showErrorDialog("Invalid day value");
                    else
                        onSubmit.accept(datepickerSingleDay.getValue(),null);
                });

                datepickerSingleDay.setDayCellFactory(
                        (dateAvailable == null) ? null : getFactoryForDatePickerAvalability(dateAvailable));
                break;
        }
    }

    public City getSelectedCity() {
        return selectedCity;
    }

    private void afterMapIsInitialized(User user) {
        // set defaults
        mapView.setZoom(ZOOM_DEFAULT);
        mapView.setCenter(coordDefault);

        leftControls.setExpandedPane(paneFilters);

        // button events
        buttonFilter.setOnAction((event) -> searchLocation());

        // Base use cases
        buttonShowWeatherHistory.setOnAction(
                (event) -> changeTimePane(TimePaneType.SINGLEDATE, (d1, d2) -> showWeatherHistory(d1),
                        MongoDBManager.getInstance().getPastWeatherAvailableDates(getSelectedCity())));
        buttonShowWeatherForecast.setOnAction(
                (event) -> changeTimePane(TimePaneType.SINGLEDATE, (d1, d2) -> showWeatherForecast(d1),
                        MongoDBManager.getInstance().getForecastWeatherAvailableDates(getSelectedCity())));
        buttonShowAirPollution.setOnAction(
                (event) -> changeTimePane(TimePaneType.SINGLEDATE, (d1, d2) -> showAirPollution(d1),
                        MongoDBManager.getInstance().getPollutionAvailableDates(getSelectedCity())));
        buttonWeatherForecastReliability.setOnAction(
                (event) -> changeTimePane(TimePaneType.SINGLEDATE, (d1, d2) -> showWeatherReliability(d1),
                        (d) -> MongoDBManager.getInstance().getPastWeatherAvailableDates(getSelectedCity()).contains(d) &&
                                MongoDBManager.getInstance().getForecastWeatherAvailableDates(getSelectedCity()).contains(d)
                ));

        // Regular additional use cases
        if(!user.getStatus().equals(User.Status.NOTENABLED))
            buttonShowAirPollutionForecast.setOnAction(
                        (event) -> changeTimePane(TimePaneType.SINGLEDATE, (d1, d2) -> showAirPollutionForecast()));

        // Admin additional use cases
        if(user.getStatus().equals(User.Status.ADMIN)) {
            buttonFetchPollution.setOnAction(e ->
                changeTimePane(TimePaneType.DATERANGE,
                        (d1, d2) -> new LoadingWindow().showAndWaitCallable(() -> MongoDBManager.getInstance()
                                        .loadPollutionFromAPI(selectedCity, datepickerStart.getValue(), datepickerEnd.getValue()),
                                "Loading Pollution Measures...", "Pollution measures loading success")
                )
            );
            buttonFetchPastWeather.setOnAction(e ->
                    changeTimePane(TimePaneType.DATERANGE,
                            (d1, d2) -> new LoadingWindow().showAndWaitCallable(() -> MongoDBManager.getInstance()
                                            .loadPastWeatherFromAPI(selectedCity, datepickerStart.getValue(), datepickerEnd.getValue()),
                                    "Loading Past Weather Measures...", "Past Weather measures loading completed")
                    )
            );
            buttonFetchForecastWeather.setOnAction(e ->
                    changeTimePane(TimePaneType.DATERANGE,
                            (d1, d2) -> new LoadingWindow().showAndWaitCallable(() -> MongoDBManager.getInstance()
                                            .loadForecastWeatherFromAPI(selectedCity, datepickerStart.getValue(), datepickerEnd.getValue()),
                                    "Loading Forecast Weather Measures...", "Forecast Weather measures loading completed")
                    )
            );
        }

        // menu events
        enableDisableMenu.setOnAction((event -> enableDisableUsers()));
        reloadLocationsMenuItem.setOnAction(event -> SimpleDialog.showIfErrorOrSuccess(
                () -> MongoDBManager.getInstance().resetLocationList(),
                "Reloading locations...","Location reload completed"
        ));

        openSettings.setOnAction(e -> showSettings());

        for (City c : locations) {
            Coordinate coords = new Coordinate(c.getCoords().lat, c.getCoords().lon);
            Marker marker = new Marker(getClass().getResource("/Map-Marker.png"), -10, -20).setPosition(coords)
                    .setVisible(false);
            MapLabel label = new MapLabel(c.getCity(), -5, 15).setPosition(coords).setCssClass("label");
            markers.add(marker);
            labels.add(label);
            marker.attachLabel(label);
            mapView.addMarker(marker);
        }

        mapView.addEventHandler(MapViewEvent.MAP_CLICKED, event -> {
            event.consume();
            final Coordinate newPosition = event.getCoordinate();
            City closestCity = getClosestCity(newPosition);
            centerMapToCity(closestCity);
        });
    }

        private void searchLocation(){
            // Button was clicked, close Filters, fetch correct locations and open Locations

            locationButtons.getChildren().clear();
            leftControls.setExpandedPane(paneLocations);
            java.lang.String country = textCountry.getText();
            String city = textCity.getText();
            for (City c: locations) {
                if(country.equals(c.getCountry()) || country.equals(""))
                    if(city.equals(c.getCity()) || city.equals("")){
                        Button cityButton = new Button(c.getCity() + ", "+ c.getCountry());
                        cityButton.setOnAction((event1 -> centerMapToCity(c)));
                        locationButtons.getChildren().add(cityButton);
                    }
            }
        }

        private void centerMapToCity(City c){
            //removing old marker
            for (Marker m : markers) {
                m.setVisible(false);
            }
            leftControls.setExpandedPane(paneActions);
            Coordinate coords = new Coordinate(c.getCoords().lat, c.getCoords().lon);
            mapView.setCenter(coords).animationDurationProperty().set(1000);
            for ( Marker m : markers) {
                if (m.getPosition().equals(coords))
                    m.setVisible(true);
            }

            selectedCity = c;
        }

        private City getClosestCity(Coordinate clickPosition){
            double minDistance = Double.POSITIVE_INFINITY;
            City closestCity = null;
            for (City location: locations){
                double distance = Math.sqrt(
                        Math.pow((clickPosition.getLatitude() - location.getCoords().lat),2) +
                        Math.pow((clickPosition.getLongitude() - location.getCoords().lon),2)
                );
                if(distance < minDistance){
                    minDistance = distance;
                    closestCity = location;
                }
            }
            return closestCity;
        }

        private void showWeatherHistory(LocalDate refday) {
            LocalDate startDate = refday.minusDays(3);
            LocalDate endDate = refday.plusDays(3);
            if(endDate.isAfter(LocalDate.now()))
                endDate = LocalDate.now();

            HashMap<City.CityName, ArrayList<MeasureValue>> result =
                    MongoDBManager.getInstance().getDailyPastWeather(startDate, endDate);
            ArrayList<MeasureValue> dailyWeather = result.get(selectedCity.getCityName());
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("WeatherDialog.fxml"));
            Parent root = null;
            try {
                root = fxmlLoader.load();
            } catch (IOException e) {
                e.printStackTrace();
            }

            PopupController pc = fxmlLoader.getController();
            pc.showWeather(dailyWeather);
            setupStage(root,"Weather History");
        }

        private void showWeatherForecast(LocalDate refday) {
            LocalDate startDate = refday.minusDays(3), endDate = refday.plusDays(3);
            HashMap<City.CityName, ArrayList<MeasureValue>> result =
                    MongoDBManager.getInstance().getDailyForecastWeather(startDate, endDate);
            ArrayList<MeasureValue> dailyWeather = result.get(selectedCity.getCityName());
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("weatherDialog.fxml"));
            Parent root = null;
            try {
                root = fxmlLoader.load();
            } catch (IOException e) {
                e.printStackTrace();
            }

            PopupController pc = fxmlLoader.getController();
            pc.showWeather(dailyWeather);
            setupStage(root,"Weather Forecast");
        }

        private void showWeatherReliability(LocalDate refday) {
            LocalDate startDate = refday.minusDays(3);
            LocalDate endDate = refday.plusDays(3);
            if(endDate.isAfter(LocalDate.now()))
                endDate = LocalDate.now();

            ArrayList<MeasureValue> result =
                    MongoDBManager.getInstance().getWeatherForecastReliability(startDate, endDate);
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("weatherDialog.fxml"));
            Parent root = null;
            try {
                root = fxmlLoader.load();
            } catch (IOException e) {
                e.printStackTrace();
            }

            PopupController pc = fxmlLoader.getController();
            pc.showWeather(result);
            setupStage(root,"Weather Forecast Reliability");
        }

        private void showAirPollution(LocalDate refday) {
            LocalDate startDate = refday.minusDays(3);
            LocalDate endDate = refday.plusDays(3);
            if(endDate.isAfter(LocalDate.now()))
                endDate = LocalDate.now();

            HashMap<City.CityName, ArrayList<MeasureValue>> result =
                    MongoDBManager.getInstance().getDailyPollution(startDate, endDate);
            ArrayList<MeasureValue> dailyPollution = result.get(selectedCity.getCityName());
            if(dailyPollution == null) {
                SimpleDialog.showErrorDialog("No pollution data for this location and time!");
                return;
            }

            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("pollutionDialog.fxml"));
            Parent root = null;
            try {
                root = fxmlLoader.load();
            } catch (IOException e) {
                e.printStackTrace();
            }

            PopupController pc = fxmlLoader.getController();
            pc.showAirPollution(dailyPollution);
            setupStage(root,"Air Pollution");
        }

        private void showAirPollutionForecast() {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("pollutionDialog.fxml"));
            Parent root = null;
            try {
                root = fxmlLoader.load();
            } catch (IOException e) {
                e.printStackTrace();
            }

            PopupController pc = fxmlLoader.getController();
            pc.showAirPollutionForecast();
            setupStage(root,"Air Pollution Forecast");
        }

        private void enableDisableUsers(){
            ArrayList<User> enabledUserList = MongoDBManager.getInstance().getUsersByStatus(0);
            ArrayList<User> notenabledUserList = MongoDBManager.getInstance().getUsersByStatus(1);

            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("enableDisableUser.fxml"));
            Parent root = null;
            try {
                root = fxmlLoader.load();
            } catch (IOException e) {
                e.printStackTrace();
            }

            PopupController pc = fxmlLoader.getController();
            pc.showEnableDisable(enabledUserList, notenabledUserList);
            setupStage(root,"Enable Or Disable Users");
        }

        private void showSettings() {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("settings.fxml"));
            Parent root = null;
            try { root = fxmlLoader.load();
            } catch (IOException e) { e.printStackTrace(); }

            SettingsController controller = fxmlLoader.getController();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setTitle("Settings");

            controller.initialize(stage, this::loadSettingsFromFile);
            stage.show();
        }

        private void setupStage(Parent root, String title){
            Stage stage = new Stage();
            // now that we want to open dialog, we must use this line:
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.show();
        }

        // add the markers to the map - they are still invisible
        //mapView.addMarker(marker);

        // add the fix label, the other's are attached to markers.
        //mapView.addLabel(label);

        // add the tracks
        //mapView.addCoordinateLine(track);

        // now enable the controls (buttons)
        //setControlsDisable(false);
    }
