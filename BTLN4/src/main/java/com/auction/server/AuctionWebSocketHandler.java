package com.auction.server;

import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;
import com.auction.model.*;
import com.auction.service.AuctionService;
import com.auction.service.UserService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * AuctionWebSocketHandler – full message-based protocol.
 *
 * CLIENT → SERVER messages (type field):
 *   PLACE_BID       – bidder places a bid on a running auction
 *   CREATE_AUCTION  – seller creates a new auction
 *   ADMIN_ACTION    – admin performs approve/start/finish/cancel
 *   REQUEST_SYNC    – client requests full state snapshot on connect
 *
 * SERVER → CLIENT broadcasts:
 *   BID_UPDATE            – new bid placed (full bidder info)
 *   AUCTION_CREATED       – new auction submitted by seller
 *   AUCTION_STATUS_CHANGED– auction status changed by admin
 *   BALANCE_UPDATE        – winner balance deducted (targeted to winner session)
 *   FULL_SYNC             – snapshot of all auctions sent on REQUEST_SYNC
 *   error                 – error for the requesting client only
 */
public class AuctionWebSocketHandler {

    private final AuctionService auctionService;
    private final UserService userService;
    private final Gson gson = new Gson();

    // All currently connected clients
    private final Set<WsContext> sessions = ConcurrentHashMap.newKeySet();

