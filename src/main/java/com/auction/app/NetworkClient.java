package com.auction.app;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class NetworkClient {
    private String serverUrl;
    private final HttpClient httpClient;

    public NetworkClient() {
        this.httpClient = HttpClient.newHttpClient();
    }

    public void connect(String host, int port) {
        // We just save the URL. HTTP ignores the port parameter since the URL handles it!
        this.serverUrl = host;
    }

    public JsonObject sendRequest(JsonObject request) throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl))
                .header("Content-Type", "application/json")
                .header("ngrok-skip-browser-warning", "true")
                .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        //Debug:
        System.out.println("STATUS CODE: " + response.statusCode());
        System.out.println("RAW RESPONSE: " + response.body());

        if (response.body() != null && !response.body().isEmpty()) {
            return JsonParser.parseString(response.body()).getAsJsonObject();
        }
        return null;
    }

    public void disconnect() {
        // No persistent socket to close in HTTP!
    }
}