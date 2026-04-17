package com.auction.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.LocalDateTime;
import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;

public class Auction extends Entity implements Subject {
    private final Seller seller;
    private final Item item;
    private AuctionStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double highestBid;
    private List<BidTransaction> bidHistory = new CopyOnWriteArrayList<>();
    private List<Observer> observers = new CopyOnWriteArrayList<>();

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

    public void notifyObserver(BidTransaction newBid) {
        for (Observer o : observers) {
            o.notify(newBid);
        }
    }

    public void startAuction() throws InvalidStatusException {
        if (this.status != AuctionStatus.OPEN) {
            throw new InvalidStatusException("Auction cannot start");
        }
        status = AuctionStatus.RUNNING;
    }

    public synchronized void placeBid(BidTransaction newBid) throws InvalidBidException, InvalidStatusException {
        if (this.status != AuctionStatus.RUNNING) {
            throw new InvalidStatusException("Auction is not running");
        }

        double currentPrice = this.highestBid;
        double newBidAmount = newBid.getAmount();

        if (newBidAmount <= currentPrice) {
            throw new InvalidBidException("Bid must be higher than current bid");
        }

        this.highestBid = newBidAmount;
        this.bidHistory.add(newBid);
        this.notifyObserver(newBid);
    }

    public void finishAuction() throws InvalidStatusException {
        if (status != AuctionStatus.RUNNING) {
            throw new InvalidStatusException("Auction cannot finish");
        }
        status = AuctionStatus.PAID;
    }

    public void markPaid() throws InvalidStatusException {
        if (status != AuctionStatus.PAID) {
            throw new InvalidStatusException("Auction not finished");
        }
        status = AuctionStatus.PAID;
    }

    public void cancelAuction() throws InvalidStatusException {
        if (status == AuctionStatus.PAID) {
            throw new InvalidStatusException("Paid auction cannot cancel");
        }
        status = AuctionStatus.CANCELED;
    }
}