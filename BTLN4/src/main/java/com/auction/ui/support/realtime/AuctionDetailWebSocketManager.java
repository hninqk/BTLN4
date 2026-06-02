package com.auction.ui.support.realtime;

import com.auction.core.model.Auction;
import com.auction.core.model.AuctionStatus;
import com.auction.core.model.BidTransaction;
import com.auction.core.model.Bidder;
import com.auction.core.model.User;
import com.auction.service.AuctionWebSocketService;
import com.auction.core.util.HotItemCache;
import com.auction.core.util.SessionManager;
import com.auction.core.util.TimeSyncManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class AuctionDetailWebSocketManager implements AuctionWebSocketService.AuctionWebSocketListener {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Auction currentAuction;
    private final Consumer<String> feedAppender;
    private final Consumer<BidTransaction> chartUpdater;
    private final Runnable uiRefresher;
    private final Consumer<String> errorReporter;
    private final Consumer<JsonObject> autoBidStatusHandler;
    private final Consumer<JsonObject> winnerAnnouncementHandler;

    public AuctionDetailWebSocketManager(
            Auction currentAuction,
            Consumer<String> feedAppender,
            Consumer<BidTransaction> chartUpdater,
            Runnable uiRefresher,
            Consumer<String> errorReporter,
            Consumer<JsonObject> autoBidStatusHandler,
            Consumer<JsonObject> winnerAnnouncementHandler) {
        this.currentAuction = currentAuction;
        this.feedAppender = feedAppender;
        this.chartUpdater = chartUpdater;
        this.uiRefresher = uiRefresher;
        this.errorReporter = errorReporter;
        this.autoBidStatusHandler = autoBidStatusHandler;
        this.winnerAnnouncementHandler = winnerAnnouncementHandler;
    }

    @Override
    public void onWsConnected() {
        Platform.runLater(uiRefresher);
    }

    @Override
    public void onWsDisconnected(String error) {
        Platform.runLater(() -> errorReporter.accept("Mất kết nối server - không thể đặt giá."));
    }

    @Override
    public void onWsError(String errorMsg) {
        Platform.runLater(() -> errorReporter.accept(errorMsg));
    }

    @Override
    public void onBidUpdate(JsonObject json) {
        String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;

        double amount = json.get("amount").getAsDouble();
        String bidderName = json.get("bidderUsername").getAsString();
        String bidderId = json.has("bidderId") ? json.get("bidderId").getAsString() : "remote";
        String timeStr = json.has("time") ? json.get("time").getAsString() : TimeSyncManager.getNow().toString();

        if (aid != null) HotItemCache.getInstance().recordBid(aid);

        LocalDateTime ts = LocalDateTime.parse(timeStr);
        if (currentAuction != null) {
            currentAuction.setHighestBid(amount);
            if (json.has("endTime")) {
                try {
                    currentAuction.setEndTime(LocalDateTime.parse(json.get("endTime").getAsString()));
                } catch (Exception ignored) {}
            }
            Bidder dummy = new Bidder(bidderId, TimeSyncManager.getNow(), bidderName, "", 0);
            BidTransaction dummyBid = new BidTransaction(UUID.randomUUID().toString(), ts, dummy, currentAuction, amount);
            currentAuction.injectBid(dummyBid);
            
            Platform.runLater(() -> {
                feedAppender.accept(String.format("[%s]  %s  →  %,.0f ₫", ts.format(TIME_FMT), bidderName, amount));
                chartUpdater.accept(dummyBid);
                uiRefresher.run();
            });
        }
    }

    @Override
    public void onAutoBidLog(JsonObject json) {
        String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;

        String msg = json.get("message").getAsString();
        Platform.runLater(() -> feedAppender.accept(String.format("[%s] Auto-Bid: %s", TimeSyncManager.getNow().format(TIME_FMT), msg)));
    }

    @Override
    public void onAutoBidAck(JsonObject json) {
        String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;

        Platform.runLater(() -> {
            autoBidStatusHandler.accept(json);
            uiRefresher.run();
        });
    }

    @Override
    public void onAutoBidStatus(JsonObject json) {
        String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;
        Platform.runLater(() -> autoBidStatusHandler.accept(json));
    }

    @Override
    public void onAutoBidDeactivated(JsonObject json) {
        String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;
        Platform.runLater(() -> autoBidStatusHandler.accept(json));
    }

    @Override
    public void onStatusChanged(JsonObject json) {
        String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;

        String newStatusStr = json.get("newStatus").getAsString();
        double highestBid = json.has("highestBid") ? json.get("highestBid").getAsDouble() : -1;
        String startTimeStr = json.has("startTime") ? json.get("startTime").getAsString() : "";
        String endTimeStr = json.has("endTime") ? json.get("endTime").getAsString() : "";

        AuctionStatus previousStatus = currentAuction != null ? currentAuction.getStatus() : null;

        if (currentAuction != null) {
            try {
                AuctionStatus newStatus = AuctionStatus.valueOf(newStatusStr);
                currentAuction.setStatus(newStatus);
            } catch (IllegalArgumentException ignored) {}

            if (highestBid >= 0) currentAuction.setHighestBid(highestBid);
            if (!startTimeStr.isEmpty()) {
                try { currentAuction.setStartTime(LocalDateTime.parse(startTimeStr)); } catch (Exception ignored) {}
            }
            if (!endTimeStr.isEmpty()) {
                try { currentAuction.setEndTime(LocalDateTime.parse(endTimeStr)); } catch (Exception ignored) {}
            }
        }

        Platform.runLater(() -> {
            uiRefresher.run();
            if ("CLOSED".equals(newStatusStr) && !AuctionStatus.CLOSED.equals(previousStatus)) {
                winnerAnnouncementHandler.accept(json);
            }
        });
    }

    @Override
    public void onBalanceUpdate(JsonObject json) {
        String bidderId = json.get("bidderId").getAsString();
        double newBalance = json.get("newBalance").getAsDouble();
        double frozen = json.has("frozenBalance") ? json.get("frozenBalance").getAsDouble() : -1;

        User me = SessionManager.getInstance().getCurrentUser();
        if (me instanceof Bidder myBidder && myBidder.getId().equals(bidderId)) {
            myBidder.setAccountBalance(newBalance);
            if (frozen >= 0) myBidder.setFrozenBalance(frozen);
            SessionManager.getInstance().setCurrentUser(myBidder);
            Platform.runLater(uiRefresher);
        }
    }

    @Override
    public void onFullSync(JsonObject json) {
        if (!json.has("auctions") || currentAuction == null) return;
        JsonArray auctions = json.get("auctions").getAsJsonArray();
        for (int i = 0; i < auctions.size(); i++) {
            JsonObject a = auctions.get(i).getAsJsonObject();
            String aid = a.get("auctionId").getAsString();
            if (aid.equals(currentAuction.getId())) {
                applyAuctionSnapshot(a);
                return;
            }
        }
    }

    private void applyAuctionSnapshot(JsonObject snap) {
        if (currentAuction == null) return;
        double highestBid = snap.get("highestBid").getAsDouble();
        currentAuction.setHighestBid(highestBid);

        if (snap.has("bidHistory")) {
            JsonArray bids = snap.get("bidHistory").getAsJsonArray();
            List<String> existingIds = currentAuction.getBidHistory().stream().map(BidTransaction::getId).toList();
            for (int i = 0; i < bids.size(); i++) {
                JsonObject b = bids.get(i).getAsJsonObject();
                String bidId = b.get("bidId").getAsString();
                if (!existingIds.contains(bidId)) {
                    double amt = b.get("amount").getAsDouble();
                    String bName = b.get("bidderUsername").getAsString();
                    String bId = b.get("bidderId").getAsString();
                    LocalDateTime ts = LocalDateTime.parse(b.get("time").getAsString());
                    Bidder dummy = new Bidder(bId, ts, bName, "", 0);
                    currentAuction.injectBid(new BidTransaction(bidId, ts, dummy, currentAuction, amt));
                }
            }
        }
        Platform.runLater(uiRefresher);
    }

    @Override
    public void onOutbid(JsonObject json) {}

    @Override
    public void onLegacyBidUpdate(JsonObject json) {
        onBidUpdate(json); // They are similar enough for client-side model updates
    }
}
