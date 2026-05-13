package com.auction;

import com.auction.server.AuctionWebSocketHandler;
import com.auction.service.AuctionService;
import com.auction.util.DatabaseConnection;
import com.auction.util.NavigationManager;
import com.auction.util.ServerConfig;
import io.javalin.Javalin;
import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

/**
 * Main – JavaFX + Javalin entry point.
 *
 * HOW TO RUN:
 *
 * === SERVER (the ONE machine that hosts the auction) ===
 *   ./run_with_ngrok.sh server
 *   (or via IDE: add VM arg  --server  in Application arguments)
 *   - Starts Javalin on port 7000 + opens the UI.
 *   - Then start ngrok:  ngrok http 7000
 *   - Share the ngrok wss:// URL with your friends.
 *
 * === CLIENT (everyone else) ===
 *   ./run_with_ngrok.sh client wss://&lt;ngrok-host&gt;/auction
 *   (or: java -Dauction.server.url=wss://... -jar app-1.0-SNAPSHOT.jar)
 *   - Opens only the UI, connects to the remote server – never starts a local server.
 *
 * NEVER hardcode the ngrok URL in this file.  The URL is supplied at runtime
 * via the system property  -Dauction.server.url=wss://...  set by the shell
 * script or by whoever distributes the JAR.
 */
public class Main extends Application {

    /** Set to true when this JVM instance is the designated server host. */
    private static boolean isServer = false;

    private static Javalin javalinServer;

    @Override
    public void start(Stage stage) throws IOException {
        // Detect --server flag passed as application parameter
        List<String> params = getParameters().getRaw();
        isServer = params.contains("--server");

        if (isServer) {
            // SERVER MODE: init DB and start WebSocket server
            System.out.println("[Main] Running in SERVER mode.");
            DatabaseConnection.initialize();
            startWebSocketServer();
        } else {
            // CLIENT MODE: just connect to whatever URL ServerConfig resolves
            System.out.println("[Main] Running in CLIENT mode.");
            System.out.println("[Main] Connecting to → " + ServerConfig.getServerUrl());
        }

        // Register the primary stage with the navigation manager
        NavigationManager nav = NavigationManager.getInstance();
        nav.setPrimaryStage(stage);

        stage.setTitle("Hệ thống Đấu giá Trực tuyến");
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        nav.navigateTo(NavigationManager.LOGIN, "Đăng nhập", null);

        stage.centerOnScreen();
        stage.show();
    }

    @Override
    public void stop() {
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

            System.out.println("[Main] WebSocket server started on port 7000.");
            System.out.println("[Main] Clients connect to → " + ServerConfig.getServerUrl());
        } catch (Exception e) {
            System.err.println("[Main] WebSocket server failed to start: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch();
    }
}