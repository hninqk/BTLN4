package com.auction.service;

import com.auction.core.exception.InvalidBidException;
import com.auction.core.exception.InvalidStatusException;
import com.auction.core.model.Auction;
import com.auction.core.model.AutoBid;
import com.auction.core.model.BidTransaction;
import com.auction.core.model.Bidder;
import com.auction.infra.repository.JdbcAuctionRepository;
import com.auction.infra.repository.JdbcAutoBidRepository;
import com.auction.infra.repository.JdbcBidRepository;
import com.auction.infra.repository.JdbcUserRepository;
import com.auction.api.config.AppConfig;
import com.auction.core.util.TimeSyncManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the complete bidding workflow:
 * validation, fund freezing, bid creation, anti-sniping, persistence, and
 * outbid unfreeze state.
 */
public final class BiddingService {

    private static final Logger log = LoggerFactory.getLogger(BiddingService.class);

    private final JdbcAuctionRepository auctionRepo;
    private final JdbcBidRepository bidRepo;
    private final JdbcUserRepository userRepo;
    private final JdbcAutoBidRepository autoBidRepo;
    private final Map<String, Auction> auctionCache;
    private final ConcurrentHashMap<String, ReentrantLock> userLocks;
    private final ConcurrentHashMap<String, ReentrantLock> auctionLocks = new ConcurrentHashMap<>();
    private final ExecutorService persistenceExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "BidPersist");
        t.setDaemon(true);
        return t;
    });
    private final ProxyBiddingEngine proxyBiddingEngine;

    private final ThreadLocal<String> pendingUnfreezeId = new ThreadLocal<>();
    private final ThreadLocal<Double> pendingUnfreezeAmount = ThreadLocal.withInitial(() -> 0.0);
    private final ThreadLocal<String> pendingUnfreezeAuctionId = new ThreadLocal<>();

    public BiddingService(
            JdbcAuctionRepository auctionRepo,
            JdbcBidRepository bidRepo,
            JdbcUserRepository userRepo,
            JdbcAutoBidRepository autoBidRepo,
            Map<String, Auction> auctionCache,
            ConcurrentHashMap<String, ReentrantLock> userLocks) {
        this.auctionRepo = auctionRepo;
        this.bidRepo = bidRepo;
        this.userRepo = userRepo;
        this.autoBidRepo = autoBidRepo;
        this.auctionCache = auctionCache;
        this.userLocks = userLocks;
        this.proxyBiddingEngine = new ProxyBiddingEngine(autoBidRepo, userRepo, this);
    }

    public BidTransaction placeBid(Auction auction, Bidder bidder, double amount)
            throws InvalidBidException, InvalidStatusException {
        String bidderId = bidder.getId();
        log.info("Bid requested auctionId={} bidderId={} amount={}", auction.getId(), bidderId, amount);

        ReentrantLock lock = getUserLock(bidderId);
        lock.lock();
        try {
            Bidder freshBidder = (Bidder) userRepo.findById(bidderId)
                    .filter(u -> u instanceof Bidder)
                    .orElse(bidder);

            BidTransaction currentHighest = auction.getWinner();
            if (currentHighest != null
                    && currentHighest.getBidder() != null
                    && bidderId.equals(currentHighest.getBidder().getId())) {
                throw new InvalidBidException(
                        "Bạn đang là người ra giá cao nhất, không thể đặt giá 2 lần liên tiếp cho sản phẩm này.");
            }

            double step = com.auction.core.util.BidLadderUtil.getIncrementForPrice(auction.getHighestBid());
            double minRequired = auction.getHighestBid() + step;
            if (amount < minRequired) {
                throw new InvalidBidException(String.format("Giá đặt tối thiểu phải là %,.0f ₫ (bước giá %,.0f ₫).", minRequired, step));
            }

            double available = freshBidder.getAvailableBalance();
            if (amount > available) {
                throw new InvalidBidException(
                        String.format("Số dư khả dụng không đủ. Khả dụng: %,.0f ₫ | Đóng băng: %,.0f ₫ | Giá đặt: %,.0f ₫",
                                available,
                                freshBidder.getFrozenBalance(),
                                amount));
            }

            Bidder oldHighestBidder = null;
            double oldHighestAmount = 0;
            if (currentHighest != null && currentHighest.getBidder() != null) {
                oldHighestAmount = currentHighest.getAmount();
                String oldId = currentHighest.getBidder().getId();
                if (!oldId.equals(bidderId)) {
                    oldHighestBidder = (Bidder) userRepo.findById(oldId)
                            .filter(u -> u instanceof Bidder)
                            .orElse(null);
                }
            }

            boolean hasAutoBid = autoBidRepo.findByAuctionId(auction.getId()).stream()
                    .anyMatch(ab -> ab.getBidderId().equals(freshBidder.getId()));
            if (!hasAutoBid) {
                freshBidder.freezeFunds(amount);
                userRepo.updateFrozenBalance(bidderId, freshBidder.getFrozenBalance());
                log.info("Funds frozen bidderId={} amount={} frozen={} available={}",
                        bidderId, amount, freshBidder.getFrozenBalance(), freshBidder.getAvailableBalance());
            }

            pendingUnfreezeAuctionId.set(auction.getId());

            BidTransaction bid = new BidTransaction(
                    UUID.randomUUID().toString(),
                    TimeSyncManager.getNow(),
                    freshBidder,
                    auction,
                    amount);

            auction.placeBid(bid);
            applyAntiSnipingIfNeeded(auction);
            auctionCache.put(auction.getId(), auction);
            persistBidAsync(auction, bid);

            pendingUnfreezeId.set((oldHighestBidder != null) ? oldHighestBidder.getId() : null);
            pendingUnfreezeAmount.set(oldHighestAmount);

            log.info("Bid accepted auctionId={} bidderId={} amount={} highestBid={} endTime={}",
                    auction.getId(), bidderId, amount, auction.getHighestBid(), auction.getEndTime());
            return bid;
        } catch (InvalidBidException | InvalidStatusException e) {
            log.warn("Bid rejected auctionId={} bidderId={} amount={} reason={}",
                    auction.getId(), bidderId, amount, e.getMessage());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    public Bidder processOutbidUnfreeze() {
        String oldId = pendingUnfreezeId.get();
        double oldAmount = pendingUnfreezeAmount.get();
        String auctionId = pendingUnfreezeAuctionId.get();
        pendingUnfreezeId.remove();
        pendingUnfreezeAmount.remove();
        pendingUnfreezeAuctionId.remove();

        if (oldId == null || oldAmount <= 0) {
            return null;
        }

        ReentrantLock oldLock = getUserLock(oldId);
        oldLock.lock();
        try {
            Bidder freshOld = (Bidder) userRepo.findById(oldId)
                    .filter(u -> u instanceof Bidder)
                    .orElse(null);
            if (freshOld == null) {
                return null;
            }

            boolean oldHasAutoBid = auctionId != null && autoBidRepo.findByAuctionId(auctionId).stream()
                    .anyMatch(ab -> ab.getBidderId().equals(freshOld.getId()));

            if (!oldHasAutoBid) {
                freshOld.unfreezeFunds(oldAmount);
                userRepo.updateFrozenBalance(freshOld.getId(), freshOld.getFrozenBalance());
                log.info("Outbid funds unfrozen bidderId={} amount={} frozen={} available={}",
                        freshOld.getId(), oldAmount, freshOld.getFrozenBalance(), freshOld.getAvailableBalance());
            }
            return freshOld;
        } finally {
            oldLock.unlock();
        }
    }

    public AuctionService.AutoBidResult registerAutoBid(
            Auction auction,
            Bidder bidder,
            double maxBid,
            double increment) throws InvalidBidException {
        ReentrantLock lock = getUserLock(bidder.getId());
        lock.lock();
        try {
            Bidder freshBidder = (Bidder) userRepo.findById(bidder.getId())
                    .filter(u -> u instanceof Bidder)
                    .orElse(bidder);

            double step = com.auction.core.util.BidLadderUtil.getIncrementForPrice(auction.getHighestBid());
            double minRequired = auction.getHighestBid() + step;
            if (maxBid < minRequired) {
                throw new InvalidBidException(String.format("Giá tối đa (Max Bid) tối thiểu phải là %,.0f ₫ (bước giá %,.0f ₫).", minRequired, step));
            }
            if (freshBidder.getAvailableBalance() < maxBid) {
                throw new InvalidBidException("Số dư không đủ. Cần " + String.format("%,.0f ₫", maxBid)
                        + " để đặt Auto-Bid.");
            }

            AutoBid existing = autoBidRepo.findByAuctionId(auction.getId()).stream()
                    .filter(ab -> ab.getBidderId().equals(freshBidder.getId()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                autoBidRepo.deleteByAuctionIdAndBidderId(auction.getId(), freshBidder.getId());
                freshBidder.unfreezeFunds(existing.getMaxBid());
                userRepo.updateFrozenBalance(freshBidder.getId(), freshBidder.getFrozenBalance());
            }

            freshBidder.freezeFunds(maxBid);
            userRepo.updateFrozenBalance(freshBidder.getId(), freshBidder.getFrozenBalance());

            AutoBid autoBid = new AutoBid(
                    UUID.randomUUID().toString(),
                    auction.getId(),
                    freshBidder.getId(),
                    maxBid,
                    increment,
                    TimeSyncManager.getNow());
            autoBidRepo.save(autoBid);
            log.info("Auto-bid registered auctionId={} bidderId={} maxBid={} increment={}",
                    auction.getId(), freshBidder.getId(), maxBid, increment);
        } finally {
            lock.unlock();
        }

        return resolveBiddingWar(auction);
    }

    public AuctionService.AutoBidResult resolveBiddingWar(Auction auction) {
        ReentrantLock lock = getAuctionLock(auction.getId());
        lock.lock();
        try {
            return proxyBiddingEngine.resolveBiddingWar(auction);
        } finally {
            lock.unlock();
        }
    }

    private void applyAntiSnipingIfNeeded(Auction auction) {
        LocalDateTime endTime = auction.getEndTime();
        if (endTime == null) {
            return;
        }

        LocalDateTime now = TimeSyncManager.getNow();
        long diffSeconds = Duration.between(now, endTime).getSeconds();
        if (diffSeconds > 0 && diffSeconds <= AppConfig.antiSnipeWindowSeconds()) {
            LocalDateTime extendedEndTime = endTime.plusSeconds(AppConfig.antiSnipeExtensionSeconds());
            auction.setEndTime(extendedEndTime);
            auctionRepo.updateEndTime(auction.getId(), extendedEndTime);
            log.info("Anti-sniping extended auctionId={} oldEndTime={} newEndTime={} diffSeconds={}",
                    auction.getId(), endTime, extendedEndTime, diffSeconds);
        }
    }

    private void persistBidAsync(Auction auction, BidTransaction bid) {
        persistenceExecutor.submit(() -> {
            try {
                bidRepo.save(bid);
                auctionRepo.updateStatus(
                        auction.getId(),
                        auction.getStatus(),
                        auction.getHighestBid(),
                        auction.getStartTime());
            } catch (Exception e) {
                log.error("Bid persistence failed auctionId={} bidId={} bidderId={}",
                        auction.getId(), bid.getId(), bid.getBidder().getId(), e);
            }
        });
    }

    private ReentrantLock getUserLock(String userId) {
        return userLocks.computeIfAbsent(userId, id -> new ReentrantLock(true));
    }

    private ReentrantLock getAuctionLock(String auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, id -> new ReentrantLock(true));
    }
}
