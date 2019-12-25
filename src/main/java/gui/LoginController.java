package main.java.gui;

import javafx.application.Platform;
import javafx.event.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import main.java.User;
import main.java.db.MongoDBManager;

/** Controls the login screen */
public class LoginController {
    @FXML private TextField user;
    @FXML private TextField password;
    @FXML private Button loginButton;

    private User loginUser;

    public void initialize() {
        loginButton.setOnAction(event -> onLoginButton(event));
    }

    private void onLoginButton(ActionEvent event) {
        loginUser = MongoDBManager.getInstance().getUserWithPassword(user.getText(), password.getText());
        if(loginUser == null)
            SimpleDialog.showErrorDialog("Invalid credentials");
        else {
            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.close();
        }

    }

    public User getLoggedUser() {
        return loginUser;
    }
}