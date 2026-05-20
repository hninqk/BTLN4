package com.auction.client;

import com.auction.util.ServerConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * AuctionClient – WebSocket client for real-time multi-machine communication.
 *
 * Supports the extended message protocol:
 * send(json) – sends any JSON message to the server
 * Incoming messages are forwarded to the onMessage callback.
 *
 * Caller must wrap UI updates in Platform.runLater().
 */
public class AuctionClient {

    private WebSocket ws;
    private volatile boolean connected = false;

    // Called when the WS connection is successfully opened
    private Runnable onOpen;

    /**
     * Opens the WebSocket connection.
     *
     * @param onMessage callback for every incoming text frame
     * @param onError   callback on connection failure (nullable)
     */
    public void connect(Consumer<String> onMessage, Consumer<String> onError) {
        connect(onMessage, onError, null);
    }

    /**
     * Opens the WebSocket connection with an optional onOpen callback.
     *
     * @param onMessage callback for every incoming text frame
     * @param onError   callback on connection failure (nullable)
     * @param onOpen    called once when the WS handshake is complete (nullable)
     */
    public void connect(Consumer<String> onMessage, Consumer<String> onError, Runnable onOpen) {
        this.onOpen = onOpen;
        String baseUrl = ServerConfig.getServerUrl();
        String url = baseUrl.replaceFirst("^http", "ws");
        if (!url.endsWith("/auction")) {
            url += "/auction";
        }

        System.out.println("[AuctionClient] Connecting to: " + url);

        try {
            HttpClient client = HttpClient.newHttpClient();
            ws = client.newWebSocketBuilder()
                    .header("ngrok-skip-browser-warning", "true")
                    .buildAsync(URI.create(url), new WebSocket.Listener() {

                        private final StringBuilder messageBuffer = new StringBuilder();

                        @Override
                        public void onOpen(WebSocket webSocket) {
                            ws = webSocket;
                            connected = true;
                            System.out.println("[AuctionClient] Connected to server.");
                            webSocket.request(1);
                            if (AuctionClient.this.onOpen != null) {
                                AuctionClient.this.onOpen.run();
                            }
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket,
                                CharSequence data, boolean last) {
                            messageBuffer.append(data);
                            if (last) {
                                onMessage.accept(messageBuffer.toString());
                                messageBuffer.setLength(0);
                            }
                            webSocket.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket,
                                int statusCode, String reason) {
                            connected = false;
                            System.out.println("[AuctionClient] CLOSED: " + reason
                                    + " (Code: " + statusCode + ")");
                            return null;
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            connected = false;
                            System.err.println("[AuctionClient] ERROR: " + error.getMessage());
                            if (error.getCause() != null)
                                System.err.println("[AuctionClient] CAUSE: " + error.getCause().getMessage());
                            if (onError != null)
                                onError.accept(error.getMessage());
                        }
                    }).join();

        } catch (Exception e) {
            connected = false;
            String errMsg = e.getMessage();
            if (e.getCause() != null)
                errMsg += " | Cause: " + e.getCause().getMessage();
            System.err.println("[AuctionClient] CONNECTION FAILED: " + errMsg);
            if (onError != null)
                onError.accept(errMsg);
        }
    }

    /**
     * Sends a JSON message to the server.
     */
    public void send(String json) {
        if (ws != null && connected) {
            ws.sendText(json, true);
        } else {
            System.err.println("[AuctionClient] Cannot send – not connected.");
        }
    }

    /**
     * Closes the WebSocket gracefully.
     */
    public void disconnect() {
        if (ws != null && connected) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Client closing");
            } catch (Exception ignored) {
            }
            connected = false;
        }
    }

    public boolean isConnected() {
        return connected;
    }
}