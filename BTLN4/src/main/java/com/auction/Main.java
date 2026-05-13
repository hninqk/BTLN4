package com.auction;

import com.auction.util.NavigationManager;
import com.auction.util.ServerConfig;
import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main – Client entry point (JavaFX UI only).
 *
 * Connects automatically to the public ngrok server.
 * Friends just run this JAR – no configuration needed.
 *
 * The server is managed separately: see ServerMain.java / run_server.sh.
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        System.out.println("[Client] Connecting to → " + ServerConfig.getServerUrl());

        NavigationManager nav = NavigationManager.getInstance();
        nav.setPrimaryStage(stage);

        stage.setTitle("Hệ thống Đấu giá Trực tuyến");
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        nav.navigateTo(NavigationManager.LOGIN, "Đăng nhập", null);

        stage.centerOnScreen();
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}