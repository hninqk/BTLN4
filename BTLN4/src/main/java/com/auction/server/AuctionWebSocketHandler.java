package com.auction.server;

import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.service.AuctionService;
import com.auction.service.UserService;
import com.google.gson.Gson;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionWebSocketHandler {

    private final AuctionService service;
    private final Gson gson = new Gson();

    // lưu tất cả client đang connect
    private final Set<WsContext> sessions = ConcurrentHashMap.newKeySet();

    public AuctionWebSocketHandler(AuctionService service) {
        this.service = service;
    }

    // =============================
    // REGISTER HANDLER
    // =============================
    public void register(WsConfig ws) {
        ws.onConnect(this::onConnect);
        ws.onClose(this::onClose);
        ws.onMessage(this::onMessage);
        ws.onError(ctx -> {
            System.err.println("WS Error: " + (ctx.error() != null ? ctx.error().getMessage() : "Unknown error"));
        });
    }

    // =============================
    // CONNECT
    // =============================
    private void onConnect(WsContext ctx) {
        sessions.add(ctx);
        System.out.println("Client connected: " + ctx.sessionId());
    }

    // =============================
    // CLOSE
    // =============================
    private void onClose(WsContext ctx) {
        sessions.remove(ctx);
        System.out.println("Client disconnected: " + ctx.sessionId());
    }

    // =============================
    // MESSAGE
    // =============================
    private void onMessage(WsMessageContext ctx) {
        try {
            String msg = ctx.message();
            BidRequest req = gson.fromJson(msg, BidRequest.class);

            if (req.auctionId == null || req.bidderId == null) {
                // Fallback to old simple amount-only parsing if needed
                double amount = parseAmount(msg);
                if (amount > 0) {
                   ctx.send("Error: Please provide auctionId and bidderId in JSON format.");
                }
                return;
            }

            // ===== Retrieve data from services =====
            Auction auction = AuctionService.getInstance().findById(req.auctionId)
                    .orElseThrow(() -> new Exception("Auction not found: " + req.auctionId));
            
            Bidder bidder = UserService.getInstance().findById(req.bidderId)
                    .filter(u -> u instanceof Bidder)
                    .map(u -> (Bidder) u)
                    .orElseThrow(() -> new Exception("Bidder not found: " + req.bidderId));

            // ===== xử lý bid (thread-safe ở service) =====
            service.placeBid(auction, bidder, req.amount);

            // Fetch latest bid to broadcast
            BidTransaction latest = auction.getWinner();

            // ===== broadcast cho tất cả client =====
            String json = toJson(latest);
            broadcast(json);

        } catch (InvalidBidException | InvalidStatusException e) {
            ctx.send("Error: " + e.getMessage());
        } catch (Exception e) {
            ctx.send("System Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =============================
    // BROADCAST
    // =============================
    private void broadcast(String msg) {
        for (WsContext s : sessions) {
            if (s.session.isOpen()) {
                try {
                    s.send(msg);
                } catch (Exception ignored) {}
            }
        }
    }

    // =============================
    // PARSE
    // =============================
    private double parseAmount(String msg) {
        try {
            return Double.parseDouble(msg.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // =============================
    // TO JSON
    // =============================
    private String toJson(BidTransaction bid) {
        if (bid == null) return "{}";
        return gson.toJson(new BidResponse(
                bid.getAmount(),
                bid.getBidder().getUsername(),
                bid.getTimestamp().toString()
        ));
    }

    // DTO for incoming requests
    private static class BidRequest {
        String auctionId;
        String bidderId;
        double amount;
    }

    private static class BidResponse {
        double amount;
        String bidder;
        String time;

        BidResponse(double amount, String bidder, String time) {
            this.amount = amount;
            this.bidder = bidder;
            this.time = time;
        }
    }
}
