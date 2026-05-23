package com.auction.service;

import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;
import com.auction.model.*;
import com.auction.repository.JdbcAuctionRepository;
import com.auction.repository.JdbcBidRepository;
import com.auction.repository.JdbcUserRepository;
import com.auction.repository.JdbcAutoBidRepository;
import com.auction.util.SessionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Map;

/**
 * AuctionService – quản lý toàn bộ vòng đời phiên đấu giá.
 *
 * Status flow:
 * Seller tạo → PENDING
 * Admin duyệt → OPEN (hiển thị với bidder)
 * Admin bắt đầu → RUNNING (nhận bid)
 * Admin kết thúc → CLOSED (trừ tiền winner)
 * Admin/Seller → CANCELED
 *
 * Cơ chế Fund Freezing:
 * • placeBid() – kiểm tra availableBalance, đóng băng tiền bidder mới,
 * hoàn trả tiền đóng băng của old highest bidder.
 * • finishAuction() – trừ totalBalance winner (frozenBalance đã trừ sẵn khi
 * bid).
 * • cancelAuction() – hoàn trả tiền đóng băng cho highest bidder hiện tại.
 *
 * Thread-safety:
 * • userLocks: ConcurrentHashMap<bidderId, ReentrantLock> – mỗi user có 1 lock
 * riêng.
 * Đảm bảo khi user bid ở 2 phiên cùng lúc, các thao tác freeze/unfreeze
 * trên balance của họ sẽ tuần tự, không bị race condition.
 * • Auction.placeBid() đã được synchronized ở model layer.
 */
public class AuctionService {

    private static AuctionService instance;
    private final JdbcAuctionRepository auctionRepo = new JdbcAuctionRepository();
    private final JdbcBidRepository bidRepo = new JdbcBidRepository();
    private final JdbcUserRepository userRepo = new JdbcUserRepository();
    private final JdbcAutoBidRepository autoBidRepo = new JdbcAutoBidRepository();

    // In-memory cache for fast lookups
    private final Map<String, Auction> auctionCache = new ConcurrentHashMap<>();

    /**
     * Lock per-user để tránh race condition khi cùng 1 user bid ở nhiều phiên đồng
     * thời.
     * computeIfAbsent đảm bảo mỗi bidderId chỉ có đúng 1 lock.
     */
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    private final BiddingService biddingService;

    private AuctionService() {
        this.biddingService = new BiddingService(
                auctionRepo, bidRepo, userRepo, autoBidRepo, auctionCache, userLocks);
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

    /**
     * OPEN + RUNNING + CLOSED — visible to public bidders (CLOSED so users can see
     * finished results).
     */
    public List<Auction> getPublicAuctions() {
        return auctionRepo.findAll().stream()
                .filter(a -> a.getStatus() == AuctionStatus.OPEN
                        || a.getStatus() == AuctionStatus.RUNNING
                        || a.getStatus() == AuctionStatus.CLOSED)
                .toList();
    }

    /** All auctions for a seller, including PENDING. */
    public List<Auction> getAuctionsBySeller(Seller seller) {
        return auctionRepo.findBySellerId(seller.getId());
    }

    public Optional<Auction> findById(String id) {
        if (auctionCache.containsKey(id)) {
            return Optional.of(auctionCache.get(id));
        }
        Optional<Auction> auctionOpt = auctionRepo.findById(id);
        auctionOpt.ifPresent(a -> auctionCache.put(id, a));
        return auctionOpt;
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
        auctionCache.put(auction.getId(), auction);
        auctionRepo.updateStatus(auction.getId(), auction.getStatus(),
                auction.getHighestBid(), auction.getStartTime());
    }

    /**
     * Admin: OPEN → RUNNING.
     * Sets startTime = now() in the model, then persists it to DB.
     */
    public void startAuction(Auction auction) throws InvalidStatusException {
        auction.startAuction(); // model sets startTime = com.auction.util.TimeSyncManager.getNow()
        auctionCache.put(auction.getId(), auction);
        auctionRepo.updateStatus(auction.getId(), auction.getStatus(),
                auction.getHighestBid(), auction.getStartTime());
    }

    /**
     * Admin: RUNNING → CLOSED.
     *
     * Sau khi đóng:
     * - Winner: totalBalance -= winAmount, frozenBalance -= winAmount
     * (frozenBalance đã được cộng vào lúc bid, giờ trừ đi khi thanh toán thật).
     * - Các bidder khác: tiền đã được unfreeze từng bước khi bị outbid → không cần
     * xử lý thêm.
     */
    public void finishAuction(Auction auction) throws InvalidStatusException {
        auction.finishAuction();
        auctionCache.put(auction.getId(), auction);
        auctionRepo.updateStatus(auction.getId(), auction.getStatus(),
                auction.getHighestBid(), auction.getStartTime());

        BidTransaction winner = auction.getWinner();
        
        // --- AutoBid Cleanup ---
        List<AutoBid> abs = autoBidRepo.findByAuctionId(auction.getId());
        for (AutoBid ab : abs) {
            autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), ab.getBidderId());
            ReentrantLock lock = getUserLock(ab.getBidderId());
            lock.lock();
            try {
                Bidder b = (Bidder) userRepo.findById(ab.getBidderId()).filter(u -> u instanceof Bidder).orElse(null);
                if (b != null) {
                    double toUnfreeze = ab.getMaxBid();
                    if (winner != null && winner.getBidder().getId().equals(b.getId())) {
                        toUnfreeze = Math.max(0, ab.getMaxBid() - winner.getAmount());
                    }
                    if (toUnfreeze > 0) {
                        b.unfreezeFunds(toUnfreeze);
                        userRepo.updateFrozenBalance(b.getId(), b.getFrozenBalance());
                    }
                }
            } finally {
                lock.unlock();
            }
        }
        // -----------------------

