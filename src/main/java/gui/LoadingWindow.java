package main.java.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.concurrent.Callable;

public class LoadingWindow {
    @FXML
    Text loadingmsg;

    public interface ThrowingRunnable<E extends Exception> {
        void run() throws E;
    }

    public void showAndWaitCallable(ThrowingRunnable<Exception> bwork, String loadingtext, String successmsg) {
        FXMLLoader fxmlLoader = new FXMLLoader(SimpleDialog.class.getClassLoader().getResource("loading.fxml"));
        fxmlLoader.setController(this);

        Parent root = null;
        try {
            root = fxmlLoader.load();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        loadingmsg.setText(loadingtext);

        Stage stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(new Scene(root));
        stage.setTitle("Loading");

        // separate non-FX thread for background work
        Thread t = new Thread(() -> {
            try {
                bwork.run();
                Platform.runLater(() -> {
                    stage.close();
                    SimpleDialog.showConfirmDialog(successmsg);
                });
            }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        // on exception: show error and wait for window closing, then close this window
        t.setUncaughtExceptionHandler((t1, e) -> Platform.runLater( () -> {
            SimpleDialog.showErrorDialog(e.getMessage());
            stage.close();
        }));
        t.start();

        stage.showAndWait();
    }
}
