package com.auction.service;

import com.auction.core.model.Auction;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class AuctionManager {

    private static volatile AuctionManager instance;
    private List<Auction> auctions;

    private AuctionManager() {
        auctions = new CopyOnWriteArrayList<>();
    }

    public static AuctionManager getInstance() {
        if (instance == null) {
            synchronized (AuctionManager.class) {
                if (instance == null) {
                    instance = new AuctionManager();
                }
            }
        }
        return instance;
    }

    public void createAuction(Auction auction) {
        auctions.add(auction);
    }

    public List<Auction> getAuctions() {
        return auctions;
    }

    public Auction findAuctionById(String id) {
        for (Auction a : auctions) {
            if (a.getId().equals(id)) {
                return a;
            }
        }
        return null;
    }

    public void removeAuction(String id) {
        auctions.removeIf(a -> a.getId().equals(id));
    }
}