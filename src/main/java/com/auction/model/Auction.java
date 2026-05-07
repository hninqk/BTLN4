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
        this.highestBid = (item != null) ? item.getStartingPrice() : 0.0;
        this.startTime = LocalDateTime.now();
        this.endTime = endTime;
    }

    // ------- Observer pattern -------

    @Override
    public void addObserver(Observer observer) { observers.add(observer); }

    @Override
    public void removeObserver(Observer observer) { observers.remove(observer); }

    @Override
    public void notifyObserver(BidTransaction newBid) {
        for (Observer o : observers) o.notify(newBid);
    }

    // ------- State transitions -------

    public void startAuction() throws InvalidStatusException {
        if (this.status != AuctionStatus.OPEN)
            throw new InvalidStatusException("Auction cannot start from status: " + status);
        status = AuctionStatus.RUNNING;
    }

    public synchronized void placeBid(BidTransaction newBid) throws InvalidBidException, InvalidStatusException {
        if (this.status != AuctionStatus.RUNNING)
            throw new InvalidStatusException("Auction is not running");
        double newBidAmount = newBid.getAmount();
        if (newBidAmount <= this.highestBid)
            throw new InvalidBidException("Bid must be higher than current bid of " + this.highestBid);
        this.highestBid = newBidAmount;
        this.bidHistory.add(newBid);
        this.notifyObserver(newBid);
    }

    public void finishAuction() throws InvalidStatusException {
        if (status != AuctionStatus.RUNNING)
            throw new InvalidStatusException("Auction cannot finish from status: " + status);
        status = AuctionStatus.PAID;
    }

    public void cancelAuction() throws InvalidStatusException {
        if (status == AuctionStatus.PAID)
            throw new InvalidStatusException("Paid auction cannot be cancelled");
        status = AuctionStatus.CANCELED;
    }

    // ------- Getters -------

    public Seller getSeller()           { return seller; }
    public Item getItem()               { return item; }
    public AuctionStatus getStatus()    { return status; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime()   { return endTime; }
    public double getHighestBid()       { return highestBid; }
    public List<BidTransaction> getBidHistory() { return new java.util.ArrayList<>(bidHistory); }

    public BidTransaction getWinner() {
        if (bidHistory.isEmpty()) return null;
        return bidHistory.get(bidHistory.size() - 1);
    }

    public String getStatusDisplay() {
        return switch (status) {
            case OPEN     -> "Chờ bắt đầu";
            case RUNNING  -> "Đang diễn ra";
            case CLOSE    -> "Đã đóng";
            case PAID     -> "Hoàn thành";
            case CANCELED -> "Đã huỷ";
        };
    }
}