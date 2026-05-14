package com.auction.model;

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
        this.timestamp = LocalDateTime.now();
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
