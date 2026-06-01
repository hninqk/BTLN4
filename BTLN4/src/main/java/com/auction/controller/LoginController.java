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
                User loggedIn = result.get();
                SessionManager.getInstance().setCurrentUser(loggedIn);

                // ── Per-user cache warm-up ──────────────────────────────────────
                // Only Bidders have bid history — skip entirely for Sellers/Admins.
                // Runs on a background thread so the navigation is not blocked.
                if (loggedIn instanceof com.auction.model.Bidder bidder) {
                    final String bidderId = bidder.getId();
                    Thread cacheThread = new Thread(() -> {
                        try {
                            // Reuse the auction list cached during splash (no extra HTTP call).
                            // If splash hasn't finished yet (very rare), fetch fresh.
                            java.util.List<com.auction.model.Auction> auctions =
                                    SplashController.cachedFullAuctions;
                            if (auctions == null || auctions.isEmpty()) {
                                auctions = AppFacade.getInstance().getAllAuctions();
                                // Fetch full details for each shallow auction
                                java.util.List<com.auction.model.Auction> full =
                                        new java.util.ArrayList<>(auctions.size());
                                for (com.auction.model.Auction a : auctions) {
                                    AppFacade.getInstance().findAuctionById(a.getId())
                                            .ifPresent(full::add);
                                }
                                auctions = full;
                            }
                            BidHistoryController.preloadCacheForUser(auctions, bidderId);
                            UserProfileController.preloadCacheForUser(auctions, bidderId);
                            System.out.println("[Login] Per-user cache warmed for bidder: " + bidderId);
                        } catch (Exception ex) {
                            System.err.println("[Login] Cache warm-up failed: " + ex.getMessage());
                        }
                    }, "login-cache-warmup");
                    cacheThread.setDaemon(true);
                    cacheThread.start();
                }

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
