package com.auction.ui.support.realtime;

import com.auction.api.http.AuctionClient;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;

import java.util.function.Consumer;

public final class AuctionClientConnection implements AuctionRealtimeConnection {
    private final String threadName;
    private final String logPrefix;
    private final Gson gson = new Gson();
    private AuctionClient client;

    public AuctionClientConnection(String threadName, String logPrefix) {
        this.threadName = threadName;
        this.logPrefix = logPrefix;
    }

    @Override
    public void connect(Consumer<JsonObject> onMessage, Consumer<String> onError, Runnable onConnected) {
        client = new AuctionClient();
        Thread thread = new Thread(() -> client.connect(
                raw -> Platform.runLater(() -> handleRawMessage(raw, onMessage, onError)),
                err -> Platform.runLater(() -> onError.accept(err)),
                () -> Platform.runLater(onConnected)),
                threadName);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void send(JsonObject message) {
        if (client != null) {
            client.send(message.toString());
        }
    }

    @Override
    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public void disconnect() {
        if (client != null) {
            client.disconnect();
        }
    }

    private void handleRawMessage(String raw, Consumer<JsonObject> onMessage, Consumer<String> onError) {
        try {
            JsonElement element = gson.fromJson(raw, JsonElement.class);
            if (element == null || !element.isJsonObject()) {
                return;
            }
            onMessage.accept(element.getAsJsonObject());
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Unknown parse error" : e.getMessage();
            System.err.println(logPrefix + " WS parse error: " + message);
            onError.accept(message);
        }
    }
}
