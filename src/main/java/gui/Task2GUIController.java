package main.java.gui;

import com.sothawo.mapjfx.*;
import javafx.fxml.FXML;

public class Task2GUIController {
    @FXML
    private MapView mapView;

    private static final int ZOOM_DEFAULT = 14;

    private static final Coordinate coordDefault = new Coordinate(55.05863889, 8.417527778);

    public Task2GUIController() {

    }

    public void initMapAndControls(Projection projection) {
        // set the custom css file for the MapView
        //mapView.setCustomMapviewCssURL(getClass().getResource("/custom_mapview.css"));
        mapView.initialize();
        mapView.initializedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                afterMapIsInitialized();
            }
        });

        mapView.initialize(Configuration.builder().projection(projection).showZoomControls(false).build());
    }

    private void afterMapIsInitialized() {
        // set defaults
        mapView.setZoom(ZOOM_DEFAULT);
        mapView.setCenter(coordDefault);

        // add the markers to the map - they are still invisible
        //mapView.addMarker(marker);

        // add the fix label, the other's are attached to markers.
        //mapView.addLabel(label);

        // add the tracks
        //mapView.addCoordinateLine(track);

        // now enable the controls (buttons)
        //setControlsDisable(false);
    }
}
