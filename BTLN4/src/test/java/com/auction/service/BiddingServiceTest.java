package com.auction.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.Electronics;
import com.auction.model.AuctionStatus;
import com.auction.repository.JdbcAuctionRepository;
import com.auction.repository.JdbcAutoBidRepository;
import com.auction.repository.JdbcBidRepository;
import com.auction.repository.JdbcUserRepository;
import com.auction.util.AppConfig;
import com.auction.util.TimeSyncManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class BiddingServiceTest {

    private JdbcAuctionRepository auctionRepo;
    private JdbcBidRepository bidRepo;
    private JdbcUserRepository userRepo;
    private JdbcAutoBidRepository autoBidRepo;
    private Map<String, Auction> auctionCache;
    private ConcurrentHashMap<String, ReentrantLock> userLocks;
    private BiddingService biddingService;

    private MockedStatic<TimeSyncManager> mockedTime;
    private MockedStatic<AppConfig> mockedConfig;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        auctionRepo = mock(JdbcAuctionRepository.class);
        bidRepo = mock(JdbcBidRepository.class);
        userRepo = mock(JdbcUserRepository.class);
        autoBidRepo = mock(JdbcAutoBidRepository.class);
        auctionCache = new HashMap<>();
        userLocks = new ConcurrentHashMap<>();
        biddingService = new BiddingService(auctionRepo, bidRepo, userRepo, autoBidRepo, auctionCache, userLocks);

        now = LocalDateTime.of(2025, 1, 1, 12, 0);
        mockedTime = mockStatic(TimeSyncManager.class);
        mockedTime.when(TimeSyncManager::getNow).thenReturn(now);

        mockedConfig = mockStatic(AppConfig.class);
        mockedConfig.when(AppConfig::antiSnipeWindowSeconds).thenReturn(60L);
        mockedConfig.when(AppConfig::antiSnipeExtensionSeconds).thenReturn(180L);
    }

    @AfterEach
    void tearDown() {
        mockedTime.close();
        mockedConfig.close();
    }

    @Test
    void placeBidSuccess() throws Exception {
        Seller seller = new Seller("s1", now, "seller", "pass", "shop");
        Electronics item = new Electronics("i1", now, "Laptop", "desc", 1000, seller);
        Auction auction = new Auction("a1", now, seller, item, AuctionStatus.RUNNING, 1000, now, now.plusHours(1));
        
        Bidder bidder = new Bidder("b1", now, "bidder", "pass", 2000.0);

        when(userRepo.findById("b1")).thenReturn(Optional.of(bidder));
        when(autoBidRepo.findByAuctionId("a1")).thenReturn(new ArrayList<>());

        BidTransaction bid = biddingService.placeBid(auction, bidder, 1500);

        assertNotNull(bid);
        assertEquals(1500, bid.getAmount());
        assertEquals(bidder, bid.getBidder());
        assertEquals(1500, auction.getHighestBid());
        assertEquals(1500, bidder.getFrozenBalance());
        assertEquals(500, bidder.getAvailableBalance());
        
        verify(userRepo).updateFrozenBalance(eq("b1"), eq(1500.0));
        assertTrue(auctionCache.containsKey("a1"));
    }

    @Test
    void placeBidThrowsWhenBiddingOnOwnHighestBid() {
        Seller seller = new Seller("s1", now, "seller", "pass", "shop");
        Electronics item = new Electronics("i1", now, "Laptop", "desc", 1000, seller);
        Bidder bidder = new Bidder("b1", now, "bidder", "pass", 2000.0);
        
        Auction auction = new Auction("a1", now, seller, item, AuctionStatus.RUNNING, 1500, now, now.plusHours(1));
        BidTransaction existingBid = new BidTransaction("bt1", now, bidder, auction, 1500);
        auction.injectBid(existingBid);

        when(userRepo.findById("b1")).thenReturn(Optional.of(bidder));

        assertThrows(InvalidBidException.class, () -> biddingService.placeBid(auction, bidder, 1600));
    }

    @Test
    void placeBidThrowsWhenAmountTooLow() {
        Seller seller = new Seller("s1", now, "seller", "pass", "shop");
        Electronics item = new Electronics("i1", now, "Laptop", "desc", 1000, seller);
        Auction auction = new Auction("a1", now, seller, item, AuctionStatus.RUNNING, 1500, now, now.plusHours(1));
        
        Bidder bidder = new Bidder("b1", now, "bidder", "pass", 2000.0);

        when(userRepo.findById("b1")).thenReturn(Optional.of(bidder));

        assertThrows(InvalidBidException.class, () -> biddingService.placeBid(auction, bidder, 1400));
    }

    @Test
    void placeBidThrowsWhenInsufficientBalance() {
        Seller seller = new Seller("s1", now, "seller", "pass", "shop");
        Electronics item = new Electronics("i1", now, "Laptop", "desc", 1000, seller);
        Auction auction = new Auction("a1", now, seller, item, AuctionStatus.RUNNING, 1000, now, now.plusHours(1));
        
        Bidder bidder = new Bidder("b1", now, "bidder", "pass", 1200.0);

        when(userRepo.findById("b1")).thenReturn(Optional.of(bidder));

        assertThrows(InvalidBidException.class, () -> biddingService.placeBid(auction, bidder, 1500));
    }

    @Test
    void placeBidAppliesAntiSniping() throws Exception {
        Seller seller = new Seller("s1", now, "seller", "pass", "shop");
        Electronics item = new Electronics("i1", now, "Laptop", "desc", 1000, seller);
        // End time is in 30 seconds (within 60s window)
        LocalDateTime endTime = now.plusSeconds(30);
        Auction auction = new Auction("a1", now, seller, item, AuctionStatus.RUNNING, 1000, now, endTime);
        
        Bidder bidder = new Bidder("b1", now, "bidder", "pass", 2000.0);

        when(userRepo.findById("b1")).thenReturn(Optional.of(bidder));
        when(autoBidRepo.findByAuctionId("a1")).thenReturn(new ArrayList<>());

        biddingService.placeBid(auction, bidder, 1500);

        // Should be extended by 180 seconds
        assertEquals(endTime.plusSeconds(180), auction.getEndTime());
        verify(auctionRepo).updateEndTime(eq("a1"), eq(endTime.plusSeconds(180)));
    }

    @Test
    void registerAutoBidSuccess() throws Exception {
        Seller seller = new Seller("s1", now, "seller", "pass", "shop");
        Electronics item = new Electronics("i1", now, "Laptop", "desc", 1000, seller);
        Auction auction = new Auction("a1", now, seller, item, AuctionStatus.RUNNING, 1000, now, now.plusHours(1));
        
        Bidder bidder = new Bidder("b1", now, "bidder", "pass", 5000.0);

        when(userRepo.findById("b1")).thenReturn(Optional.of(bidder));
        when(autoBidRepo.findByAuctionId("a1")).thenReturn(new ArrayList<>());

        biddingService.registerAutoBid(auction, bidder, 4000, 100);

        verify(autoBidRepo).save(any());
        assertEquals(4000, bidder.getFrozenBalance());
        assertEquals(1000, bidder.getAvailableBalance());
    }

    @Test
    void registerAutoBidThrowsWhenMaxBidTooLow() {
        Seller seller = new Seller("s1", now, "seller", "pass", "shop");
        Electronics item = new Electronics("i1", now, "Laptop", "desc", 1000, seller);
        Auction auction = new Auction("a1", now, seller, item, AuctionStatus.RUNNING, 2000, now, now.plusHours(1));
        
        Bidder bidder = new Bidder("b1", now, "bidder", "pass", 5000.0);

        when(userRepo.findById("b1")).thenReturn(Optional.of(bidder));

        assertThrows(InvalidBidException.class, () -> biddingService.registerAutoBid(auction, bidder, 1500, 100));
    }

    @Test
    void processOutbidUnfreezeSuccess() throws Exception {
        Seller seller = new Seller("s1", now, "seller", "pass", "shop");
        Electronics item = new Electronics("i1", now, "Laptop", "desc", 1000, seller);
        
        Bidder b1 = new Bidder("b1", now, "b1", "pass", 2000.0);
        Bidder b2 = new Bidder("b2", now, "b2", "pass", 2000.0);
        
        Auction auction = new Auction("a1", now, seller, item, AuctionStatus.RUNNING, 1000, now, now.plusHours(1));
        
        // 1. b1 bids 1200
        when(userRepo.findById("b1")).thenReturn(Optional.of(b1));
        when(autoBidRepo.findByAuctionId("a1")).thenReturn(new ArrayList<>());
        biddingService.placeBid(auction, b1, 1200);
        
        // 2. b2 bids 1500
        when(userRepo.findById("b2")).thenReturn(Optional.of(b2));
        biddingService.placeBid(auction, b2, 1500);
        
        // 3. process unfreeze for b1
        Bidder unfrozen = biddingService.processOutbidUnfreeze();
        
        assertNotNull(unfrozen);
        assertEquals("b1", unfrozen.getId());
        assertEquals(0.0, b1.getFrozenBalance());
        assertEquals(2000.0, b1.getAvailableBalance());
    }

    @Test
    void processOutbidUnfreezeWithAutoBidDoesNotUnfreeze() throws Exception {
        Seller seller = new Seller("s1", now, "seller", "pass", "shop");
        Electronics item = new Electronics("i1", now, "Laptop", "desc", 1000, seller);
        
        Bidder b1 = new Bidder("b1", now, "b1", "pass", 2000.0, 1500.0); // b1 has autobid
        Bidder b2 = new Bidder("b2", now, "b2", "pass", 2000.0);
        
        Auction auction = new Auction("a1", now, seller, item, AuctionStatus.RUNNING, 1000, now, now.plusHours(1));
        BidTransaction bt1 = new BidTransaction("bt1", now, b1, auction, 1200);
        auction.injectBid(bt1);
        
        // Simulate b2 outbidding b1
        when(userRepo.findById("b2")).thenReturn(Optional.of(b2));
        when(userRepo.findById("b1")).thenReturn(Optional.of(b1));
        
        // Setup state for unfreeze
        // We need to trigger placeBid for b2 first
        when(autoBidRepo.findByAuctionId("a1")).thenReturn(new ArrayList<>());
        biddingService.placeBid(auction, b2, 1500);
        
        // Mock that b1 HAS an autobid active
        com.auction.model.AutoBid ab = new com.auction.model.AutoBid("ab1", "a1", "b1", 1500, 100, now);
        when(autoBidRepo.findByAuctionId("a1")).thenReturn(java.util.List.of(ab));
        
        Bidder unfrozen = biddingService.processOutbidUnfreeze();
        
        assertNotNull(unfrozen);
        assertEquals("b1", unfrozen.getId());
        // Should NOT unfreeze because of autobid
        assertEquals(1500.0, b1.getFrozenBalance());
    }
}
