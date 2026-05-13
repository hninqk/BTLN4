package com.auction;

import com.auction.server.AuctionWebSocketHandler;
import com.auction.service.AuctionService;
import com.auction.util.DatabaseConnection;
import com.auction.util.NavigationManager;
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
 *   ngrok tcp 7000
 *   then pass: -Dauction.server.url=ws://<ngrok-host>:<port>/auction
 */
public class Main extends Application {

    private static Javalin javalinServer;

    @Override
    public void start(Stage stage) throws IOException {
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

            javalinServer = Javalin.create().start(7000);
            javalinServer.ws("/auction", handler::register);

            System.out.println("[Main] WebSocket server started → ws://localhost:7000/auction");
        } catch (Exception e) {
            System.err.println("[Main] WebSocket server failed to start: " + e.getMessage());
            // App continues running even without WS – falls back to local-only mode
        }
    }

    public static void main(String[] args) {
        launch();
    }
}