package com.auction.ui.controller;

import com.auction.ui.support.realtime.AuctionRealtimeConnection;
import com.auction.ui.support.realtime.AuctionClientConnection;
import com.google.gson.JsonObject;
import javafx.application.Platform;

/**
 * RealtimeController – base for controllers needing WebSocket connectivity.
 */
public abstract class RealtimeController extends BaseController {

    protected final AuctionRealtimeConnection realtime;

    public RealtimeController(String wsThreadName, String logPrefix) {
        this.realtime = new AuctionClientConnection(wsThreadName, logPrefix);
    }

    protected void setupRealtime() {
        realtime.connect(this::handleWsMessage, 
            err -> System.err.println(getClass().getSimpleName() + " WS error: " + err),
            () -> {});
    }

    /**
     * Handle incoming WebSocket messages. Must be implemented by subclasses.
     */
    protected abstract void handleWsMessage(JsonObject json);

    @Override
    public void cleanup() {
        super.cleanup();
        if (realtime != null) {
            realtime.disconnect();
        }
    }
}
