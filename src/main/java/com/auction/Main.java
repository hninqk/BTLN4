package com.auction;

import com.auction.util.NavigationManager;
import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main – JavaFX entry point.
 * Bootstraps NavigationManager and loads the Login screen.
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Register the primary stage with the navigation manager
        NavigationManager nav = NavigationManager.getInstance();
        nav.setPrimaryStage(stage);

        stage.setTitle("Hệ thống Đấu giá Trực tuyến");
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        // Navigate to Login as the initial screen
        nav.navigateTo(NavigationManager.LOGIN, "Đăng nhập", null);

        stage.centerOnScreen();
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}