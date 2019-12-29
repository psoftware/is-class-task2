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

        showMapWindow(stage, loggedUser);
    }

    void showMapWindow(Stage stage, User user) throws IOException {
        FXMLLoader fxmlLoader = null;
        switch (user.getStatus()) {
            case ADMIN:
                fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("admin.fxml"));
                break;
            case ENABLED:
                fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("enabled.fxml"));
                break;
            case NOTENABLED:
                fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("notEnabled.fxml"));
        }

        Parent root = fxmlLoader.load();

        final Task2GUIController controller = fxmlLoader.<Task2GUIController>getController();
        final Projection projection = getParameters().getUnnamed().contains("wgs84")
                ? Projection.WGS_84 : Projection.WEB_MERCATOR;
        controller.initMapAndControls(projection, user);

        Scene scene = new Scene(root, 800, 600);
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
