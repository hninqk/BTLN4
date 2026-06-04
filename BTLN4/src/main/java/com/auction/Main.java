package com.auction;

import com.auction.core.util.TimeSyncManager;
import com.auction.ui.util.NavigationManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.image.Image;
import java.io.IOException;
import java.io.InputStream;

public class Main extends Application {

    @Override

    public void start(Stage stage) throws IOException {
        stage.initStyle(StageStyle.UNDECORATED);

        try {
            InputStream iconStream = getClass().getResourceAsStream("/com/auction/images/logo.png");
            if (iconStream != null) {
                Image icon = new Image(iconStream);
                stage.getIcons().add(icon);
            }
        } catch (Exception e) {
            System.err.println("Could not load application icon: " + e.getMessage());
        }

        stage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });

        new Thread(() -> com.auction.core.util.TimeSyncManager.syncTimeWithServer(), "TimeSync-Thread").start();

        NavigationManager nav = NavigationManager.getInstance();
        nav.setPrimaryStage(stage);

        stage.setTitle("Hệ thống Đấu giá Trực tuyến");
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.setMaxWidth(Double.MAX_VALUE);
        stage.setMaxHeight(Double.MAX_VALUE);

        nav.navigateTo(NavigationManager.SPLASH, "Đang tải...", null);

        stage.centerOnScreen();
        stage.show();
    }

    public static void main(String[] args) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        launch();
    }
}
