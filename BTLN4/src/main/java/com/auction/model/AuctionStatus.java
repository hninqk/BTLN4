package com.auction.model;

public enum AuctionStatus {
    /** @deprecated No longer used — auctions are created directly as OPEN. Kept for DB back-compat. */
    @Deprecated
    PENDING,
    OPEN,       // Seller submitted; visible and waiting for Admin to start
    RUNNING,    // Admin started bidding — bidders can place bids
    CLOSED,     // Auction ended (winner decided)
    CANCELED    // Auction was cancelled
}
