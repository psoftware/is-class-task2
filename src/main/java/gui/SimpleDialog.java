package main.java.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import main.java.User;

import java.io.IOException;
import java.util.ArrayList;


public abstract class SimpleDialog<T> extends Dialog<T> {

    public SimpleDialog(String title, String header) {
        // Create the custom dialog.
        this.setTitle(title);
        this.setHeaderText(header);

        //Set the button types.
        ButtonType confirmButtonType = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        this.getDialogPane().getButtonTypes().addAll(confirmButtonType);
/*
        // Create the username and password labels and fields.
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        this.addComponents(grid);

        this.getDialogPane().setContent(grid);
*/
        // Convert the result to a username-password-pair when the login button is clicked.
        this.setResultConverter(dialogButton -> null);
    }

    public abstract void addComponents(GridPane grid);
    public abstract T getValue();

    public static void showErrorDialog(String error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error Dialog");
        alert.setHeaderText("Error");
        alert.setContentText(error);

        alert.showAndWait();
    }

    public static void showIfErrorOrSuccess(LoadingWindow.ThrowingRunnable<Exception> r, String loadingtext, String successmsg) {
        new LoadingWindow().showAndWaitCallable(r, loadingtext, successmsg);
    }

    public static void showConfirmDialog(String info) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText("Operation completed successfully");
        alert.setContentText(info);

        alert.showAndWait();
    }
}