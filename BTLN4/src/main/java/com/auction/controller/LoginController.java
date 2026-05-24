package com.auction.controller;

import com.auction.model.User;
import com.auction.service.AppFacade;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.util.Optional;

/**
 * LoginController – handles user authentication via REST API.
 *
 * The login call is performed on a background thread (Task) so the FX thread
 * is never blocked during the network round-trip to the server.
 */
public class LoginController {

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;
    @FXML private Button        loginButton;

    @FXML
    public void initialize() {
        errorLabel.setText("");
        Platform.runLater(() -> {
            // Background wave animation
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(100));
            pause.setOnFinished(e -> {
                if (loginButton != null && loginButton.getScene() != null && loginButton.getScene().getRoot() instanceof javafx.scene.layout.Pane p) {
                    com.auction.util.AnimationUtil.createWaveBackground(p);
                }
            });
            pause.play();

            // Entrance animation for the card
            if (loginButton != null && loginButton.getScene() != null) {
                javafx.scene.Node authCard = loginButton.getScene().lookup(".auth-card");
                if (authCard != null) {
                    authCard.setOpacity(0.0);
                    authCard.setTranslateY(30);
                    
                    javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(800), authCard);
                    ft.setToValue(1.0);
                    
                    javafx.animation.TranslateTransition tt = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(800), authCard);
                    tt.setToY(0);
                    tt.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
                    
                    javafx.animation.ParallelTransition pt = new javafx.animation.ParallelTransition(ft, tt);
                    pt.play();
                }
            }
        });
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        errorLabel.setText("");
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
            return;
        }

        // Disable UI while the request is in flight
        loginButton.setDisable(true);
        errorLabel.setText("Đang kết nối server...");

        // Run the HTTP call on a background thread
        Task<Optional<User>> task = new Task<>() {
            @Override
            protected Optional<User> call() {
                return AppFacade.getInstance().login(username, password);
            }
        };

        task.setOnSucceeded(e -> {
            Optional<User> result = task.getValue();
            if (result.isPresent()) {
                SessionManager.getInstance().setCurrentUser(result.get());
                try {
                    NavigationManager.getInstance().navigateTo(
                            NavigationManager.DASHBOARD, "Tổng quan", null);
                } catch (IOException ex) {
                    errorLabel.setText("Lỗi hệ thống: không thể tải giao diện.");
                    ex.printStackTrace();
                }
            } else {
                errorLabel.setText("Tên đăng nhập hoặc mật khẩu không chính xác.");
                passwordField.clear();
                loginButton.setDisable(false);
            }
        });

        task.setOnFailed(e -> Platform.runLater(() -> {
            errorLabel.setText("Lỗi kết nối server: " + task.getException().getMessage());
            loginButton.setDisable(false);
        }));

        new Thread(task, "login-task").start();
    }

    @FXML
    private void handleGoToRegister(ActionEvent event) {
        try {
            NavigationManager.getInstance().navigateTo(NavigationManager.REGISTER, "Đăng ký", null);
        } catch (IOException e) {
            errorLabel.setText("Lỗi hệ thống: " + e.getMessage());
        }
    }
}