    // Scheduler for auto-finishing expired auctions
    private final ScheduledExecutorService autoFinishScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AutoFinish-Scheduler");
        t.setDaemon(true);
        return t;
    });

    public AuctionWebSocketHandler(AuctionService auctionService) {
        this.auctionService = auctionService;
        this.userService = UserService.getInstance();
        startAutoFinishScheduler();
    }

    /**
     * Chạy mỗi 30 giây: tự động kết thúc các phiên RUNNING đã hết giờ.
     * Đây là cơ chế đảm bảo tiền được trừ ngay khi auction hết giờ,
     * thay vì phải chờ Admin bấm nút "Kết thúc".
     */
    private void startAutoFinishScheduler() {
        autoFinishScheduler.scheduleAtFixedRate(() -> {
            try {
                List<Auction> all = auctionService.getAllAuctions();
                LocalDateTime now = LocalDateTime.now();
                for (Auction a : all) {
                    if (a.getStatus() == AuctionStatus.RUNNING
                            && a.getEndTime() != null
                            && now.isAfter(a.getEndTime())) {
                        try {
                            BidTransaction preWinner = a.getWinner();
                            auctionService.finishAuction(a);
                            broadcastFinishResult(a, preWinner);
                            System.out.printf("[AutoFinish] Auto-closed auction %s%n", a.getId());
                        } catch (Exception e) {
                            System.err.printf("[AutoFinish] Failed to finish auction %s: %s%n",
                                    a.getId(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[AutoFinish] Scheduler error: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
        System.out.println("[Server] Auto-finish scheduler started (every 30s).");
    }

    /**
     * Phát sóng kết quả khi 1 auction kết thúc (cả auto-finish và admin-finish).
     * Gửi: BALANCE_UPDATE tới winner + AUCTION_STATUS_CHANGED kèm thông tin winner.
     */
    private void broadcastFinishResult(Auction auction, BidTransaction preWinner) {
        // 1. Gửi BALANCE_UPDATE nếu có winner
        if (preWinner != null) {
            String winnerId = preWinner.getBidder().getId();
            userService.findById(winnerId)
                    .filter(u -> u instanceof Bidder)
                    .map(u -> (Bidder) u)
                    .ifPresent(winner -> {
                        JsonObject balUpdate = new JsonObject();
                        balUpdate.addProperty("type",       "BALANCE_UPDATE");
                        balUpdate.addProperty("bidderId",   winner.getId());
                        balUpdate.addProperty("newBalance", winner.getAccountBalance());
                        broadcastAll(balUpdate.toString());
                        System.out.printf("[Server] BALANCE_UPDATE bidder=%s newBalance=%.0f%n",
                                winner.getUsername(), winner.getAccountBalance());
                    });
        }

        // 2. Gửi AUCTION_STATUS_CHANGED kèm thông tin winner
        Auction fresh = auctionService.findById(auction.getId()).orElse(auction);
        JsonObject broadcast = new JsonObject();
        broadcast.addProperty("type",       "AUCTION_STATUS_CHANGED");
        broadcast.addProperty("auctionId",  fresh.getId());
        broadcast.addProperty("newStatus",  fresh.getStatus().name());
        broadcast.addProperty("highestBid", fresh.getHighestBid());
        broadcast.addProperty("startTime",  fresh.getStartTime() != null
                ? fresh.getStartTime().toString() : "");
        // Thêm thông tin người thắng cuộc
        if (preWinner != null) {
            broadcast.addProperty("winnerUsername", preWinner.getBidder().getUsername());
            broadcast.addProperty("winnerBid",      preWinner.getAmount());
        }
        broadcastAll(broadcast.toString());
    }

    // =========================================================================
    // REGISTER
    // =========================================================================

    public void register(WsConfig ws) {
        ws.onConnect(this::onConnect);
        ws.onClose(this::onClose);
        ws.onMessage(this::onMessage);
        ws.onError(ctx -> System.err.println(
                "[Server] WS Error: " + (ctx.error() != null ? ctx.error().getMessage() : "unknown")));
    }

    // =========================================================================
    // CONNECT / CLOSE
    // =========================================================================

    private void onConnect(WsContext ctx) {
        sessions.add(ctx);
        System.out.println("[Server] Client connected: " + ctx.sessionId()
                + "  (total=" + sessions.size() + ")");
    }

    private void onClose(WsContext ctx) {
        sessions.remove(ctx);
        System.out.println("[Server] Client disconnected: " + ctx.sessionId()
                + "  (total=" + sessions.size() + ")");
    }

    // =========================================================================
    // MESSAGE DISPATCH
    // =========================================================================

    private void onMessage(WsMessageContext ctx) {
        try {
            JsonObject req = gson.fromJson(ctx.message(), JsonObject.class);
            String type = req.has("type") ? req.get("type").getAsString() : "PLACE_BID";

            switch (type) {
                case "PLACE_BID"      -> handlePlaceBid(ctx, req);
                case "CREATE_AUCTION" -> handleCreateAuction(ctx, req);
                case "ADMIN_ACTION"   -> handleAdminAction(ctx, req);
                case "REQUEST_SYNC"   -> handleRequestSync(ctx);
                default               -> sendError(ctx, "Unknown message type: " + type);
            }
        } catch (Exception e) {
            sendError(ctx, "Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // PLACE_BID
    // =========================================================================

    private void handlePlaceBid(WsMessageContext ctx, JsonObject req) {
        try {
            String auctionId      = req.get("auctionId").getAsString();
            String bidderId       = req.get("bidderId").getAsString();
            String bidderUsername = req.has("bidderUsername") ? req.get("bidderUsername").getAsString() : null;
            double bidderBalance  = req.has("bidderBalance")  ? req.get("bidderBalance").getAsDouble()  : 0.0;
            double amount         = req.get("amount").getAsDouble();

            // ── Load auction from server DB ──
            Auction auction = auctionService.findById(auctionId)
                    .orElseThrow(() -> new Exception("Auction not found: " + auctionId));

            // ── Load or auto-register bidder on server DB ──
            Bidder bidder = (Bidder) userService.findById(bidderId)
                    .filter(u -> u instanceof Bidder)
                    .orElseGet(() -> {
                        String uname = bidderUsername != null
                                ? bidderUsername
                                : "Guest_" + bidderId.substring(0, Math.min(6, bidderId.length()));
                        System.out.println("[Server] Auto-registering remote bidder: " + uname);
                        // Use client-reported balance only for new bidder registration
                        Bidder nb = new Bidder(bidderId, LocalDateTime.now(), uname, "remote_pass", bidderBalance);
                        userService.saveUser(nb);
                        return nb;
                    });
            // Server balance is authoritative — never overwrite with client-reported value

            // ── Process bid (validates, saves, updates highest_bid in DB) ──
            // Returns the created BidTransaction with the exact server-assigned timestamp
            BidTransaction createdBid = auctionService.placeBid(auction, bidder, amount);

            // ── Broadcast BID_UPDATE to ALL clients ──
            JsonObject broadcast = new JsonObject();
            broadcast.addProperty("type",           "BID_UPDATE");
            broadcast.addProperty("auctionId",      auction.getId());
            broadcast.addProperty("amount",         amount);
            broadcast.addProperty("bidderId",       bidder.getId());
            broadcast.addProperty("bidderUsername", bidder.getUsername());
            // Use the exact timestamp from the created bid — NOT LocalDateTime.now() at broadcast time
            broadcast.addProperty("time",           createdBid.getTimestamp().toString());
            broadcastAll(broadcast.toString());

            System.out.printf("[Server] BID_UPDATE  auction=%s  bidder=%s  amount=%.0f%n",
                    auctionId, bidder.getUsername(), amount);

        } catch (InvalidBidException | InvalidStatusException e) {
            sendError(ctx, e.getMessage());
        } catch (Exception e) {
            sendError(ctx, e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // CREATE_AUCTION
    // =========================================================================

    private void handleCreateAuction(WsMessageContext ctx, JsonObject req) {
        try {
            String sellerId   = req.get("sellerId").getAsString();
            String itemName   = req.get("itemName").getAsString();
            String category   = req.has("category")    ? req.get("category").getAsString()    : "Điện tử";
            String desc       = req.has("description") ? req.get("description").getAsString() : "";
            String imageUrl   = req.has("imageUrl")    ? req.get("imageUrl").getAsString()    : "";
            double startPrice = req.get("startPrice").getAsDouble();
            String endTimeStr = req.get("endTime").getAsString();
            LocalDateTime endTime = LocalDateTime.parse(endTimeStr);

            // ── Load seller from server DB ──
            Seller seller = (Seller) userService.findById(sellerId)
                    .filter(u -> u instanceof Seller)
                    .orElseThrow(() -> new Exception("Seller not found: " + sellerId));

            // ── Build item ──
            Item item = switch (category) {
                case "Nghệ thuật" -> new Art(itemName, desc, startPrice, seller);
                case "Xe cộ"      -> new Vehicle(itemName, desc, startPrice, seller);
                default           -> new Electronics(itemName, desc, startPrice, seller);
            };
            item.setImageUrl(imageUrl);

            // ── Create auction (PENDING status) ──
            Auction auction = auctionService.createAuction(seller, item, endTime);

            // ── Broadcast AUCTION_CREATED to ALL clients ──
            JsonObject broadcast = AuctionSerializer.auctionToJson("AUCTION_CREATED", auction, false);
            broadcastAll(broadcast.toString());

            System.out.printf("[Server] AUCTION_CREATED  id=%s  item=%s  seller=%s%n",
                    auction.getId(), itemName, seller.getUsername());

        } catch (Exception e) {
            sendError(ctx, e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // ADMIN_ACTION
    // =========================================================================

    private void handleAdminAction(WsMessageContext ctx, JsonObject req) {
        try {
            String action    = req.get("action").getAsString();    // approve|start|finish|cancel
            String auctionId = req.get("auctionId").getAsString();

            Auction auction = auctionService.findById(auctionId)
                    .orElseThrow(() -> new Exception("Auction not found: " + auctionId));

            switch (action) {
                case "approve" -> auctionService.approveAuction(auction);
                case "start"   -> auctionService.startAuction(auction);
                case "finish"  -> {
                    BidTransaction preWinner = auction.getWinner();
                    auctionService.finishAuction(auction);
                    broadcastFinishResult(auction, preWinner);
                    // broadcastFinishResult đã gửi BALANCE_UPDATE + AUCTION_STATUS_CHANGED
                    // → return sớm để tránh broadcast trùng bên dưới
                    System.out.printf("[Server] AUCTION_STATUS_CHANGED id=%s action=finish status=CLOSED%n",
                            auctionId);
                    return;
                }
                case "cancel"  -> auctionService.cancelAuction(auction);
                default        -> throw new Exception("Unknown admin action: " + action);
            }

            // Reload fresh auction state from DB after the operation
            Auction fresh = auctionService.findById(auctionId).orElse(auction);

            // ── Broadcast AUCTION_STATUS_CHANGED to ALL (approve/start/cancel) ──
            JsonObject broadcast = new JsonObject();
            broadcast.addProperty("type",        "AUCTION_STATUS_CHANGED");
            broadcast.addProperty("auctionId",   fresh.getId());
            broadcast.addProperty("newStatus",   fresh.getStatus().name());
            broadcast.addProperty("highestBid",  fresh.getHighestBid());
            broadcast.addProperty("startTime",   fresh.getStartTime() != null
                    ? fresh.getStartTime().toString() : "");
            broadcastAll(broadcast.toString());

            System.out.printf("[Server] AUCTION_STATUS_CHANGED  id=%s  action=%s  status=%s%n",
                    auctionId, action, fresh.getStatus().name());

        } catch (InvalidStatusException e) {
            sendError(ctx, e.getMessage());
        } catch (Exception e) {
            sendError(ctx, e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // REQUEST_SYNC
    // =========================================================================

    private void handleRequestSync(WsMessageContext ctx) {
        try {
            List<Auction> all = auctionService.getAllAuctions();
            JsonArray arr = new JsonArray();
            for (Auction a : all) {
                arr.add(AuctionSerializer.auctionToJson(a, false));
            }
            JsonObject resp = new JsonObject();
            resp.addProperty("type", "FULL_SYNC");
            resp.add("auctions", arr);
            ctx.send(resp.toString());
            System.out.println("[Server] FULL_SYNC sent to " + ctx.sessionId()
                    + "  (" + all.size() + " auctions)");
        } catch (Exception e) {
            sendError(ctx, e.getMessage());
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void broadcastAll(String msg) {
        for (WsContext s : sessions) {
            if (s.session.isOpen()) {
                try { s.send(msg); } catch (Exception ignored) {}
            }
        }
    }

    private void sendError(WsContext ctx, String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message != null ? message.replace("\"", "'") : "Unknown error");
        try { ctx.send(err.toString()); } catch (Exception ignored) {}
    }
}
