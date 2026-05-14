package com.auction.service;

import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;
import com.auction.model.*;
import com.auction.repository.JdbcAuctionRepository;
import com.auction.repository.JdbcBidRepository;
import com.auction.repository.JdbcUserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AppFacade – single entry point that all controllers use.
 *
 * Architecture:
 *   Controllers → AppFacade → AuctionService / UserService → Repository → SQLite DB
 *
 * Controllers MUST NOT import repository classes directly.
 * This enforces strict MVC layering:
 *   View (FXML/Controllers)  ←→  Service Layer (AppFacade)  ←→  Data Layer (Repos)
 *
 * Also re-exports the individual services for callers that need them.
 */
public final class AppFacade {

    private static AppFacade instance;

    private final AuctionService auctionService;
    private final UserService    userService;

    private AppFacade() {
        this.userService    = UserService.getInstance();
        this.auctionService = AuctionService.getInstance();
    }

    public static AppFacade getInstance() {
        if (instance == null) {
            instance = new AppFacade();
        }
        return instance;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Auth
    // ──────────────────────────────────────────────────────────────────────────

    public Optional<User> login(String username, String password) {
        return userService.login(username, password);
    }

    public boolean register(String username, String password, String role) {
        return userService.register(username, password, role);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Users
    // ──────────────────────────────────────────────────────────────────────────

    public List<User> getAllUsers() { return userService.getAllUsers(); }

    public Optional<User> findUserByUsername(String username) {
        return userService.findByUsername(username);
    }

    public Optional<User> findUserById(String id) {
        return userService.findById(id);
    }

    public boolean deleteUser(String userId) { return userService.deleteUser(userId); }

    public void saveUser(User user) { userService.saveUser(user); }

    // ──────────────────────────────────────────────────────────────────────────
    // Auctions
    // ──────────────────────────────────────────────────────────────────────────

    /** All auctions (Admin view). */
    public List<Auction> getAllAuctions() { return auctionService.getAllAuctions(); }

    /** Only OPEN + RUNNING — what bidders see. */
    public List<Auction> getPublicAuctions() { return auctionService.getPublicAuctions(); }

    /** All auctions for a specific seller (including PENDING). */
    public List<Auction> getAuctionsBySeller(Seller seller) {
        return auctionService.getAuctionsBySeller(seller);
    }

    public Optional<Auction> findAuctionById(String id) {
        return auctionService.findById(id);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Auction status transitions (Admin-controlled)
    // ──────────────────────────────────────────────────────────────────────────

    public Auction createAuction(Seller seller, Item item, LocalDateTime endTime) {
        return auctionService.createAuction(seller, item, endTime);
    }

    public boolean removeAuction(String auctionId) {
        return auctionService.removeAuction(auctionId);
    }

    /** Admin: PENDING → OPEN */
    public void approveAuction(Auction auction) throws InvalidStatusException {
        auctionService.approveAuction(auction);
    }

    /** Admin: OPEN → RUNNING */
    public void startAuction(Auction auction) throws InvalidStatusException {
        auctionService.startAuction(auction);
    }

    /** Admin: RUNNING → CLOSED */
    public void finishAuction(Auction auction) throws InvalidStatusException {
        auctionService.finishAuction(auction);
    }

    /** Admin or Seller: any → CANCELED (except CLOSED) */
    public void cancelAuction(Auction auction) throws InvalidStatusException {
        auctionService.cancelAuction(auction);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bidding (Bidder only, auction must be RUNNING)
    // ──────────────────────────────────────────────────────────────────────────

    public void placeBid(Auction auction, Bidder bidder, double amount)
            throws InvalidBidException, InvalidStatusException {
        auctionService.placeBid(auction, bidder, amount);
    }
}
