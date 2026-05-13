package com.auction;

import com.auction.server.AuctionWebSocketHandler;
import com.auction.service.AuctionService;
import com.auction.util.DatabaseConnection;
import io.javalin.Javalin;

/**
 * ServerMain – Headless server entry point.
 *
 * Starts the SQLite database + Javalin WebSocket server on port 7000.
 * NO JavaFX UI – runs entirely in the terminal.
 *
 * HOW TO RUN (operator only):
 *   ./run_server.sh
 *   (or: mvn exec:java -Pserver -Dcheckstyle.skip=true)
 *
 * Then expose via ngrok:
 *   ngrok http 7000
 *
 * When the ngrok URL changes, update DEFAULT_URL in ServerConfig.java,
 * rebuild the client JAR, and share the new JAR.
 */
public class ServerMain {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   Auction WebSocket Server v1.0      ║");
        System.out.println("╚══════════════════════════════════════╝");

        // 1. Initialize database
        System.out.println("[Server] Initializing database...");
        DatabaseConnection.initialize();
        System.out.println("[Server] Database ready.");

        // 2. Start WebSocket server
        AuctionService service = AuctionService.getInstance();
        AuctionWebSocketHandler handler = new AuctionWebSocketHandler(service);

        Javalin server = Javalin.create().start(7000);
        server.ws("/auction", handler::register);

        System.out.println("[Server] WebSocket server running on port 7000.");
        System.out.println("[Server] Expose publicly with:  ngrok http 7000");
        System.out.println("[Server] Press Ctrl+C to stop.");

        // 3. Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Shutting down...");
            server.stop();
            System.out.println("[Server] Goodbye.");
        }));

        // 4. Block forever (server stays alive until Ctrl+C)
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
