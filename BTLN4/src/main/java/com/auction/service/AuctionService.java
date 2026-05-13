package com.auction.service;

import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;
import com.auction.model.*;
import com.auction.repository.JdbcAuctionRepository;
import com.auction.repository.JdbcBidRepository;
import com.auction.repository.JdbcUserRepository;
import com.auction.util.SessionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AuctionService – manages the full auction lifecycle.
 *
 * Status flow:
 *   Seller creates → PENDING
 *   Admin approves → OPEN   (visible to bidders)
 *   Admin starts   → RUNNING (bids accepted)
 *   Admin finishes → CLOSED  (winner charged)
 *   Admin/Seller   → CANCELED
 *
 * Balance rules:
 *   • placeBid()    – balance is CHECKED (must have enough to win) but NOT deducted.
 *   • finishAuction() – winner's balance is deducted and saved.
 *   • cancelAuction() – no money movement.
 */
public class AuctionService {

    private static AuctionService instance;
    private final JdbcAuctionRepository auctionRepo = new JdbcAuctionRepository();
    private final JdbcBidRepository     bidRepo     = new JdbcBidRepository();
    private final JdbcUserRepository    userRepo    = new JdbcUserRepository();

    private AuctionService() {
        ensureSeeded();
    }

    public static AuctionService getInstance() {
        if (instance == null) {
            instance = new AuctionService();
        }
        return instance;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<Auction> getAllAuctions() {
        return auctionRepo.findAll();
    }

    /** Only OPEN + RUNNING — visible to public bidders. */
    public List<Auction> getPublicAuctions() {
        return auctionRepo.findAll().stream()
                .filter(a -> a.getStatus() == AuctionStatus.OPEN
                          || a.getStatus() == AuctionStatus.RUNNING)
                .toList();
    }

    /** All auctions for a seller, including PENDING. */
    public List<Auction> getAuctionsBySeller(Seller seller) {
        return auctionRepo.findBySellerId(seller.getId());
    }

    public Optional<Auction> findById(String id) {
        return auctionRepo.findById(id);
    }

    // ── Create / Delete ───────────────────────────────────────────────────────

    /** Seller submits → saved as PENDING, awaiting Admin approval. */
    public Auction createAuction(Seller seller, Item item, LocalDateTime endTime) {
        Auction auction = new Auction(seller, item, endTime);
        auctionRepo.save(auction);
        return auction;
    }

    public boolean removeAuction(String auctionId) {
        return auctionRepo.deleteById(auctionId);
    }

    // ── Status transitions ────────────────────────────────────────────────────

    /** Admin: PENDING → OPEN */
    public void approveAuction(Auction auction) throws InvalidStatusException {
        auction.approveAuction();
        auctionRepo.updateStatus(auction.getId(), auction.getStatus(),
                auction.getHighestBid(), auction.getStartTime());
    }

    /**
     * Admin: OPEN → RUNNING.
     * Sets startTime = now() in the model, then persists it to DB
     * so re-fetches always show the correct start time instead of null.
     */
    public void startAuction(Auction auction) throws InvalidStatusException {
        auction.startAuction();   // model sets startTime = LocalDateTime.now()
        auctionRepo.updateStatus(auction.getId(), auction.getStatus(),
                auction.getHighestBid(), auction.getStartTime());
    }

    /**
     * Admin: RUNNING → CLOSED.
     * After closing, the winner's balance is deducted and persisted.
     * This is the ONLY place money changes hands — not on each individual bid.
     */
    public void finishAuction(Auction auction) throws InvalidStatusException {
        auction.finishAuction();
        auctionRepo.updateStatus(auction.getId(), auction.getStatus(),
                auction.getHighestBid(), auction.getStartTime());

        // Charge the winner
        BidTransaction winner = auction.getWinner();
        if (winner != null) {
            Bidder winnerBidder = winner.getBidder();
            boolean charged = winnerBidder.deductBalance(winner.getAmount());
            if (charged) {
                userRepo.update(winnerBidder);
                // Refresh session if the winner is currently logged in
                var sessionUser = SessionManager.getInstance().getCurrentUser();
                if (sessionUser != null && sessionUser.getId().equals(winnerBidder.getId())) {
                    SessionManager.getInstance().setCurrentUser(winnerBidder);
                }
                System.out.printf("[AuctionService] Winner %s charged %,.0f ₫%n",
                        winnerBidder.getUsername(), winner.getAmount());
            } else {
                System.err.printf("[AuctionService] WARNING: winner %s has insufficient balance%n",
                        winnerBidder.getUsername());
            }
        }
    }

    /** Admin or Seller: cancel any non-closed auction. No money movement. */
    public void cancelAuction(Auction auction) throws InvalidStatusException {
        auction.cancelAuction();
        auctionRepo.updateStatus(auction.getId(), auction.getStatus(),
                auction.getHighestBid(), auction.getStartTime());
    }

    // ── Bidding ───────────────────────────────────────────────────────────────

    /**
     * Bidder places a bid.
     *
     * Rules:
     *   1. Auction must be RUNNING.
     *   2. Amount must be > current highest bid.
     *   3. Bidder must have enough balance to cover the bid IF they win.
     *      (Balance is NOT deducted here — only charged at finishAuction.)
     */
    public void placeBid(Auction auction, Bidder bidder, double amount)
            throws InvalidBidException, InvalidStatusException {

        // Solvency check — must be able to afford the bid if they end up winning
        if (bidder.getAccountBalance() < amount) {
            throw new InvalidBidException(
                    String.format("Số dư không đủ. Số dư: %,.0f ₫, Giá đặt: %,.0f ₫",
                            bidder.getAccountBalance(), amount));
        }

        BidTransaction bid = new BidTransaction(
                java.util.UUID.randomUUID().toString(),
                LocalDateTime.now(),
                bidder,
                auction,
                amount);

        // Throws InvalidBidException if amount ≤ highest, InvalidStatusException if not RUNNING
        auction.placeBid(bid);

        // Persist bid + updated highest price. Balance is NOT touched.
        bidRepo.save(bid);
        auctionRepo.updateStatus(auction.getId(), auction.getStatus(),
                auction.getHighestBid(), auction.getStartTime());
    }

    // ── Seed ──────────────────────────────────────────────────────────────────

    private void ensureSeeded() {
        if (!auctionRepo.findAll().isEmpty()) return;

        UserService userService = UserService.getInstance();
        Seller carol = (Seller) userService.findByUsername("carol").orElse(null);
        Seller dave  = (Seller) userService.findByUsername("dave").orElse(null);
        if (carol == null || dave == null) return;

        Electronics laptop   = new Electronics("Laptop Dell XPS 15", "Laptop cao cấp, i9, 32GB RAM", 15_000_000, carol);
        Electronics phone    = new Electronics("iPhone 15 Pro Max", "Mới 100%, chưa kích hoạt", 28_000_000, carol);
        Art         painting = new Art("Tranh sơn dầu phong cảnh", "Phong cảnh Việt Nam, 80x60cm", 5_000_000, dave);
        Vehicle     car      = new Vehicle("Toyota Camry 2022", "Xe đẹp, ít đi, bảo hành hãng", 800_000_000, dave);

        Auction a1 = createAuction(carol, laptop,   LocalDateTime.now().plusDays(2));
        Auction a2 = createAuction(carol, phone,    LocalDateTime.now().plusHours(5));
        Auction a3 = createAuction(dave,  painting, LocalDateTime.now().plusDays(7));
        Auction a4 = createAuction(dave,  car,      LocalDateTime.now().plusDays(1));

        try {
            approveAuction(a1); approveAuction(a2);
            approveAuction(a3); approveAuction(a4);
            startAuction(a1);   startAuction(a2);
        } catch (InvalidStatusException ignored) {}

        System.out.println("[AuctionService] Seed data inserted.");
    }
}
