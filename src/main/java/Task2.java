package main.java;

import com.sothawo.mapjfx.Projection;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;
import main.java.gui.LoginController;
import main.java.gui.Task2GUIController;

import java.io.IOException;

public class Task2 extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        User loggedUser = showAndWaitLogin();
        if(loggedUser == null) // exit if close button is used
            return;
        System.out.println(loggedUser);

        showMapWindow(stage);
    }

    void showMapWindow(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("task2.fxml"));
        Parent root = fxmlLoader.load();

        final Task2GUIController controller = fxmlLoader.<Task2GUIController>getController();
        final Projection projection = getParameters().getUnnamed().contains("wgs84")
                ? Projection.WGS_84 : Projection.WEB_MERCATOR;
        controller.initMapAndControls(projection);

        Scene scene = new Scene(root, 300, 275);
        stage.setTitle("Map Page");
        stage.setScene(scene);
        stage.show();
    }

    private User showAndWaitLogin() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("login.fxml"));
        Parent root = fxmlLoader.load();

        final LoginController controller = fxmlLoader.<LoginController>getController();
        controller.initialize();

        Scene scene = new Scene(root);
        Stage newStage = new Stage();
        newStage.setTitle("Login");
        newStage.setScene(scene);
        newStage.showAndWait();

        return controller.getLoggedUser();
    }
}
