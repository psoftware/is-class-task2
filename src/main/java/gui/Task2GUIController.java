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
import java.time.LocalDateTime;
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
    private AnchorPane separatorAdminFilter;

    @FXML
    private Button buttonShowTopVoted;

    @FXML
    private AnchorPane paneVoteLocation;

    @FXML
    private Button buttonVoteLocation;

    @FXML
    private Button buttonUnvoteLocation;

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
    private MenuItem promoteDemoteMenu;

    @FXML
    private MenuItem syncLocationsMenuItem;

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

    public static Task2GUIController INSTANCE = null;

    public Task2GUIController() {
        locations = MongoDBManager.getInstance().getLocationList();
    }

    public void loadSettingsFromFile() {
        DarkSkyFetcher.getInstance().setApiKey(SettingsManager.MAINSETTINGS.<String>getOrSetDefault("darksky","apiKey", ""));
        DarkSkyFetcher.getInstance().setDebugMode(SettingsManager.MAINSETTINGS.<Boolean>getOrSetDefault("darksky", "debugMode", false));
        DarkSkyFetcher.getInstance().enableLocalCache(SettingsManager.MAINSETTINGS.<Boolean>getOrSetDefault("darksky", "localCache",  false));
    }

    public void initMapAndControls(Projection projection, User user, Task2GUIController instance) {
        INSTANCE = instance;
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

        //mapView.initialize();
        mapView.initialize(Configuration.builder().projection(projection).showZoomControls(true).build());
        mapView.initializedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                afterMapIsInitialized(user);
            }
        });


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
        buttonVoteLocation.setOnAction(
                (event) -> SimpleDialog.showIfErrorOrSuccess(
                        () -> MongoDBManager.getInstance().voteLocation(user, getSelectedCity().getCityName()),
                        "Voting...", "Location voted successfully")
        );

        buttonUnvoteLocation.setOnAction(
                (event) -> SimpleDialog.showIfErrorOrSuccess(
                        () -> MongoDBManager.getInstance().unvoteLocation(user, getSelectedCity().getCityName()),
                        "Unvoting...", "Location unvoted successfully")
        );

        buttonShowWeatherHistory.setOnAction(
                (event) -> {
                    HashSet<LocalDate> pastWDates = MongoDBManager.getInstance().getPastWeatherAvailableDates(getSelectedCity());
                    changeTimePane(TimePaneType.SINGLEDATE, (d1, d2) -> showWeatherHistory(d1), pastWDates);
                });
        buttonShowWeatherForecast.setOnAction(
                (event) -> {
                    HashSet<LocalDate> forecastWDates = MongoDBManager.getInstance().getForecastWeatherAvailableDates(getSelectedCity());
                    changeTimePane(TimePaneType.SINGLEDATE, (d1, d2) -> showWeatherForecast(d1), forecastWDates);
                });
        buttonShowAirPollution.setOnAction(
                (event) -> {
                    HashSet<LocalDate> pollutionDates = MongoDBManager.getInstance().getPollutionAvailableDates(getSelectedCity());
                    changeTimePane(TimePaneType.SINGLEDATE, (d1, d2) -> showAirPollution(d1), pollutionDates);
                });
        buttonWeatherForecastReliability.setOnAction(
                (event) -> {
                    HashSet<LocalDate> pastWeatherDates = MongoDBManager.getInstance().getPastWeatherAvailableDates(getSelectedCity());
                    HashSet<LocalDate> forecastWDates = MongoDBManager.getInstance().getForecastWeatherAvailableDates(getSelectedCity());
                    changeTimePane(TimePaneType.SINGLEDATE, (d1, d2) -> showWeatherReliability(d1),
                        (d) -> pastWeatherDates.contains(d) && forecastWDates.contains(d));
                    });

        // Regular additional use cases
        if(!user.getStatus().equals(User.Status.NOTENABLED))
            buttonShowAirPollutionForecast.setOnAction(
                    (event) -> {
                        HashSet<LocalDate> forecastDates = MongoDBManager.getInstance().getForecastWeatherAvailableDates(getSelectedCity());
                        changeTimePane(TimePaneType.SINGLEDATE, (d1, d2) -> showAirPollutionForecast(d1), forecastDates);
                    });

        // Admin additional use cases
        if(user.getStatus().equals(User.Status.ADMIN)) {
            buttonShowTopVoted.setOnAction(e -> showTopLocations(10));
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

            // menu events
            enableDisableMenu.setOnAction((event -> enableDisableUsers()));
            promoteDemoteMenu.setOnAction((event -> promoteDemoteAdmin()));
            reloadLocationsMenuItem.setOnAction(event -> SimpleDialog.showIfErrorOrSuccess(
                    () -> MongoDBManager.getInstance().resetLocationList(),
                    "Reloading locations...","Location reload completed"
            ));
            syncLocationsMenuItem.setOnAction(event -> SimpleDialog.showIfErrorOrSuccess(
                    () -> MongoDBManager.getInstance().syncLocationList(),
                    "Syncing locations...","Location sync completed"
            ));
            openSettings.setOnAction(e -> showSettings());
        }

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

    private void showTopLocations(int n){
        // Button was clicked, close Filters, fetch correct locations and open Locations
        locationButtons.getChildren().clear();
        leftControls.setExpandedPane(paneLocations);
        for (City c: MongoDBManager.getInstance().getTopLocationsByVoteCount(n)) {
            Button cityButton = new Button(c.getCity() + ", "+ c.getCountry() + " (" + c.getVoteCount() +" votes)");
            cityButton.setOnAction((event1 -> centerMapToCity(c)));
            locationButtons.getChildren().add(cityButton);
        }
    }

        private void searchLocation(){
            // Button was clicked, close Filters, fetch correct locations and open Locations

            locationButtons.getChildren().clear();
            leftControls.setExpandedPane(paneLocations);
            java.lang.String country = textCountry.getText();
            String city = textCity.getText();
            for (City c: locations) {
                if(country.toLowerCase().equals(c.getCountry().toLowerCase()) || country.equals(""))
                    if(city.toLowerCase().equals(c.getCity().toLowerCase()) || city.equals("")){
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
                    MongoDBManager.getInstance().getDailyPastWeather(startDate, endDate, selectedCity);
            ArrayList<MeasureValue> dailyWeather = result.get(selectedCity.getCityName());
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("measurementsDialog.fxml"));
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
                    MongoDBManager.getInstance().getDailyForecastWeather(startDate, endDate, selectedCity);
            ArrayList<MeasureValue> dailyWeather = result.get(selectedCity.getCityName());
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("measurementsDialog.fxml"));
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
                    MongoDBManager.getInstance().getWeatherForecastReliability(startDate, endDate, selectedCity);
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("measurementsDialog.fxml"));
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
                    MongoDBManager.getInstance().getDailyPollution(startDate, endDate, selectedCity);
            ArrayList<MeasureValue> dailyPollution = result.get(selectedCity.getCityName());
            if(dailyPollution == null) {
                SimpleDialog.showErrorDialog("No pollution data for this location and time!");
                return;
            }

            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("measurementsDialog.fxml"));
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

        public void showHourlyPollution(LocalDateTime start){
            LocalDateTime end = start.plusDays(1);

            HashMap<City.CityName, ArrayList<MeasureValue>> hourlyPollution =
                    MongoDBManager.getInstance().getHourlyPollution(start, end, selectedCity);
            ArrayList<MeasureValue> hPollution = hourlyPollution.get(selectedCity.getCityName());

            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("measurementsDialog.fxml"));
            Parent root = null;
            try { root = fxmlLoader.load();
            } catch (IOException e) { e.printStackTrace(); }

            PopupController pc = fxmlLoader.getController();
            pc.showHourlyPollution(hPollution);
            setupStage(root,"Hourly Air Pollution");

        }

    public void showHourlyWeather(LocalDateTime start){
        LocalDateTime end = start.plusDays(1);

        HashMap<City.CityName, ArrayList<MeasureValue>> hourlyWeather =
                MongoDBManager.getInstance().getHourlyPollution(start, end, selectedCity);
        ArrayList<MeasureValue> hWeather = hourlyWeather.get(selectedCity.getCityName());

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("measurementsDialog.fxml"));
        Parent root = null;
        try { root = fxmlLoader.load();
        } catch (IOException e) { e.printStackTrace(); }

        PopupController pc = fxmlLoader.getController();
        pc.showHourlyWeather(hWeather);
        setupStage(root,"Hourly Weather");

    }

        private void showAirPollutionForecast(LocalDate start) {
            LocalDate startDate = start.plusDays(1);
            LocalDate endDate = start.plusDays(7);
            if(start.isBefore(LocalDate.now()))
                return;

            ArrayList<MeasureValue> forecastPollution =
                    MongoDBManager.getInstance().getPollutionForecast(startDate, endDate, selectedCity);

            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("measurementsDialog.fxml"));
            Parent root = null;
            try { root = fxmlLoader.load();
            } catch (IOException e) { e.printStackTrace(); }

            PopupController pc = fxmlLoader.getController();
            pc.showAirPollution(forecastPollution);
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

        private void promoteDemoteAdmin(){
            ArrayList<User> AdminsList = MongoDBManager.getInstance().getUsersByStatus(2);
            ArrayList<User> notAdminsList = MongoDBManager.getInstance().getUsersByStatus(0);
            notAdminsList.addAll(MongoDBManager.getInstance().getUsersByStatus(1));

            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("enableDisableUser.fxml"));
            Parent root = null;
            try {
                root = fxmlLoader.load();
            } catch (IOException e) {
                e.printStackTrace();
            }

            PopupController pc = fxmlLoader.getController();
            pc.showPromoteDemote(AdminsList, notAdminsList);
            setupStage(root,"Promote Or Demote Admins");
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
