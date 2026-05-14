package com.auction.client;

import com.auction.util.ServerConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * ApiClient – HTTP bridge between the JavaFX Client and the Javalin REST server.
 *
 * DESIGN
 * ──────
 * • Reads the server base URL from ServerConfig (which honours -Dauction.server.url).
 *   ws://host/auction  →  http://host
 *   wss://host/auction →  https://host
 * • Every request sends the "ngrok-skip-browser-warning: true" header so the
 *   ngrok browser-warning redirect never interferes with HTTP calls.
 * • Provides both async (CompletableFuture) and blocking (…Sync) overloads.
 *   Controllers ALWAYS use the async overloads from the FX thread and update
 *   UI inside Platform.runLater().  AppFacade uses the blocking overloads from
 *   inside a Task (already off the FX thread).
 *
 * USAGE EXAMPLE (controller)
 * ──────────────────────────
 * Task<List<Auction>> task = new Task<>() {
 *     @Override protected List<Auction> call() {
 *         return AppFacade.getInstance().getPublicAuctions();
 *     }
 * };
 * task.setOnSucceeded(e -> Platform.runLater(() -> table.setItems(
 *         FXCollections.observableArrayList(task.getValue()))));
 * new Thread(task, "fetch-auctions").start();
 */
public class ApiClient {

    private static ApiClient instance;

    private final HttpClient httpClient;
    private final Gson       gson = new Gson();

    // Lazily derived from ServerConfig so any runtime URL override is respected
    private String cachedBaseUrl;

    private ApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    // ── URL helpers ───────────────────────────────────────────────────────────

    /**
     * Derives the HTTP base URL from the WebSocket URL stored in ServerConfig.
     * Examples:
     *   ws://localhost:7000/auction  →  http://localhost:7000
     *   wss://abc.ngrok-free.app/auction → https://abc.ngrok-free.app
     */
    public String getBaseUrl() {
        // Re-derive each call to pick up runtime URL changes (setServerUrl)
        String wsUrl = ServerConfig.getServerUrl();
        String httpUrl = wsUrl
                .replaceFirst("^wss://", "https://")
                .replaceFirst("^ws://",  "http://");
        // Strip trailing /auction (or any path component)
        int slashAfterHost = httpUrl.indexOf('/', httpUrl.indexOf("://") + 3);
        if (slashAfterHost > 0) {
            httpUrl = httpUrl.substring(0, slashAfterHost);
        }
        cachedBaseUrl = httpUrl;
        return cachedBaseUrl;
    }

    // ── Async API (for use directly from Task or CompletableFuture) ───────────

    /**
     * Async HTTP GET.
     * @param path e.g. "/api/auctions" or "/api/auctions?filter=public"
     * @return CompletableFuture carrying the raw response body string
     */
    public CompletableFuture<String> getAsync(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("ngrok-skip-browser-warning", "true")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    /**
     * Async HTTP POST with JSON body.
     * @param path e.g. "/api/login"
     * @param body a JsonObject to send as the request body
     * @return CompletableFuture carrying the raw response body string
     */
    public CompletableFuture<String> postAsync(String path, JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("ngrok-skip-browser-warning", "true")
                .POST(HttpRequest.BodyPublishers.ofString(
                        body.toString(), StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    /**
     * Async HTTP DELETE.
     */
    public CompletableFuture<String> deleteAsync(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("ngrok-skip-browser-warning", "true")
                .DELETE()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    /**
     * Async HTTP PUT with JSON body.
     */
    public CompletableFuture<String> putAsync(String path, JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("ngrok-skip-browser-warning", "true")
                .PUT(HttpRequest.BodyPublishers.ofString(
                        body.toString(), StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    // ── Blocking API (for use inside Task.call() — already off FX thread) ────

    /**
     * Blocking HTTP GET. Returns the raw body string or null on error.
     * MUST be called from a background thread (e.g., inside Task.call()).
     */
    public String getSync(String path) {
        try {
            return getAsync(path).get();
        } catch (Exception e) {
            System.err.println("[ApiClient] GET " + path + " failed: " + e.getMessage());
            throw new RuntimeException("GET " + path + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Blocking HTTP POST. Returns the raw body string or throws on error.
     * MUST be called from a background thread (e.g., inside Task.call()).
     */
    public String postSync(String path, JsonObject body) {
        try {
            return postAsync(path, body).get();
        } catch (Exception e) {
            System.err.println("[ApiClient] POST " + path + " failed: " + e.getMessage());
            throw new RuntimeException("POST " + path + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Blocking HTTP DELETE.
     */
    public String deleteSync(String path) {
        try {
            return deleteAsync(path).get();
        } catch (Exception e) {
            System.err.println("[ApiClient] DELETE " + path + " failed: " + e.getMessage());
            throw new RuntimeException("DELETE " + path + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Blocking HTTP PUT.
     */
    public String putSync(String path, JsonObject body) {
        try {
            return putAsync(path, body).get();
        } catch (Exception e) {
            System.err.println("[ApiClient] PUT " + path + " failed: " + e.getMessage());
            throw new RuntimeException("PUT " + path + " failed: " + e.getMessage(), e);
        }
    }

    // ── Convenience parse helpers ─────────────────────────────────────────────

    /** Parse a raw JSON string into a JsonObject. */
    public JsonObject parseObject(String json) {
        return gson.fromJson(json, JsonObject.class);
    }

    /** Parse a raw JSON string into a JsonArray. */
    public JsonArray parseArray(String json) {
        return gson.fromJson(json, JsonArray.class);
    }

    /**
     * Returns true if the parsed response contains an "error" field.
     * Use this to check API call results before processing them.
     */
    public boolean isError(JsonObject response) {
        return response.has("error");
    }
}
