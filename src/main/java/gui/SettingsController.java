package main.java.gui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import main.java.db.SettingsManager;

import java.io.IOException;

public class SettingsController {
    @FXML
    TextField darkskyApiKey;

    @FXML
    RadioButton debugModeYes;

    @FXML
    RadioButton debugModeNo;

    @FXML
    RadioButton localCacheYes;

    @FXML
    RadioButton localCacheNo;

    @FXML
    Button buttonOk;

    @FXML
    Button buttonCancel;

    public void initialize(Stage stage, Runnable onEventOk) {
        darkskyApiKey.setText(SettingsManager.MAINSETTINGS.get("darksky", "apiKey"));

        Boolean debugModeSelected = SettingsManager.MAINSETTINGS.get("darksky", "debugMode");
        debugModeYes.setSelected(debugModeSelected);
        debugModeNo.setSelected(!debugModeSelected);

        Boolean localCacheSelected = SettingsManager.MAINSETTINGS.get("darksky", "localCache");
        localCacheYes.setSelected(localCacheSelected);
        localCacheNo.setSelected(!localCacheSelected);

        buttonOk.setOnAction(e -> { saveSettings(); onEventOk.run(); stage.close(); });
        buttonCancel.setOnAction(e -> { stage.close();});
    }

    public void saveSettings() {
        SettingsManager.MAINSETTINGS.set("darksky", "apiKey", darkskyApiKey.getText());
        SettingsManager.MAINSETTINGS.set("darksky", "debugMode", debugModeYes.isSelected());
        SettingsManager.MAINSETTINGS.set("darksky", "localCache", localCacheYes.isSelected());

        try {
            SettingsManager.MAINSETTINGS.saveToFile();
        } catch (IOException e) {
            SimpleDialog.showErrorDialog(e.getMessage());
            e.printStackTrace();
        }
    }
}
