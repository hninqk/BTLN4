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

/**
 * Main – Client entry point (JavaFX UI only).
 *
 * Connects automatically to the Render production server.
 * Friends just run this JAR – no configuration needed.
 *
 * The server is managed separately: see ServerMain.java / run_server.sh.
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        stage.initStyle(StageStyle.UNDECORATED);

        // Set application icon for title bar
        try {
            InputStream iconStream = getClass().getResourceAsStream("/com/auction/images/logo.png");
            if (iconStream != null) {
                Image icon = new Image(iconStream);
                stage.getIcons().add(icon);
            }
        } catch (Exception e) {
            System.err.println("Could not load application icon: " + e.getMessage());
        }

        // Make sure the application exits completely on close request
        stage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });

        // Fetch server time offset asynchronously
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
