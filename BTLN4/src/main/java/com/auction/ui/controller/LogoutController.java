package com.auction.ui.controller;

import com.auction.ui.util.NavigationManager;
import com.auction.core.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressIndicator;

import java.io.IOException;

/**
 * LogoutController - Shows a "Logging out..." screen for a short duration
 * to allow background tasks, WebSockets, and garbage collection to safely finish
 * before redirecting to the Login screen.
 */
public class LogoutController extends BaseController {

    @FXML
    private javafx.scene.layout.StackPane rootPane;

    @FXML
    private ProgressIndicator spinner;

    @FXML
    public void initialize() {
        // Run wave background for some visual flair during logout
        Platform.runLater(() -> {
            if (rootPane != null) {
                com.auction.ui.util.AnimationUtil.createWaveBackground(rootPane);
            }
        });

        // Background task to perform cleanup and wait for a safe delay
        taskRunner.run("logout-cleanup", () -> {
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
        }, result -> {
            try {
                nav.navigateTo(NavigationManager.LOGIN, "Đăng nhập", null);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }, null);
    }
}
