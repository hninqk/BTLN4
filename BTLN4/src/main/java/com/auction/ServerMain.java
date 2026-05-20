package com.auction;

import com.auction.server.AuctionWebSocketHandler;
import com.auction.server.RestApiHandler;
import com.auction.service.AuctionService;
import com.auction.service.UserService;
import com.auction.util.DatabaseConnection;
import io.javalin.Javalin;

import java.time.Duration;

/**
 * ServerMain – Headless server entry point.
 *
 * Starts the SQLite database + Javalin server on port 7000, exposing:
 * • WebSocket /auction – real-time bidding (unchanged)
 * • REST /api/** – HTTP endpoints for login, auctions, users
 *
 * HOW TO RUN (operator only):
 * ./run_server.sh
 * (or: mvn exec:java -Pserver -Dcheckstyle.skip=true)
 *
 * Then expose via ngrok:
 * ngrok http 7000
 *
 * When the ngrok URL changes, update DEFAULT_URL in ServerConfig.java,
 * rebuild the client JAR, and share the new JAR.
 */
public class ServerMain {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   Auction Server v2.0  (WebSocket + REST API)    ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        // 1. Initialize database (Server is the ONLY machine that touches SQLite)
        System.out.println("[Server] Initializing database...");
        DatabaseConnection.initialize();
        System.out.println("[Server] Database ready.");

        // 2. Build Javalin application
        AuctionService auctionService = AuctionService.getInstance();
        UserService userService = UserService.getInstance();

        AuctionWebSocketHandler wsHandler = new AuctionWebSocketHandler(auctionService);
        RestApiHandler restHandler = new RestApiHandler(auctionService, userService);

        Javalin server = Javalin
                .create(config -> config.jetty
                        .modifyWebSocketServletFactory(factory -> factory.setIdleTimeout(Duration.ofMinutes(10))))
                .start(7000);

        // 3. Register routes
        server.ws("/auction", wsHandler::register); // WebSocket (unchanged)
        restHandler.register(server); // REST API (new)

        System.out.println("[Server] WebSocket + REST server running on port 7000.");
        System.out.println("[Server] REST API base: http://localhost:7000/api");
        System.out.println("[Server] Expose publicly with:  ngrok http 7000");
        System.out.println("[Server] Press Ctrl+C to stop.");

        // 4. Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Shutting down...");
            server.stop();
            System.out.println("[Server] Goodbye.");
        }));

        // 5. Block forever (server stays alive until Ctrl+C)
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}