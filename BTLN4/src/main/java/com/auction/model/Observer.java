package com.auction.model;

public interface Observer {
    public void update(BidTransaction bidder);
}