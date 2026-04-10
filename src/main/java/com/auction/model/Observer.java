package com.auction.model;

public interface Observer {
    public void notify(BidTransaction biddder);
}