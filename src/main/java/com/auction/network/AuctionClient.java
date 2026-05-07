package com.auction.network;

import com.auction.model.BidTransaction;
import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AuctionClient {
    private final Gson gson = new Gson();
    private final HttpClient client = HttpClient.newHttpClient();

    public void sendBid(BidTransaction tx) throws Exception {
        String json = gson.toJson(tx);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7000/bid"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}