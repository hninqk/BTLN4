package com.auction.core.util;

import com.auction.core.model.Auction;
import com.auction.core.model.AuctionStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HotItemCache – in-memory, thread-safe cache for "Top 5 Hot Auctions".
 *
 * Design
 * ──────
 * • Uses a ConcurrentHashMap<auctionId, bidCount> as the backing store.
 * • recordBid(auctionId) increments the bid counter in O(1) via
 *   AtomicInteger, with no locking on the read path.
 * • getTopN() scans the map (cheap – at most a few hundred active auctions)
 *   and returns the sorted top-N IDs.  This is called by the UI refresh
 *   timer (~5 s), not on every bid, so the occasional O(n) sort is fine.
 * • The cache is pre-seeded on startup / FULL_SYNC by seedFromList().
 * • All writes come from the background WebSocket/bid-listener thread;
 *   all reads come from Platform.runLater() — no additional locking needed.
 *
 * Memory: O(active_auctions) ≈ negligible.
 */
public final class HotItemCache {

    private static final HotItemCache INSTANCE = new HotItemCache();

    /** auctionId → live bid count (only RUNNING auctions tracked) */
    private final ConcurrentHashMap<String, AtomicInteger> bidCounts =
            new ConcurrentHashMap<>();

    private HotItemCache() {}

    public static HotItemCache getInstance() {
        return INSTANCE;
    }

    // ── Write path ────────────────────────────────────────────────────────────

    /**
     * Record a new bid for an auction. O(1).
     * Safe to call from any thread (WebSocket listener, etc.).
     */
    public void recordBid(String auctionId) {
        bidCounts.computeIfAbsent(auctionId, k -> new AtomicInteger(0))
                 .incrementAndGet();
    }

    /**
     * Seed the cache from a full auction list (called on FULL_SYNC or initial load).
     * Only RUNNING auctions are tracked. Existing counts are preserved for
     * already-tracked IDs; new auctions start at their current bid-history size.
     */
    public void seedFromList(List<Auction> auctions) {
        for (Auction a : auctions) {
            if (a.getStatus() == AuctionStatus.RUNNING) {
                bidCounts.computeIfAbsent(a.getId(),
                        k -> new AtomicInteger(a.getBidHistory().size()));
            }
        }
        // Evict closed/cancelled auctions to keep the map small
        evictClosed(auctions);
    }

    /**
     * Remove an auction from the cache (e.g. when it closes/cancels).
     */
    public void evict(String auctionId) {
        bidCounts.remove(auctionId);
    }

    // ── Read path ─────────────────────────────────────────────────────────────

    /**
     * Returns the top-N auction IDs sorted by descending bid count.
     * The caller should cross-reference these IDs against the full auction
     * list to get the Auction objects.
     *
     * Called infrequently (dashboard refresh ~5 s) so O(n log n) is fine.
     */
    public List<String> getTopN(int n) {
        return bidCounts.entrySet().stream()
                .sorted(Comparator.comparingInt(
                        (Map.Entry<String, AtomicInteger> e) -> e.getValue().get())
                        .reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Returns the current bid count for one auction, or 0 if not tracked. */
    public int getBidCount(String auctionId) {
        AtomicInteger ai = bidCounts.get(auctionId);
        return ai == null ? 0 : ai.get();
    }

    /** Total number of tracked auctions in the cache. */
    public int size() {
        return bidCounts.size();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void evictClosed(List<Auction> auctions) {
        // Build set of active IDs
        var activeIds = new java.util.HashSet<String>();
        for (Auction a : auctions) {
            if (a.getStatus() == AuctionStatus.RUNNING) {
                activeIds.add(a.getId());
            }
        }
        // Remove stale keys
        bidCounts.keySet().removeIf(id -> !activeIds.contains(id));
    }
}
