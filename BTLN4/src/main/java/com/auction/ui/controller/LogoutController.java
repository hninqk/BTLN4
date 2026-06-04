package com.auction.ui.controller;

import com.auction.ui.util.AnimationUtil;
import com.auction.ui.util.NavigationManager;
import com.auction.core.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressIndicator;
import java.io.IOException;

public class LogoutController extends BaseController {

    @FXML

    private javafx.scene.layout.StackPane rootPane;

    @FXML

    private ProgressIndicator spinner;

    @FXML

    public void initialize() {

        Platform.runLater(() -> {
            if (rootPane != null) {
                com.auction.ui.util.AnimationUtil.createWaveBackground(rootPane);
            }
        });

        taskRunner.run("logout-cleanup", () -> {

            Thread.sleep(300);

            BidHistoryController.clearCache();
            UserProfileController.clearCache();

            SessionManager.getInstance().logoutUser();

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
