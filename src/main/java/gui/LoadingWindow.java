package main.java.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.concurrent.Callable;

public class LoadingWindow implements ProgressHandler {
    @FXML
    Text loadingmsg;

    @FXML
    ProgressBar progressBar;

    private int currentProgress = 0;
    private int maxProgress = 0;

    private Stage stage;

    public interface ThrowingRunnable<E extends Exception> {
        void run() throws E;
    }

    public interface ThrowingReportingRunnable<E extends Exception> {
        void run(ProgressHandler lw) throws E;
    }

    private void setupStage(String loadingtext) {
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

        this.stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(new Scene(root));
        stage.setTitle("Loading");
    }

    private void executeThreadOnFx(Thread t, String loadingtext, String successmsg) {
        // on exception: show error and wait for window closing, then close this window
        t.setUncaughtExceptionHandler((t1, e) -> Platform.runLater( () -> {
            SimpleDialog.showErrorDialog(e.getMessage());
            stage.close();
        }));
        t.start();

        stage.showAndWait();
    }

    public void showAndWaitCallable(ThrowingRunnable<Exception> bwork, String loadingtext, String successmsg) {
        setupStage(loadingtext);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        executeThreadOnFx(new Thread(() -> {
            try {
                bwork.run();
                Platform.runLater(() -> {
                    stage.close();
                    SimpleDialog.showConfirmDialog(successmsg);
                });
            }
            catch (Exception e) { throw new RuntimeException(e); }
        }), loadingtext, successmsg);
    }

    public void showAndWaitCallableWithBar(ThrowingReportingRunnable<Exception> bwork, String loadingtext, String successmsg) {
        setupStage(loadingtext);

        executeThreadOnFx(new Thread(() -> {
            try {
                bwork.run(this);
                Platform.runLater(() -> {
                    stage.close();
                    SimpleDialog.showConfirmDialog(successmsg);
                });
            }
            catch (Exception e) { throw new RuntimeException(e); }
        }), loadingtext, successmsg);
    }

    @Override
    public void reportProgressText(String text) {

    }

    @Override
    public void increaseProgress() {
        increaseProgress(++currentProgress);
    }

    @Override
    public void increaseProgress(int steps) {
        Platform.runLater(() -> {
            progressBar.setProgress((double)steps/maxProgress);
        });
    }

    @Override
    public void setMaxProgress(int steps) {
        this.maxProgress = steps;
    }
}
