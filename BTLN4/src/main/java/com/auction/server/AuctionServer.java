package com.auction.server;

import com.auction.model.Auction;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Seller;
import com.auction.manager.AuctionManager;
import com.auction.service.AuctionService;
import io.javalin.Javalin;
import java.time.LocalDateTime;

public class AuctionServer {

    public static void main(String[] args) {
        try {
            // 1. Khởi tạo dữ liệu mẫu cho Auction
            Seller seller = new Seller("admin", "admin", "Admin Shop");
            Item item = new Electronics("Laptop", "Macbook Pro", 1000.0, seller, 12);
            Auction auction = new Auction(seller, item, LocalDateTime.now().plusDays(1));

            // Đăng ký vào AuctionManager
            AuctionManager.getInstance().createAuction(auction);

            // Chuyển trạng thái auction sang RUNNING để có thể bid
            auction.startAuction();

            // 2. Lấy Service
            AuctionService service = AuctionService.getInstance();

            // 3. Khởi tạo WebSocket Handler
            AuctionWebSocketHandler handler = new AuctionWebSocketHandler(service);

            // 4. Khởi tạo và chạy Javalin Server
            Javalin app = Javalin.create(config -> {
                config.bundledPlugins.enableDevLogging();
            }).start(7000);

            // Đăng ký websocket endpoint
            app.ws("/auction", handler::register);

            System.out.println("========================================");
            System.out.println("SERVER IS RUNNING");
            System.out.println("Endpoint: ws://localhost:7000/auction");
            System.out.println("Auctioning: " + item.getName() + " starting at $" + 1000.0);
            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}