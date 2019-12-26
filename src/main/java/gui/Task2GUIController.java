package main.java.gui;

import com.sothawo.mapjfx.*;
import javafx.animation.Transition;
import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import com.sothawo.mapjfx.event.MapViewEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import main.java.City;
import main.java.db.MongoDBManager;

import java.awt.event.MouseEvent;
import java.util.ArrayList;

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

    private ArrayList<City> locations;

    private static final int ZOOM_DEFAULT = 4;

    private static final Coordinate coordDefault = new Coordinate(41.0, 16.0);

    public Task2GUIController() {
        locations = MongoDBManager.getInstance().getLocationList();
    }

    public void initMapAndControls(Projection projection) {
        // set the custom css file for the MapView
        //mapView.setCustomMapviewCssURL(getClass().getResource("/custom_mapview.css"));

        // bind the map's center and zoom properties to the corresponding labels and format them
        //labelCenter.textProperty().bind(Bindings.format("Center: %s", mapView.centerProperty()));
        //labelZoom.textProperty().bind(Bindings.format("Zoom: %.0f", mapView.zoomProperty()));

        mapView.initialize();
        mapView.initializedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                afterMapIsInitialized();
            }
        });
        mapView.initialize(Configuration.builder().projection(projection).showZoomControls(true).build());
    }

    private void afterMapIsInitialized() {
        // set defaults
        mapView.setZoom(ZOOM_DEFAULT);
        mapView.setCenter(coordDefault);

        buttonFilter.setOnAction((event) -> searchLocation());

        }

        private void searchLocation(){
            // Button was clicked, close Filters, fetch correct locations and open Locations
            locationButtons.getChildren().clear();
            leftControls.setExpandedPane(paneLocations);
            String country = textCountry.getText();
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
            leftControls.setExpandedPane(paneActions);
            Coordinate coords = new Coordinate(c.getCoords().lat, c.getCoords().lon);
            mapView.setCenter(coords).animationDurationProperty().set(1000);
            System.out.println(coords);
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
