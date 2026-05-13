package com.auction;

import com.auction.server.AuctionWebSocketHandler;
import com.auction.service.AuctionService;
import com.auction.util.DatabaseConnection;
import com.auction.util.NavigationManager;
import com.auction.util.ServerConfig; // Added import
import io.javalin.Javalin;
import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main – JavaFX + Javalin entry point.
 *
 * Starts the embedded WebSocket server (port 7000) BEFORE showing the UI,
 * so all clients (local or remote via ngrok) connect to the same auction state.
 *
 * To use ngrok:
 * 1. Run: ngrok http 7000
 * 2. Copy the https URL provided by ngrok.
 * 3. Update the ServerConfig.setServerUrl() below, ensuring you use "wss://"
 * instead of "https://".
 */
public class Main extends Application {

    private static Javalin javalinServer;

    @Override
    public void start(Stage stage) throws IOException {
        // === NGROK CONFIGURATION ===
        // Note: Since you are on the free tier of ngrok, this URL will change every
        // time
        // you restart the ngrok terminal. Update this string whenever you restart
        // ngrok.
        ServerConfig.setServerUrl("wss://valeria-witless-stellularly.ngrok-free.dev/auction");

        // 1. Init database
        DatabaseConnection.initialize();

        // 2. Start embedded WebSocket server on port 7000
        startWebSocketServer();

        // 3. Register the primary stage with the navigation manager
        NavigationManager nav = NavigationManager.getInstance();
        nav.setPrimaryStage(stage);

        stage.setTitle("Hệ thống Đấu giá Trực tuyến");
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        // 4. Navigate to Login as the initial screen
        nav.navigateTo(NavigationManager.LOGIN, "Đăng nhập", null);

        stage.centerOnScreen();
        stage.show();
    }

    @Override
    public void stop() {
        // Gracefully stop Javalin when JavaFX window closes
        if (javalinServer != null) {
            javalinServer.stop();
            System.out.println("[Main] WebSocket server stopped.");
        }
    }

    private static void startWebSocketServer() {
        try {
            AuctionService service = AuctionService.getInstance();
            AuctionWebSocketHandler handler = new AuctionWebSocketHandler(service);

            // The local server ALWAYS runs on 7000. Ngrok just tunnels TO this port.
            javalinServer = Javalin.create().start(7000);
            javalinServer.ws("/auction", handler::register);

            // Dynamically print the active URL (Ngrok or Localhost)
            System.out.println("[Main] WebSocket server started.");
            System.out.println("[Main] Clients should connect to → " + ServerConfig.getServerUrl());
        } catch (Exception e) {
            System.err.println("[Main] WebSocket server failed to start: " + e.getMessage());
            // App continues running even without WS – falls back to local-only mode
        }
    }

    public static void main(String[] args) {
        launch();
    }
}