        if (winner != null) {
            String winnerId = winner.getBidder().getId();
            ReentrantLock lock = getUserLock(winnerId);
            lock.lock();
            try {
                // Load fresh bidder từ DB — in-memory object có thể stale
                Bidder winnerBidder = (Bidder) userRepo.findById(winnerId)
                        .filter(u -> u instanceof Bidder)
                        .orElse(winner.getBidder());

                boolean charged = winnerBidder.deductBalance(winner.getAmount());
                if (charged) {
                    userRepo.update(winnerBidder);
                    // Refresh session nếu winner đang login trên máy này
                    var sessionUser = SessionManager.getInstance().getCurrentUser();
                    if (sessionUser != null && sessionUser.getId().equals(winnerBidder.getId())) {
                        SessionManager.getInstance().setCurrentUser(winnerBidder);
                    }
                    System.out.printf("[AuctionService] Winner %s charged %,.0f ₫ | " +
                            "totalBalance: %,.0f ₫ | frozenBalance: %,.0f ₫ | available: %,.0f ₫%n",
                            winnerBidder.getUsername(), winner.getAmount(),
                            winnerBidder.getAccountBalance(),
                            winnerBidder.getFrozenBalance(),
                            winnerBidder.getAvailableBalance());
                } else {
                    System.err.printf("[AuctionService] WARNING: winner %s insufficient balance " +
                            "(total=%.0f, frozen=%.0f, need=%.0f)%n",
                            winnerBidder.getUsername(),
                            winnerBidder.getAccountBalance(),
                            winnerBidder.getFrozenBalance(),
                            winner.getAmount());
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Admin hoặc Seller: huỷ phiên đấu giá bất kỳ (trừ CLOSED).
     *
     * Nếu đang có highest bidder, hoàn trả tiền đóng băng cho họ.
     */
    public void cancelAuction(Auction auction) throws InvalidStatusException {
        // --- AutoBid Cleanup ---
        List<AutoBid> abs = autoBidRepo.findByAuctionId(auction.getId());
        for (AutoBid ab : abs) {
            autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), ab.getBidderId());
            ReentrantLock lock = getUserLock(ab.getBidderId());
            lock.lock();
            try {
                Bidder b = (Bidder) userRepo.findById(ab.getBidderId()).filter(u -> u instanceof Bidder).orElse(null);
                if (b != null) {
                    b.unfreezeFunds(ab.getMaxBid());
                    userRepo.updateFrozenBalance(b.getId(), b.getFrozenBalance());
                }
            } finally {
                lock.unlock();
            }
        }
        // -----------------------

        // Lưu thông tin highest bidder trước khi cancel
        BidTransaction currentHighest = auction.getWinner();

        auction.cancelAuction();
        auctionCache.put(auction.getId(), auction);
        auctionRepo.updateStatus(auction.getId(), auction.getStatus(),
                auction.getHighestBid(), auction.getStartTime());

        // Hoàn trả tiền đóng băng cho highest bidder hiện tại (nếu có)
        if (currentHighest != null) {
            String highestBidderId = currentHighest.getBidder().getId();
            double frozenAmount = currentHighest.getAmount();

            ReentrantLock lock = getUserLock(highestBidderId);
            lock.lock();
            try {
                Bidder bidder = (Bidder) userRepo.findById(highestBidderId)
                        .filter(u -> u instanceof Bidder)
                        .orElse(null);
                if (bidder != null) {
                    bidder.unfreezeFunds(frozenAmount);
                    userRepo.update(bidder);
                    System.out.printf("[AuctionService] CANCEL unfreeze bidder=%s amount=%,.0f ₫ | " +
                            "available now: %,.0f ₫%n",
                            bidder.getUsername(), frozenAmount, bidder.getAvailableBalance());
                }
            } finally {
                lock.unlock();
            }
        }
    }

    public BidTransaction placeBid(Auction auction, Bidder bidder, double amount)
            throws InvalidBidException, InvalidStatusException {
        return biddingService.placeBid(auction, bidder, amount);
    }

    public Bidder processOutbidUnfreeze() {
        return biddingService.processOutbidUnfreeze();
    }

    // ── Auto-Bidding ──────────────────────────────────────────────────────────

    public static class AutoBidResult {
        public List<BidTransaction> newBids = new java.util.ArrayList<>();
        public List<Bidder> unfrozenBidders = new java.util.ArrayList<>();
        public List<String> virtualLogs = new java.util.ArrayList<>();
        public List<String> deactivatedBidderIds = new java.util.ArrayList<>();
    }

    public AutoBidResult registerAutoBid(Auction auction, Bidder bidder, double maxBid, double increment) throws InvalidBidException {
        return biddingService.registerAutoBid(auction, bidder, maxBid, increment);
    }

    public AutoBidResult resolveBiddingWar(Auction auction) {
        return biddingService.resolveBiddingWar(auction);
    }

    public AutoBid findAutoBid(String auctionId, String bidderId) {
        return autoBidRepo.findByAuctionIdAndBidderId(auctionId, bidderId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Lấy (hoặc tạo mới) ReentrantLock cho 1 user ID.
     * computeIfAbsent đảm bảo thread-safe, mỗi user chỉ có đúng 1 lock.
     */
    private ReentrantLock getUserLock(String userId) {
        return userLocks.computeIfAbsent(userId, id -> new ReentrantLock(true));
    }

    // ── Seed ──────────────────────────────────────────────────────────────────

    private void ensureSeeded() {
        if (!auctionRepo.findAll().isEmpty())
            return;

        UserService userService = UserService.getInstance();
        Seller carol = (Seller) userService.findByUsername("carol").orElse(null);
        Seller dave = (Seller) userService.findByUsername("dave").orElse(null);
        if (carol == null || dave == null)
            return;

        // Fixed seed time so timestamps are also identical across machines
        LocalDateTime seed = LocalDateTime.of(2025, 1, 1, 0, 0);

        // Items — deterministic IDs by item name
        Electronics laptop = new Electronics(did("item-laptop"), seed, "Laptop Dell XPS 15",
                "Laptop cao cấp, i9, 32GB RAM", 15_000_000, carol, 0);
        Electronics phone = new Electronics(did("item-phone"), seed, "iPhone 15 Pro Max", "Mới 100%, chưa kích hoạt",
                28_000_000, carol, 0);
        Art painting = new Art(did("item-painting"), seed, "Tranh sơn dầu phong cảnh", "Phong cảnh Việt Nam, 80x60cm",
                5_000_000, dave, "", 0);
        Vehicle car = new Vehicle(did("item-car"), seed, "Toyota Camry 2022", "Xe đẹp, ít đi, bảo hành hãng",
                800_000_000, dave, 0.0, 0);

        Auction a1 = createAuction(carol, laptop, com.auction.util.TimeSyncManager.getNow().plusDays(2));
        Auction a2 = createAuction(carol, phone, com.auction.util.TimeSyncManager.getNow().plusHours(5));
        Auction a3 = createAuction(dave, painting, com.auction.util.TimeSyncManager.getNow().plusDays(7));
        Auction a4 = createAuction(dave, car, com.auction.util.TimeSyncManager.getNow().plusDays(1));

        auctionRepo.deleteById(a1.getId());
        auctionRepo.deleteById(a2.getId());
        auctionRepo.deleteById(a3.getId());
        auctionRepo.deleteById(a4.getId());

        LocalDateTime end1 = com.auction.util.TimeSyncManager.getNow().plusDays(2);
        LocalDateTime end2 = com.auction.util.TimeSyncManager.getNow().plusHours(5);
        LocalDateTime end3 = com.auction.util.TimeSyncManager.getNow().plusDays(7);
        LocalDateTime end4 = com.auction.util.TimeSyncManager.getNow().plusDays(1);

        Auction b1 = new Auction(did("auction-laptop"), seed, carol, laptop, AuctionStatus.PENDING,
                laptop.getBasePrice(), null, end1);
        Auction b2 = new Auction(did("auction-phone"), seed, carol, phone, AuctionStatus.PENDING, phone.getBasePrice(),
                null, end2);
        Auction b3 = new Auction(did("auction-painting"), seed, dave, painting, AuctionStatus.PENDING,
                painting.getBasePrice(), null, end3);
        Auction b4 = new Auction(did("auction-car"), seed, dave, car, AuctionStatus.PENDING, car.getBasePrice(), null,
                end4);

        auctionRepo.save(b1);
        auctionRepo.save(b2);
        auctionRepo.save(b3);
        auctionRepo.save(b4);

        try {
            approveAuction(b1);
            approveAuction(b2);
            approveAuction(b3);
            approveAuction(b4);
            startAuction(b1);
            startAuction(b2);
        } catch (InvalidStatusException ignored) {
        }

        System.out.println("[AuctionService] Seed data inserted.");
    }

    /** Shorthand for deterministic ID generation. */
    private static String did(String key) {
        return UserService.deterministicId(key);
    }
}
