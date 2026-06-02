package com.auction.service;

import com.auction.core.exception.InvalidBidException;
import com.auction.core.exception.InvalidStatusException;
import com.auction.core.model.*;
import com.auction.infra.repository.JdbcAuctionRepository;
import com.auction.infra.repository.JdbcBidRepository;
import com.auction.infra.repository.JdbcUserRepository;
import com.auction.infra.repository.JdbcAutoBidRepository;
import com.auction.infra.util.SessionManager;

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
 * Seller tạo lịch → UPCOMING hoặc RUNNING tùy startTime
 * Server tự động chuyển UPCOMING → RUNNING khi đến startTime
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
     * UPCOMING + RUNNING + CLOSED — visible to public bidders (CLOSED so users can see
     * finished results).
     */
    public List<Auction> getPublicAuctions() {
        return auctionRepo.findAll().stream()
                .filter(a -> a.getStatus() == AuctionStatus.UPCOMING
                        || a.getStatus() == AuctionStatus.OPEN
                        || a.getStatus() == AuctionStatus.RUNNING
                        || a.getStatus() == AuctionStatus.CLOSED)
                .toList();
    }

    /** All auctions for a seller, including scheduled and closed auctions. */
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

    /** Legacy create path: starts immediately. */
    public Auction createAuction(Seller seller, Item item, LocalDateTime endTime) {
        LocalDateTime now = com.auction.infra.util.TimeSyncManager.getNow();
        if (endTime == null || !endTime.isAfter(now)) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian hiện tại.");
        }
        Auction auction = new Auction(seller, item, now, endTime);
        if (!auctionRepo.save(auction)) {
            throw new IllegalStateException("Không thể lưu phiên đấu giá vào cơ sở dữ liệu.");
        }
        return auction;
    }

    /** Seller schedules the auction start/end time; Admin no longer owns startTime. */
    public Auction createAuction(Seller seller, Item item, LocalDateTime startTime, LocalDateTime endTime) {
        validateAuctionTimes(startTime, endTime);
        Auction auction = new Auction(seller, item, startTime, endTime);
        if (!auctionRepo.save(auction)) {
            throw new IllegalStateException("Không thể lưu phiên đấu giá vào cơ sở dữ liệu.");
        }
        return auction;
    }

    public boolean removeAuction(String auctionId) {
        return auctionRepo.deleteById(auctionId);
    }

    // ── Status transitions ────────────────────────────────────────────────────

    /** Legacy approval path only. */
    public void approveAuction(Auction auction) throws InvalidStatusException {
        auction.approveAuction();
        auctionCache.put(auction.getId(), auction);
        auctionRepo.updateStatus(auction.getId(), auction.getStatus(),
                auction.getHighestBid(), auction.getStartTime());
    }

    /**
     * Legacy/manual start path retained for internal compatibility.
     */
    public void startAuction(Auction auction) throws InvalidStatusException {
        auction.startAuction();
        auctionCache.put(auction.getId(), auction);
        auctionRepo.updateStatus(auction.getId(), auction.getStatus(),
                auction.getHighestBid(), auction.getStartTime());
    }

    /** Server scheduler: UPCOMING/legacy OPEN → RUNNING when seller startTime arrives. */
    public boolean startScheduledIfDue(Auction auction, LocalDateTime now) throws InvalidStatusException {
        if (auction.getStatus() != AuctionStatus.UPCOMING && auction.getStatus() != AuctionStatus.OPEN) {
            return false;
        }
        if (auction.getEndTime() != null && !now.isBefore(auction.getEndTime())) {
            return false;
        }
        LocalDateTime startTime = auction.getStartTime();
        if (startTime == null || !now.isBefore(startTime)) {
            auction.goLive();
            auctionCache.put(auction.getId(), auction);
            auctionRepo.updateStatus(auction.getId(), auction.getStatus(),
                    auction.getHighestBid(), auction.getStartTime());
            return true;
        }
        return false;
    }

    /** Server scheduler: close any auction whose endTime has passed, even if it never went live. */
    public boolean closeExpiredIfDue(Auction auction, LocalDateTime now) throws InvalidStatusException {
        if (auction.getEndTime() == null || now.isBefore(auction.getEndTime())) {
            return false;
        }
        if (auction.getStatus() == AuctionStatus.CLOSED || auction.getStatus() == AuctionStatus.CANCELED) {
            return false;
        }
        if (auction.getStatus() == AuctionStatus.RUNNING) {
            finishAuction(auction);
            return true;
        }
        if (auction.getStatus() == AuctionStatus.UPCOMING || auction.getStatus() == AuctionStatus.OPEN) {
            auction.setStatus(AuctionStatus.CLOSED);
            auctionCache.put(auction.getId(), auction);
            auctionRepo.updateStatus(auction.getId(), auction.getStatus(),
                    auction.getHighestBid(), auction.getStartTime());
            return true;
        }
        return false;
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

    private void validateAuctionTimes(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Thời gian bắt đầu và kết thúc là bắt buộc.");
        }
        LocalDateTime now = com.auction.infra.util.TimeSyncManager.getNow();
        if (startTime.isBefore(now)) {
            throw new IllegalArgumentException("Thời gian bắt đầu phải lớn hơn hoặc bằng thời gian hiện tại.");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
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
                "Laptop cao cấp, i9, 32GB RAM", 15_000_000, carol);
        Electronics phone = new Electronics(did("item-phone"), seed, "iPhone 15 Pro Max", "Mới 100%, chưa kích hoạt",
                28_000_000, carol);
        Art painting = new Art(did("item-painting"), seed, "Tranh sơn dầu phong cảnh", "Phong cảnh Việt Nam, 80x60cm",
                5_000_000, dave, "");
        Vehicle car = new Vehicle(did("item-car"), seed, "Toyota Camry 2022", "Xe đẹp, ít đi, bảo hành hãng",
                800_000_000, dave);

        Auction a1 = createAuction(carol, laptop, com.auction.infra.util.TimeSyncManager.getNow().plusDays(2));
        Auction a2 = createAuction(carol, phone, com.auction.infra.util.TimeSyncManager.getNow().plusHours(5));
        Auction a3 = createAuction(dave, painting, com.auction.infra.util.TimeSyncManager.getNow().plusDays(7));
        Auction a4 = createAuction(dave, car, com.auction.infra.util.TimeSyncManager.getNow().plusDays(1));

        auctionRepo.deleteById(a1.getId());
        auctionRepo.deleteById(a2.getId());
        auctionRepo.deleteById(a3.getId());
        auctionRepo.deleteById(a4.getId());

        LocalDateTime end1 = com.auction.infra.util.TimeSyncManager.getNow().plusDays(2);
        LocalDateTime end2 = com.auction.infra.util.TimeSyncManager.getNow().plusHours(5);
        LocalDateTime end3 = com.auction.infra.util.TimeSyncManager.getNow().plusDays(7);
        LocalDateTime end4 = com.auction.infra.util.TimeSyncManager.getNow().plusDays(1);

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
