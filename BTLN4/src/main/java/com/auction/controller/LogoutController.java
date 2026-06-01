package com.auction.controller;

import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;

import java.io.IOException;

/**
 * LogoutController - Shows a "Logging out..." screen for a short duration
 * to allow background tasks, WebSockets, and garbage collection to safely finish
 * before redirecting to the Login screen.
 */
public class LogoutController {

    @FXML
    private javafx.scene.layout.StackPane rootPane;

    @FXML
    private ProgressIndicator spinner;

    @FXML
    public void initialize() {
        // Run wave background for some visual flair during logout
        Platform.runLater(() -> {
            if (rootPane != null) {
                com.auction.util.AnimationUtil.createWaveBackground(rootPane);
            }
        });

        // Background task to perform cleanup and wait for a safe delay
        Task<Void> logoutTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Wait a tiny bit to let previous scenes fully detach
                Thread.sleep(300);

                // Clear per-user caches so the next user gets fresh data on login
                BidHistoryController.clearCache();
                UserProfileController.clearCache();

                // Perform the actual session logout logic (clearing user, etc.)
                SessionManager.getInstance().logoutUser();

                // Wait a bit more to let everything settle (simulating safe closure)
                Thread.sleep(600);

                return null;
            }
        };

        // When the safe delay is over, automatically redirect to Login
        logoutTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                try {
                    NavigationManager.getInstance().navigateTo(NavigationManager.LOGIN, "Đăng nhập", null);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
        });

        // Run the task on a background thread so the UI thread stays responsive
        Thread t = new Thread(logoutTask, "logout-cleanup-thread");
        t.setDaemon(true);
        t.start();
    }
}
