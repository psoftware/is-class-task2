package main.java;

import com.sothawo.mapjfx.Projection;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;
import main.java.gui.Task2GUIController;

public class Task2 extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
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
}
