package com.auction.model;

public enum AuctionStatus {
    PENDING,    // Seller submitted; waiting for Admin approval
    OPEN,       // Admin approved; auction is visible and waiting to start (Admin starts it)
    RUNNING,    // Admin started bidding — bidders can place bids
    CLOSED,     // Auction ended (winner decided)
    CANCELED    // Auction was cancelled
}
