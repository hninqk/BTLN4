package com.auction.core.util;

import com.auction.core.model.Auction;
import com.auction.core.model.AuctionStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class HotItemCache {

    private static final HotItemCache INSTANCE = new HotItemCache();

    private final ConcurrentHashMap<String, AtomicInteger> bidCounts =
            new ConcurrentHashMap<>();

    private HotItemCache() {}

    public static HotItemCache getInstance() {
        return INSTANCE;
    }

    public void recordBid(String auctionId) {
        bidCounts.computeIfAbsent(auctionId, k -> new AtomicInteger(0))
                 .incrementAndGet();
    }

    public void seedFromList(List<Auction> auctions) {
        for (Auction a : auctions) {
            if (a.getStatus() == AuctionStatus.RUNNING) {
                bidCounts.computeIfAbsent(a.getId(),
                        k -> new AtomicInteger(a.getBidHistory().size()));
            }
        }

        evictClosed(auctions);
    }

    public void evict(String auctionId) {
        bidCounts.remove(auctionId);
    }

    public List<String> getTopN(int n) {
        return bidCounts.entrySet().stream()
                .sorted(Comparator.comparingInt(
                        (Map.Entry<String, AtomicInteger> e) -> e.getValue().get())
                        .reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    public int getBidCount(String auctionId) {
        AtomicInteger ai = bidCounts.get(auctionId);
        return ai == null ? 0 : ai.get();
    }

    public int size() {
        return bidCounts.size();
    }

    private void evictClosed(List<Auction> auctions) {

        var activeIds = new java.util.HashSet<String>();
        for (Auction a : auctions) {
            if (a.getStatus() == AuctionStatus.RUNNING) {
                activeIds.add(a.getId());
            }
        }

        bidCounts.keySet().removeIf(id -> !activeIds.contains(id));
    }
}
