package com.auction.api.http;

import com.auction.api.config.AppConfig;
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

public class ApiClient {

    private static ApiClient instance;

    private final HttpClient httpClient;

    private final Gson       gson = new Gson();

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

    public String getBaseUrl() {
        cachedBaseUrl = AppConfig.httpBaseUrl();
        return cachedBaseUrl;
    }

    public CompletableFuture<String> getAsync(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    public CompletableFuture<String> postAsync(String path, JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        body.toString(), StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    public CompletableFuture<String> deleteAsync(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .DELETE()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    public CompletableFuture<String> putAsync(String path, JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(
                        body.toString(), StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    public String getSync(String path) {
        try {
            return getAsync(path).get();
        } catch (Exception e) {
            System.err.println("[ApiClient] GET " + path + " failed: " + e.getMessage());
            throw new RuntimeException("GET " + path + " failed: " + e.getMessage(), e);
        }
    }

    public String postSync(String path, JsonObject body) {
        try {
            return postAsync(path, body).get();
        } catch (Exception e) {
            System.err.println("[ApiClient] POST " + path + " failed: " + e.getMessage());
            throw new RuntimeException("POST " + path + " failed: " + e.getMessage(), e);
        }
    }

    public String deleteSync(String path) {
        try {
            return deleteAsync(path).get();
        } catch (Exception e) {
            System.err.println("[ApiClient] DELETE " + path + " failed: " + e.getMessage());
            throw new RuntimeException("DELETE " + path + " failed: " + e.getMessage(), e);
        }
    }

    public String putSync(String path, JsonObject body) {
        try {
            return putAsync(path, body).get();
        } catch (Exception e) {
            System.err.println("[ApiClient] PUT " + path + " failed: " + e.getMessage());
            throw new RuntimeException("PUT " + path + " failed: " + e.getMessage(), e);
        }
    }

    public JsonObject parseObject(String json) {
        return gson.fromJson(json, JsonObject.class);
    }

    public JsonArray parseArray(String json) {
        return gson.fromJson(json, JsonArray.class);
    }

    public boolean isError(JsonObject response) {
        return response.has("error");
    }
}
