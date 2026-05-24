package com.auction.controller;

import com.auction.model.User;
import com.auction.service.AppFacade;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Optional;

/**
 * LoginController – handles user authentication via REST API.
 *
 * The login call is performed on a background thread (Task) so the FX thread
 * is never blocked during the network round-trip to the server.
 */
public class LoginController {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    @FXML
    private Button loginButton;
    @FXML
    private StackPane rootPane;
    private Timeline loginDotsTimeline;
    private int loginDotsCount;

    @FXML
    public void initialize() {
        errorLabel.setText("");
        Platform.runLater(() -> {
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
                    javafx.util.Duration.millis(100));
            pause.setOnFinished(e -> {
                if (rootPane != null) {
                    com.auction.util.AnimationUtil.createWaveBackground(rootPane);
                }
            });
            pause.play();
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
        startLoginDots();

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
                    stopLoginDots();
                    errorLabel.setText("Lỗi hệ thống: không thể tải giao diện.");
                    ex.printStackTrace();
                }
            } else {
                stopLoginDots();
                errorLabel.setText("Tên đăng nhập hoặc mật khẩu không chính xác.");
                passwordField.clear();
                loginButton.setDisable(false);
            }
        });

        task.setOnFailed(e -> Platform.runLater(() -> {
            stopLoginDots();
            errorLabel.setText(task.getException().getMessage());
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

    private void startLoginDots() {
        stopLoginDots();
        loginDotsCount = 1;
        errorLabel.setText("Đang đăng nhập.");
        loginDotsTimeline = new Timeline(new KeyFrame(Duration.millis(350), e -> {
            loginDotsCount = loginDotsCount % 3 + 1;
            errorLabel.setText("Đang đăng nhập" + ".".repeat(loginDotsCount));
        }));
        loginDotsTimeline.setCycleCount(Timeline.INDEFINITE);
        loginDotsTimeline.play();
    }

    private void stopLoginDots() {
        if (loginDotsTimeline != null) {
            loginDotsTimeline.stop();
            loginDotsTimeline = null;
        }
    }
}
