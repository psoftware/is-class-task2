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

import java.util.ArrayList;

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

    public void showAirPollution() {

    }

    public void showWeatherForecast() {

    }

    public void showWeatherHistory() {

    }
}
