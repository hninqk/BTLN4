package com.auction.model;

public enum AuctionStatus {
    /** @deprecated Legacy approval state kept for DB back-compat. */
    @Deprecated
    PENDING,
    UPCOMING,   // Seller scheduled; automatically goes live at startTime
    OPEN,       // Legacy scheduled state kept for DB back-compat
    RUNNING,    // Auction is live — bidders can place bids
    CLOSED,     // Auction ended (winner decided)
    CANCELED    // Auction was cancelled
}
