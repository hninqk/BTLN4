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
                ctx.send("{\"error\":\"Missing auctionId or bidderId\"}");
                return;
            }

            // Retrieve auction and bidder from server's DB
            Auction auction = AuctionService.getInstance().findById(req.auctionId)
                    .orElseThrow(() -> new Exception("Auction not found: " + req.auctionId));

            Bidder bidder = UserService.getInstance().findById(req.bidderId)
                    .filter(u -> u instanceof Bidder)
                    .map(u -> (Bidder) u)
                    .orElseGet(() -> {
                        String uname = req.bidderUsername;
                        if (uname == null) {
                            uname = "Guest_" + req.bidderId.substring(0, Math.min(6, req.bidderId.length()));
                        }
                        System.out.println("[Server] Auto-registering remote bidder: " + uname);
                        Bidder newBidder = new Bidder(
                                req.bidderId, 
                                java.time.LocalDateTime.now(), 
                                uname, 
                                "remote_user_pass", 
                                req.bidderBalance != null ? req.bidderBalance : 5000000.0
                        );
                        UserService.getInstance().saveUser(newBidder);
                        return newBidder;
                    });

            // Process the bid
            service.placeBid(auction, bidder, req.amount);

            // ── Broadcast the latest bid (NOT getWinner() — that's null until CLOSED) ──
            java.util.List<BidTransaction> history = auction.getBidHistory();
            if (!history.isEmpty()) {
                BidTransaction latest = history.get(history.size() - 1);
                broadcast(toJson(latest));
            }

        } catch (InvalidBidException | InvalidStatusException e) {
            // Send error only to the bidder who triggered it
            ctx.send("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        } catch (Exception e) {
            ctx.send("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
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
                bid.getAuction().getId(),
                bid.getAmount(),
                bid.getBidder().getUsername(),
                bid.getTimestamp().toString()
        ));
    }

    // DTO for incoming requests
    private static class BidRequest {
        String type;
        String auctionId;
        String bidderId;
        String bidderUsername;
        Double bidderBalance;
        double amount;
    }

    private static class BidResponse {
        String auctionId;
        double amount;
        String bidder;
        String time;

        BidResponse(String auctionId, double amount, String bidder, String time) {
            this.auctionId = auctionId;
            this.amount = amount;
            this.bidder = bidder;
            this.time = time;
        }
    }
}
