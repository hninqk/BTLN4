package com.auction.service;

import com.auction.model.Auction;
import java.util.ArrayList;
import java.util.List;

public class AuctionManager {

    private static AuctionManager instance;
    private List<Auction> auctions;

    private AuctionManager() {
        auctions = new ArrayList<>();
    }

    public static AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance;
    }

    public void addAuction(Auction auction) {
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