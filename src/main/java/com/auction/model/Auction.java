package com.auction.model;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;

public class Auction extends Entity implements Subject {
    private final Seller seller;
    private final Item item;
    private AuctionStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double highestBid;
    private List<BidTransaction> bidHistory = new ArrayList<>();
    private List<Observer> observers = new ArrayList<>();

    public Auction(Seller seller, Item item, LocalDateTime endTime) {
        super();
        this.status = AuctionStatus.OPEN;
        this.seller = seller;
        this.item = item;
        this.highestBid = 0.0;
        this.endTime = endTime;
    }

    public void addObserver(Observer observer) {
        observers.add(observer);
    }

    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    private void notifyObserver(BidTransaction newBid) {
        for (Observer o : observers) {
            o.notify(newBid);
        }
    }

    public void startAuction() {
        if (this.status != AuctionStatus.OPEN) {
            throw new IllgealStateException("Auction cannot start");
        }
        status = AuctionStatus.RUNNING;
    }

    public synchronized void placeBid(BidTransaction newBid) {
        if (this.status != AuctionStatus.RUNNING) {
            throw new IllegalStateException("Auction is not running");
        }

        double currentPrice = this.highestBid;
        double newBidAmount = newBid.getAmount();

        if (newBidAmount <= currentPrice) {
            throw new IllegalArgumentException("Bid must be higher than current bid");
        }

        this.highestBid = newBidAmount;
        this.bidHistory.add(newBid);
        this.notifyObserver(newBid);
    }

    public void finishAuction() {
        if (status != AuctionStatus.RUNNING) {
            throw new IllegalStateException("Auction cannot finish");
        }
        status = AuctionStatus.PAID;
    }

    public void markPaid() {
        if (status != AuctionStatus.PAID) {
            throw new IllegalStateException("Auction not finished");
        }
        status = AuctionStatus.PAID;
    }

    public void cancelAuction() {
        if (status == AuctionStatus.PAID) {
            throw new IllegalStateException("Paid auction cannot cancel");
        }
        status = AuctionStatus.CANCELED;
    }
}