package com.auction.client;

import com.auction.util.ServerConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * AuctionClient – WebSocket client for real-time bid updates.
 *
 * Connects to the Javalin WS server (local or remote via ngrok).
 * All incoming messages are forwarded to the provided onMessage callback.
 * The caller is responsible for running UI updates on the JavaFX thread
 * (Platform.runLater) inside their callback.
 */
public class AuctionClient {

    private WebSocket ws;
    private boolean connected = false;

    /**
     * Opens the WebSocket connection.
     *
     * @param onMessage callback invoked for every text frame received
     * @param onError   callback invoked on connection failure (nullable)
     */
    public void connect(Consumer<String> onMessage, Consumer<String> onError) {
        String url = ServerConfig.getServerUrl();
        System.out.println("[AuctionClient] Connecting to: " + url);

        try {
            HttpClient client = HttpClient.newHttpClient();

            ws = client.newWebSocketBuilder()
                    .buildAsync(URI.create(url), new WebSocket.Listener() {

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket,
                                                         CharSequence data,
                                                         boolean last) {
                            onMessage.accept(data.toString());
                            webSocket.request(1);
                            return null;
                        }

                        @Override
                        public void onOpen(WebSocket webSocket) {
                            connected = true;
                            System.out.println("[AuctionClient] Connected.");
                            webSocket.request(1);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket,
                                                          int statusCode,
                                                          String reason) {
                            connected = false;
                            System.out.println("[AuctionClient] Disconnected: " + reason);
                            return null;
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            connected = false;
                            System.err.println("[AuctionClient] Error: " + error.getMessage());
                            if (onError != null) onError.accept(error.getMessage());
                        }
                    }).join();

        } catch (Exception e) {
            connected = false;
            System.err.println("[AuctionClient] Failed to connect: " + e.getMessage());
            if (onError != null) onError.accept(e.getMessage());
        }
    }

    /**
     * Sends a JSON message to the server (place bid request).
     */
    public void send(String json) {
        if (ws != null && connected) {
            ws.sendText(json, true);
        } else {
            System.err.println("[AuctionClient] Cannot send – not connected.");
        }
    }

    /**
     * Closes the WebSocket connection gracefully.
     */
    public void disconnect() {
        if (ws != null && connected) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "Client closing").join();
            connected = false;
        }
    }

    public boolean isConnected() { return connected; }
}