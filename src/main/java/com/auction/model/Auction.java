package com.auction.model;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;

public class Auction extends Entity {
	private final Seller seller;
    private final Item item;
    private AuctionStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double highestBid;
    private List<BidTransaction> bidHistory = new ArrayList<>(); 

    public Auction(Seller seller, Item item, LocalDateTime endTime) {
    	super();
    	this.seller = seller;
    	this.item = item;
    	this.highestBid = 0.0;
    	this.endTime = endTime;
    }

    public void startAuction() {
    	this.status = AuctionStatus.OPEN;
    }

    public void endAuction() {
    	this.status = AuctionStatus.CLOSE;
    }

    public synchronized boolean placeBid(BidTransaction newBid) {
        if (this.status != AuctionStatus.RUNNING) {
            return false;
        }

        double currentPrice = this.highestBid;
        double newBidAmount = newBid.getAmount();

        if (newBidAmount > currentPrice) {
            this.highestBid = newBidAmount;
            this.bidHistory.add(newBid);
            return true;
        } else {
            return false;
        }
    }
}
