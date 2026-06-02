package com.auction.core.model;

import java.time.LocalDateTime;

public class BidTransaction extends Entity {
    private Bidder bidder;
    private Auction auction;
    private double amount;
    private LocalDateTime timestamp;

    public BidTransaction(String id, LocalDateTime createdAt, Bidder bidder, Auction auction, double amount) {
        super(id, createdAt);
        this.bidder = bidder;
        this.auction = auction;
        this.amount = amount;
        // Use createdAt as the canonical timestamp so restored bids (from DB/JSON) keep their original time
        this.timestamp = (createdAt != null) ? createdAt : com.auction.core.util.TimeSyncManager.getNow();
    }

    public Bidder getBidder() {
        return bidder;
    }

    public Auction getAuction() {
        return auction;
    }

    public double getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
      return timestamp;
    }
}
