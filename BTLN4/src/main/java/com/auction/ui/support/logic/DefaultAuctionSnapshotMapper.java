package com.auction.ui.support.logic;

import com.auction.ui.support.dto.AuctionSnapshot;
import com.auction.core.model.Art;
import com.auction.core.model.Auction;
import com.auction.core.model.AuctionStatus;
import com.auction.core.model.BidTransaction;
import com.auction.core.model.Bidder;
import com.auction.core.model.Electronics;
import com.auction.core.model.Item;
import com.auction.core.model.Seller;
import com.auction.core.model.Vehicle;
import com.auction.core.util.TimeSyncManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.util.Optional;

public final class DefaultAuctionSnapshotMapper implements AuctionSnapshotMapper {
    @Override
    public Optional<AuctionSnapshot> fromServerSnapshot(JsonObject json) {
        try {
            String sellerUsername = json.get("sellerUsername").getAsString();
            String sellerId = json.get("sellerId").getAsString();
            LocalDateTime createdAt = parseDateTime(json, "auctionCreatedAt", TimeSyncManager.getNow());
            Seller seller = new Seller(sellerId, createdAt, sellerUsername, "", sellerUsername + "_Shop");
            Auction auction = buildAuction(json, seller, createdAt);
            int bidCount = json.has("bidCount") ? json.get("bidCount").getAsInt() : auction.getBidHistory().size();
            return Optional.of(new AuctionSnapshot(auction, bidCount));
        } catch (Exception e) {
            System.err.println("[AuctionSnapshotMapper] fromServerSnapshot error: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<Auction> fromSellerSnapshot(JsonObject json, Seller seller) {
        try {
            LocalDateTime createdAt = parseDateTime(json, "auctionCreatedAt", TimeSyncManager.getNow());
            return Optional.of(buildAuction(json, seller, createdAt));
        } catch (Exception e) {
            System.err.println("[AuctionSnapshotMapper] fromSellerSnapshot error: " + e.getMessage());
            return Optional.empty();
        }
    }

    private Auction buildAuction(JsonObject json, Seller seller, LocalDateTime createdAt) {
        String auctionId = json.get("auctionId").getAsString();
        String itemName = json.get("itemName").getAsString();
        double highestBid = json.get("highestBid").getAsDouble();
        AuctionStatus status = AuctionStatus.valueOf(json.get("status").getAsString());
        LocalDateTime endTime = LocalDateTime.parse(json.get("endTime").getAsString());
        LocalDateTime startTime = parseOptionalDateTime(json, "startTime");
        double startPrice = json.has("startPrice") ? json.get("startPrice").getAsDouble() : highestBid;
        String description = firstString(json, "itemDesc", "description", "");
        String imageUrl = firstString(json, "itemImageUrl", "imageUrl", "");
        String category = firstString(json, "itemCategory", "category", "Điện tử");

        Item item = itemFor(category, itemName, description, startPrice, seller);
        item.setImageUrl(imageUrl);

        Auction auction = new Auction(auctionId, createdAt, seller, item, status, highestBid, startTime, endTime);
        injectBidHistory(json, auction);
        return auction;
    }

    private Item itemFor(String category, String name, String description, double startPrice, Seller seller) {
        return switch (category) {
            case "Nghệ thuật" -> new Art(name, description, startPrice, seller);
            case "Xe cộ" -> new Vehicle(name, description, startPrice, seller);
            default -> new Electronics(name, description, startPrice, seller);
        };
    }

    private void injectBidHistory(JsonObject json, Auction auction) {
        if (!json.has("bidHistory")) {
            return;
        }
        JsonArray bids = json.get("bidHistory").getAsJsonArray();
        for (int i = 0; i < bids.size(); i++) {
            JsonObject bidJson = bids.get(i).getAsJsonObject();
            String bidId = bidJson.get("bidId").getAsString();
            double amount = bidJson.get("amount").getAsDouble();
            String bidderName = bidJson.get("bidderUsername").getAsString();
            String bidderId = bidJson.get("bidderId").getAsString();
            LocalDateTime time = LocalDateTime.parse(bidJson.get("time").getAsString());
            Bidder bidder = new Bidder(bidderId, time, bidderName, "", 0);
            auction.injectBid(new BidTransaction(bidId, time, bidder, auction, amount));
        }
    }

    private LocalDateTime parseDateTime(JsonObject json, String property, LocalDateTime fallback) {
        if (!json.has(property) || json.get(property).getAsString().isBlank()) {
            return fallback;
        }
        return LocalDateTime.parse(json.get(property).getAsString());
    }

    private LocalDateTime parseOptionalDateTime(JsonObject json, String property) {
        if (!json.has(property) || json.get(property).getAsString().isBlank()) {
            return null;
        }
        return LocalDateTime.parse(json.get(property).getAsString());
    }

    private String firstString(JsonObject json, String primary, String secondary, String fallback) {
        if (json.has(primary)) {
            return json.get(primary).getAsString();
        }
        if (json.has(secondary)) {
            return json.get(secondary).getAsString();
        }
        return fallback;
    }
}
