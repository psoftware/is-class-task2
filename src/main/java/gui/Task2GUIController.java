package main.java.gui;

import com.sothawo.mapjfx.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import com.sothawo.mapjfx.event.MapViewEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import main.java.City;
import main.java.User;
import main.java.db.MongoDBManager;
import main.java.measures.MeasureValue;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.lang.String;
import java.util.HashMap;

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
    private Button buttonShowAirPollutionForecast;

    @FXML
    private MenuItem enableDisableMenu;

    private ArrayList<City> locations;

    private ArrayList<Marker> markers = new ArrayList<>();

    private ArrayList<MapLabel> labels = new ArrayList<>();

    private City selectedCity;

    private static final int ZOOM_DEFAULT = 4;

    private static final Coordinate coordDefault = new Coordinate(41.0, 16.0);

    public Task2GUIController() {
        locations = MongoDBManager.getInstance().getLocationList();
    }

    public void initMapAndControls(Projection projection, User user) {
        // set the custom css file for the MapView
        mapView.setCustomMapviewCssURL(getClass().getResource("/custom_mapview.css"));

        // bind the map's center and zoom properties to the corresponding labels and format them
        //labelCenter.textProperty().bind(Bindings.format("Center: %s", mapView.centerProperty()));
        //labelZoom.textProperty().bind(Bindings.format("Zoom: %.0f", mapView.zoomProperty()));

        mapView.initialize();
        mapView.initializedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                afterMapIsInitialized(user);
            }
        });
        mapView.initialize(Configuration.builder().projection(projection).showZoomControls(true).build());
    }

    private void afterMapIsInitialized(User user) {
        // set defaults
        mapView.setZoom(ZOOM_DEFAULT);
        mapView.setCenter(coordDefault);

        leftControls.setExpandedPane(paneFilters);

        // button events
        buttonFilter.setOnAction((event) -> searchLocation());
        buttonShowWeatherHistory.setOnAction((event) -> showWeatherHistory());
        buttonShowWeatherForecast.setOnAction((event) -> showWeatherForecast());
        buttonShowAirPollution.setOnAction((event) -> showAirPollution());
        if(!user.getStatus().equals(User.Status.NOTENABLED))
            buttonShowAirPollutionForecast.setOnAction((event) -> showAirPollutionForecast());

        // menu events
        enableDisableMenu.setOnAction((event -> enableDisableUsers()));

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

        private void showWeatherHistory() {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("WeatherDialog.fxml"));
            Parent root = null;
            try {
                root = fxmlLoader.load();
            } catch (IOException e) {
                e.printStackTrace();
            }

            PopupController pc = fxmlLoader.getController();
            pc.showWeatherHistory();
            setupStage(root,"Weather History");
        }

        private void showWeatherForecast() {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("weatherDialog.fxml"));
            Parent root = null;
            try {
                root = fxmlLoader.load();
            } catch (IOException e) {
                e.printStackTrace();
            }

            PopupController pc = fxmlLoader.getController();
            pc.showWeatherForecast();
            setupStage(root,"Weather Forecast");
        }

        private void showAirPollution() {
            LocalDateTime startDate = LocalDateTime.now().minusDays(15), endDate = LocalDateTime.now().minusDays(10);
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
