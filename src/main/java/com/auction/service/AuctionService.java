package com.auction.service;

import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;
import com.auction.model.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AuctionService - manages auctions and bids.
 * Singleton. Currently uses in-memory mock data.
 */
public class AuctionService {

    private static AuctionService instance;
    private final List<Auction> auctions = new ArrayList<>();

    private AuctionService() {
        seedData();
    }

    public static AuctionService getInstance() {
        if (instance == null) {
            instance = new AuctionService();
        }
        return instance;
    }

    // ------- Read -------

    public List<Auction> getAllAuctions() {
        return new ArrayList<>(auctions);
    }

    public List<Auction> getAuctionsBySeller(Seller seller) {
        return auctions.stream()
                .filter(a -> a.getSeller().getId().equals(seller.getId()))
                .collect(java.util.stream.Collectors.toList());
    }

    public Optional<Auction> findById(String id) {
        return auctions.stream().filter(a -> a.getId().equals(id)).findFirst();
    }

    // ------- Create / Delete -------

    public Auction createAuction(Seller seller, Item item, LocalDateTime endTime) {
        Auction auction = new Auction(seller, item, endTime);
        auctions.add(auction);
        return auction;
    }

    public boolean removeAuction(String auctionId) {
        return auctions.removeIf(a -> a.getId().equals(auctionId));
    }

    // ------- Bidding -------

    public void placeBid(Auction auction, Bidder bidder, double amount)
            throws InvalidBidException, InvalidStatusException {
        BidTransaction bid = new BidTransaction(
                java.util.UUID.randomUUID().toString(),
                LocalDateTime.now(),
                bidder,
                auction,
                amount
        );
        auction.placeBid(bid);
    }

    public void startAuction(Auction auction) throws InvalidStatusException {
        auction.startAuction();
    }

    public void finishAuction(Auction auction) throws InvalidStatusException {
        auction.finishAuction();
    }

    public void cancelAuction(Auction auction) throws InvalidStatusException {
        auction.cancelAuction();
    }

    // ------- Seed mock data -------

    private void seedData() {
        UserService userService = UserService.getInstance();

        Seller carol = (Seller) userService.findByUsername("carol").orElse(null);
        Seller dave  = (Seller) userService.findByUsername("dave").orElse(null);

        if (carol == null || dave == null) return;

        Electronics laptop = new Electronics("Laptop Dell XPS 15", "Laptop cao cấp, i9, 32GB RAM", 15000000, carol);
        Electronics phone  = new Electronics("iPhone 15 Pro Max", "Mới 100%, chưa kích hoạt", 28000000, carol);
        Art painting = new Art("Tranh sơn dầu phong cảnh", "Phong cảnh Việt Nam, 80x60cm", 5000000, dave);
        Vehicle car = new Vehicle("Toyota Camry 2022", "Xe đẹp, ít đi, bảo hành hãng", 800000000, dave);

        Auction a1 = new Auction(carol, laptop, LocalDateTime.now().plusDays(2));
        Auction a2 = new Auction(carol, phone,  LocalDateTime.now().plusHours(5));
        Auction a3 = new Auction(dave, painting, LocalDateTime.now().plusDays(7));
        Auction a4 = new Auction(dave, car, LocalDateTime.now().plusDays(1));

        try {
            a1.startAuction();
            a2.startAuction();
        } catch (InvalidStatusException e) {
            // ignore seed errors
        }

        auctions.add(a1);
        auctions.add(a2);
        auctions.add(a3);
        auctions.add(a4);
    }
}
