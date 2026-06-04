package com.auction;

import com.auction.api.server.AuctionWebSocketHandler;
import com.auction.api.server.RestApiHandler;
import com.auction.api.server.SecurityHeaderFilter;
import com.auction.service.AuctionService;
import com.auction.service.UserService;
import com.auction.api.config.AppConfig;
import com.auction.infra.db.DatabaseConnection;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;

public class ServerMain {

    private static final Logger log = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                log.error("Uncaught exception on thread {}", thread.getName(), error));
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   Auction Server v2.0  (WebSocket + REST API)    ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        log.info("Runtime configuration: {}", AppConfig.diagnostics());

        System.out.println("[Server] Initializing database...");
        DatabaseConnection.initialize();
        System.out.println("[Server] Database ready.");

        AuctionService auctionService = AuctionService.getInstance();
        UserService userService = UserService.getInstance();

        AuctionWebSocketHandler wsHandler = new AuctionWebSocketHandler(auctionService);
        RestApiHandler restHandler = new RestApiHandler(auctionService, userService);
        SecurityHeaderFilter securityFilter = new SecurityHeaderFilter();

        Javalin server = Javalin
                .create(config -> config.jetty
                        .modifyWebSocketServletFactory(factory -> factory.setIdleTimeout(Duration.ofMinutes(10))))
                .start(AppConfig.port());

        server.ws("/auction", wsHandler::register);
        restHandler.register(server);
        securityFilter.register(server);

        System.out.println("[Server] WebSocket + REST server running on port " + AppConfig.port() + ".");
        System.out.println("[Server] REST API base: " + AppConfig.httpBaseUrl() + "/api");
        System.out.println("[Server] Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Shutting down...");
            server.stop();
            System.out.println("[Server] Goodbye.");
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
