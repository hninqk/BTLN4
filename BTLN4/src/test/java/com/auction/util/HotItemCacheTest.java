package com.auction.util;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.model.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HotItemCacheTest {

    private HotItemCache cache;

    @BeforeEach
    void setUp() {
        cache = HotItemCache.getInstance();
        // Since there's no clear(), we just use it as is or try to evict known IDs if needed
        // but for unit test it's better if it's fresh. 
        // We can use reflection to clear the map if we want a clean state.
        clearCacheViaReflection(cache);
    }

    @Test
    void seedFromListAndGetTopN() {
        Seller seller = new Seller("s1", "pass", "shop");
        Electronics item = new Electronics("Laptop", "desc", 1000, seller);
        Auction a1 = new Auction("a1", LocalDateTime.now(), seller, item, AuctionStatus.RUNNING, 1000, null, LocalDateTime.now().plusHours(1));
        
        List<Auction> list = new ArrayList<>();
        list.add(a1);
        
        cache.seedFromList(list);
        
        List<String> topN = cache.getTopN(5);
        assertEquals(1, topN.size());
        assertEquals("a1", topN.get(0));
    }

    @Test
    void testEvict() {
        cache.recordBid("a3");
        assertEquals(1, cache.size());
        cache.evict("a3");
        assertEquals(0, cache.size());
    }

    @Test
    void testSeedWithClosedAuctions() {
        Seller seller = new Seller("s1", "pass", "shop");
        Electronics item = new Electronics("Laptop", "desc", 1000, seller);
        Auction a1 = new Auction("a1", LocalDateTime.now(), seller, item, AuctionStatus.RUNNING, 1000, null, LocalDateTime.now().plusHours(1));
        Auction a2 = new Auction("a2", LocalDateTime.now(), seller, item, AuctionStatus.CLOSED, 1000, null, LocalDateTime.now().plusHours(1));

        cache.recordBid("a2"); // Track it first
        assertEquals(1, cache.size());

        List<Auction> list = List.of(a1, a2);
        cache.seedFromList(list);

        assertEquals(1, cache.size());
        assertTrue(cache.getTopN(5).contains("a1"));
        assertFalse(cache.getTopN(5).contains("a2")); // Should be evicted
    }

    private void clearCacheViaReflection(HotItemCache cache) {
        try {
            java.lang.reflect.Field field = HotItemCache.class.getDeclaredField("bidCounts");
            field.setAccessible(true);
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) field.get(cache);
            map.clear();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
