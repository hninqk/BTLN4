package com.auction.core.model;

public interface Subject {
    void addObserver(Observer observer);
    void removeObserver(Observer observer);
    void notifyObserver(BidTransaction newBid);
}