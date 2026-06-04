package com.auction.ui.support.realtime;

import com.google.gson.JsonObject;
import java.util.function.Consumer;

public interface AuctionRealtimeConnection {
    void connect(Consumer<JsonObject> onMessage, Consumer<String> onError, Runnable onConnected);

    void send(JsonObject message);

    boolean isConnected();

    void disconnect();
}
