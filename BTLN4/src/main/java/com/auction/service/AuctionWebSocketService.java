package com.auction.service;

import com.auction.client.AuctionClient;
import com.auction.util.SessionManager;
import com.auction.model.User;
import com.auction.model.Bidder;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;

/**
 * Service to manage the WebSocket connection for the live auction.
 * Abstracts connection lifecycle, raw JSON parsing, and routing.
 */
public class AuctionWebSocketService {

    public interface AuctionWebSocketListener {
        void onWsConnected();
        void onWsDisconnected(String error);
        void onWsError(String errorMsg);
        void onBidUpdate(JsonObject json);
        void onAutoBidLog(JsonObject json);
        void onAutoBidAck(JsonObject json);
        void onAutoBidStatus(JsonObject json);
        void onAutoBidDeactivated(JsonObject json);
        void onStatusChanged(JsonObject json);
        void onBalanceUpdate(JsonObject json);
        void onFullSync(JsonObject json);
        void onLegacyBidUpdate(JsonObject json);
    }

    private AuctionClient wsClient;
    private final Gson gson = new Gson();
    private final AuctionWebSocketListener listener;
    private final String currentAuctionId;

    public AuctionWebSocketService(String auctionId, AuctionWebSocketListener listener) {
        this.currentAuctionId = auctionId;
        this.listener = listener;
    }

    public void connect() {
        wsClient = new AuctionClient();
        Thread t = new Thread(() -> {
            wsClient.connect(
                    msg -> Platform.runLater(() -> handleWsMessage(msg)),
                    err -> Platform.runLater(() -> listener.onWsDisconnected(err)),
                    // onOpen
                    () -> Platform.runLater(() -> {
                        listener.onWsConnected();
                        sendRequestSync();
                    })
            );
        }, "AuctionDetail-WS");
        t.setDaemon(true);
        t.start();
    }

    public void disconnect() {
        if (wsClient != null) {
            wsClient.disconnect();
        }
    }

    public void sendRequestSync() {
        if (wsClient == null || !wsClient.isConnected()) return;
        JsonObject req = new JsonObject();
        req.addProperty("type", "REQUEST_SYNC");
        wsClient.send(req.toString());

        User user = SessionManager.getInstance().getCurrentUser();
        if (user instanceof Bidder bidder && currentAuctionId != null) {
            JsonObject abReq = new JsonObject();
            abReq.addProperty("type", "CHECK_AUTO_BID");
            abReq.addProperty("auctionId", currentAuctionId);
            abReq.addProperty("bidderId", bidder.getId());
            wsClient.send(abReq.toString());
        }
    }

    public void send(String msg) {
        if (wsClient != null && wsClient.isConnected()) {
            wsClient.send(msg);
        }
    }

    public boolean isConnected() {
        return wsClient != null && wsClient.isConnected();
    }

    private void handleWsMessage(String msg) {
        try {
            JsonElement element = gson.fromJson(msg, JsonElement.class);
            if (!element.isJsonObject()) return;
            JsonObject json = element.getAsJsonObject();

            if (json.has("error")) {
                listener.onWsError(json.get("error").getAsString());
                return;
            }

            String type = json.has("type") ? json.get("type").getAsString() : "";

            switch (type) {
                case "BID_UPDATE"             -> listener.onBidUpdate(json);
                case "AUCTION_STATUS_CHANGED" -> listener.onStatusChanged(json);
                case "BALANCE_UPDATE"         -> listener.onBalanceUpdate(json);
                case "FULL_SYNC"              -> listener.onFullSync(json);
                case "AUTO_BID_LOG"           -> listener.onAutoBidLog(json);
                case "AUTO_BID_ACK"           -> listener.onAutoBidAck(json);
                case "AUTO_BID_STATUS"        -> listener.onAutoBidStatus(json);
                case "AUTO_BID_DEACTIVATED"   -> listener.onAutoBidDeactivated(json);
                // Legacy: bare bid response without "type" field
                default -> {
                    if (json.has("amount") && json.has("bidder")) {
                        listener.onLegacyBidUpdate(json);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[AuctionWebSocketService] WS parse error: " + e.getMessage());
        }
    }
}
