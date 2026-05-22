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

    /**
     * Lock per-auction để tránh race condition khi 2+ user đăng ký auto-bid
     * cùng lúc trên cùng 1 phiên đấu giá.
     */
    private final ConcurrentHashMap<String, ReentrantLock> auctionLocks = new ConcurrentHashMap<>();

    private final ProxyBiddingEngine proxyBiddingEngine;

    private AuctionService() {
        this.proxyBiddingEngine = new ProxyBiddingEngine(autoBidRepo, userRepo, this);
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
        auction.startAuction(); // model sets startTime = LocalDateTime.now()
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

    // ── Bidding ───────────────────────────────────────────────────────────────

    /**
     * Bidder đặt giá.
     *
     * Luồng xử lý (thread-safe):
     * 1. Lock user theo bidderId
     * 2. Load bidder fresh từ DB (balance chính xác nhất)
     * 3. Kiểm tra bidder có đang là highest bidder không → chặn double-bid
     * 4. Kiểm tra availableBalance >= amount → từ chối nếu không đủ
     * 5. Lưu thông tin old highest bidder (để unfreeze sau)
     * 6. Freeze tiền bidder mới: frozenBalance += amount → persist DB
     * 7. auction.placeBid() [synchronized trong Auction model]
     * 8. Unfreeze old highest bidder (nếu có và khác bidder mới)
     * 9. Unlock
     *
     * @param auction phiên đấu giá
     * @param bidder  người đặt giá (có thể stale — sẽ reload fresh từ DB)
     * @param amount  mức giá đặt
     * @return BidTransaction được tạo
     */
    public BidTransaction placeBid(Auction auction, Bidder bidder, double amount)
            throws InvalidBidException, InvalidStatusException {

        String bidderId = bidder.getId();
        ReentrantLock lock = getUserLock(bidderId);
        lock.lock();
        try {
            // ── 1. Load bidder fresh từ DB (balance authoritative) ──
            Bidder freshBidder = (Bidder) userRepo.findById(bidderId)
                    .filter(u -> u instanceof Bidder)
                    .orElse(bidder); // fallback nếu chưa có trong DB

            // ── 2. Chặn double-bid: không cho bidder đặt giá nếu đang là highest ──
            BidTransaction currentHighest = auction.getWinner();
            if (currentHighest != null
                    && currentHighest.getBidder().getUsername().equals(freshBidder.getUsername())) {
                throw new InvalidBidException(
                        "Bạn đang là người ra giá cao nhất (" +
                                String.format("%,.0f ₫", currentHighest.getAmount()) +
                                "), không thể đặt giá 2 lần liên tiếp cho sản phẩm này.");
            }

            // ── 2.5 Kiểm tra số tiền đặt tối thiểu ──
            if (amount <= auction.getHighestBid()) {
                throw new InvalidBidException("Số tiền đặt giá chưa đủ!");
            }

            // ── 3. Kiểm tra số dư khả dụng ──
            double available = freshBidder.getAvailableBalance();
            if (amount > available) {
                throw new InvalidBidException(
                        String.format("Số dư khả dụng không đủ. " +
                                "Khả dụng: %,.0f ₫ | Đóng băng: %,.0f ₫ | Giá đặt: %,.0f ₫",
                                available,
                                freshBidder.getFrozenBalance(),
                                amount));
            }

            // ── 4. Lưu old highest bidder trước khi đặt giá mới ──
            Bidder oldHighestBidder = null;
            double oldHighestAmount = 0;
            if (currentHighest != null) {
                oldHighestAmount = currentHighest.getAmount();
                String oldId = currentHighest.getBidder().getId();
                // Chỉ unfreeze nếu old bidder là người khác
                if (!oldId.equals(bidderId)) {
                    oldHighestBidder = (Bidder) userRepo.findById(oldId)
                            .filter(u -> u instanceof Bidder)
                            .orElse(null);
                }
            }

            // ── 5. Đóng băng tiền bidder mới ──
            boolean hasAutoBid = autoBidRepo.findByAuctionId(auction.getId()).stream()
                    .anyMatch(ab -> ab.getBidderId().equals(freshBidder.getId()));
            
            if (!hasAutoBid) {
                freshBidder.freezeFunds(amount);
                userRepo.updateFrozenBalance(bidderId, freshBidder.getFrozenBalance());
                System.out.printf("[AuctionService] FREEZE bidder=%s amount=%,.0f ₫ | " +
                        "frozen=%.0f ₫ | available=%.0f ₫%n",
                        freshBidder.getUsername(), amount,
                        freshBidder.getFrozenBalance(),
                        freshBidder.getAvailableBalance());
            }

            // Ghi nhớ auctionId để unfreeze
            this.pendingUnfreezeAuctionId.set(auction.getId());

            // ── 6. Tạo bid transaction và ghi vào auction (synchronized) ──
            BidTransaction bid = new BidTransaction(
                    java.util.UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    freshBidder,
                    auction,
                    amount);

            // Throws InvalidBidException nếu amount ≤ highest, InvalidStatusException nếu
            // không RUNNING
            auction.placeBid(bid);

            // --- Anti-Sniping (Bid Extension) ---
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime endTime = auction.getEndTime();
            if (endTime != null && java.time.Duration.between(now, endTime).getSeconds() <= 60) {
                auction.setEndTime(endTime.plusMinutes(3));
                auctionRepo.updateEndTime(auction.getId(), auction.getEndTime());
                System.out.printf("[AuctionService] Anti-Sniping activated for auction %s: Extended end time by 3 minutes.%n", auction.getId());
            }

            // Cập nhật cache
            auctionCache.put(auction.getId(), auction);

            // Persist bid + highest_bid bất đồng bộ
            final BidTransaction bidToSave = bid;
            new Thread(() -> {
                bidRepo.save(bidToSave);
                auctionRepo.updateStatus(auction.getId(), auction.getStatus(),
                        auction.getHighestBid(), auction.getStartTime());
            }, "BidPersist-" + bidderId).start();

            // ── 7. Ghi nhận thông tin old bidder để WebSocket handler xử lý unfreeze ──
            // Sử dụng ThreadLocal để an toàn khi nhiều luồng cùng gọi placeBid()
            this.pendingUnfreezeId.set((oldHighestBidder != null) ? oldHighestBidder.getId() : null);
            this.pendingUnfreezeAmount.set(oldHighestAmount);

            return bid;

        } finally {
            lock.unlock();
        }
    }

    /**
     * Hoàn trả tiền đóng băng cho old highest bidder sau khi họ bị outbid.
     *
     * Phải được gọi NGAY SAU placeBid() từ cùng luồng (WebSocket handler).
     * Thực thi đồng bộ với ReentrantLock riêng của old bidder.
     *
     * @return old bidder đã được unfreeze (để broadcast BALANCE_UPDATE), hoặc null
     */
    public Bidder processOutbidUnfreeze() {
        String oldId = this.pendingUnfreezeId.get();
        double oldAmount = this.pendingUnfreezeAmount.get();
        String auctionId = this.pendingUnfreezeAuctionId.get();
        this.pendingUnfreezeId.remove();
        this.pendingUnfreezeAmount.remove();
        this.pendingUnfreezeAuctionId.remove();

        if (oldId == null || oldAmount <= 0) {
            return null;
        }

        ReentrantLock oldLock = getUserLock(oldId);
        oldLock.lock();
        try {
            Bidder freshOld = (Bidder) userRepo.findById(oldId)
                    .filter(u -> u instanceof Bidder)
                    .orElse(null);
            if (freshOld == null)
                return null;

            boolean oldHasAutoBid = auctionId != null && autoBidRepo.findByAuctionId(auctionId).stream()
                    .anyMatch(ab -> ab.getBidderId().equals(freshOld.getId()));

            if (!oldHasAutoBid) {
                freshOld.unfreezeFunds(oldAmount);
                userRepo.updateFrozenBalance(freshOld.getId(), freshOld.getFrozenBalance());
                System.out.printf("[AuctionService] UNFREEZE (outbid) bidder=%s amount=%,.0f ₫ | " +
                        "frozen=%.0f ₫ | available=%.0f ₫%n",
                        freshOld.getUsername(), oldAmount,
                        freshOld.getFrozenBalance(), freshOld.getAvailableBalance());
            } else {
                System.out.printf("[AuctionService] SKIP UNFREEZE (AutoBid active) bidder=%s amount=%,.0f ₫%n",
                        freshOld.getUsername(), oldAmount);
            }
            return freshOld;
        } finally {
            oldLock.unlock();
        }
    }

    /**
     * Thông tin old bidder cần unfreeze – ThreadLocal để an toàn giữa các luồng.
     */
    private final ThreadLocal<String> pendingUnfreezeId = new ThreadLocal<>();
    private final ThreadLocal<Double> pendingUnfreezeAmount = ThreadLocal.withInitial(() -> 0.0);
    private final ThreadLocal<String> pendingUnfreezeAuctionId = new ThreadLocal<>();

    // ── Auto-Bidding ──────────────────────────────────────────────────────────

    public static class AutoBidResult {
        public List<BidTransaction> newBids = new java.util.ArrayList<>();
        public List<Bidder> unfrozenBidders = new java.util.ArrayList<>();
        public List<String> virtualLogs = new java.util.ArrayList<>();
        public List<String> deactivatedBidderIds = new java.util.ArrayList<>();
    }

    public AutoBidResult registerAutoBid(Auction auction, Bidder bidder, double maxBid, double increment) throws InvalidBidException {
        if (maxBid <= auction.getHighestBid()) {
            throw new InvalidBidException("Max Bid phải lớn hơn giá hiện tại.");
        }
        if (bidder.getAvailableBalance() < maxBid) {
            throw new InvalidBidException("Số dư không đủ. Cần " + String.format("%,.0f ₫", maxBid) + " để đặt Auto-Bid.");
        }
        
        // Remove existing autobid if any, and unfreeze its old maxBid
        AutoBid existing = autoBidRepo.findByAuctionId(auction.getId()).stream()
                .filter(ab -> ab.getBidderId().equals(bidder.getId())).findFirst().orElse(null);
        if (existing != null) {
            autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), bidder.getId());
            bidder.unfreezeFunds(existing.getMaxBid());
            userRepo.updateFrozenBalance(bidder.getId(), bidder.getFrozenBalance());
        }
        
        // Freeze new maxBid immediately (Dong Bang)
        bidder.freezeFunds(maxBid);
        userRepo.updateFrozenBalance(bidder.getId(), bidder.getFrozenBalance());
        
        AutoBid ab = new AutoBid(
            java.util.UUID.randomUUID().toString(),
            auction.getId(),
            bidder.getId(),
            maxBid,
            increment,
            LocalDateTime.now()
        );
        autoBidRepo.save(ab);
        
        return resolveBiddingWar(auction);
    }

    public AutoBidResult resolveBiddingWar(Auction auction) {
        ReentrantLock lock = getAuctionLock(auction.getId());
        lock.lock();
        try {
            return proxyBiddingEngine.resolveBiddingWar(auction);
        } finally {
            lock.unlock();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Lấy (hoặc tạo mới) ReentrantLock cho 1 user ID.
     * computeIfAbsent đảm bảo thread-safe, mỗi user chỉ có đúng 1 lock.
     */
    private ReentrantLock getUserLock(String userId) {
        return userLocks.computeIfAbsent(userId, id -> new ReentrantLock(true));
    }

    private ReentrantLock getAuctionLock(String auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, id -> new ReentrantLock(true));
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

        Auction a1 = createAuction(carol, laptop, LocalDateTime.now().plusDays(2));
        Auction a2 = createAuction(carol, phone, LocalDateTime.now().plusHours(5));
        Auction a3 = createAuction(dave, painting, LocalDateTime.now().plusDays(7));
        Auction a4 = createAuction(dave, car, LocalDateTime.now().plusDays(1));

        auctionRepo.deleteById(a1.getId());
        auctionRepo.deleteById(a2.getId());
        auctionRepo.deleteById(a3.getId());
        auctionRepo.deleteById(a4.getId());

        LocalDateTime end1 = LocalDateTime.now().plusDays(2);
        LocalDateTime end2 = LocalDateTime.now().plusHours(5);
        LocalDateTime end3 = LocalDateTime.now().plusDays(7);
        LocalDateTime end4 = LocalDateTime.now().plusDays(1);

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