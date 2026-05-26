package com.auction.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.auction.model.Auction;
import com.auction.model.AutoBid;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.Electronics;
import com.auction.model.AuctionStatus;
import com.auction.repository.JdbcAutoBidRepository;
import com.auction.repository.JdbcUserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProxyBiddingEngineTest {

    private JdbcAutoBidRepository autoBidRepo;
    private JdbcUserRepository userRepo;
    private BiddingService biddingService;
    private ProxyBiddingEngine proxyBiddingEngine;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        autoBidRepo = mock(JdbcAutoBidRepository.class);
        userRepo = mock(JdbcUserRepository.class);
        biddingService = mock(BiddingService.class);
        proxyBiddingEngine = new ProxyBiddingEngine(autoBidRepo, userRepo, biddingService);
        now = LocalDateTime.of(2025, 1, 1, 12, 0);
    }

    @Test
    void resolveBiddingWarSingleBidder() throws Exception {
        Seller seller = new Seller("s1", now, "seller", "pass", "shop");
        Electronics item = new Electronics("i1", now, "Laptop", "desc", 1000, seller);
        Auction auction = new Auction("a1", now, seller, item, AuctionStatus.RUNNING, 1000, now, now.plusHours(1));
        
        Bidder bidder = new Bidder("b1", now, "bidder", "pass", 2000.0);
        AutoBid ab = new AutoBid("ab1", "a1", "b1", 2000, 100, now);
        
        List<AutoBid> activeBids = new ArrayList<>();
        activeBids.add(ab);
        
        when(autoBidRepo.findByAuctionId("a1")).thenReturn(activeBids);
        when(userRepo.findById("b1")).thenReturn(Optional.of(bidder));
        
        when(biddingService.placeBid(any(), any(), anyDouble())).thenAnswer(invocation -> {
            Auction a = invocation.getArgument(0);
            Bidder b = invocation.getArgument(1);
            double amount = invocation.getArgument(2);
            BidTransaction bt = new BidTransaction("bt", now, b, a, amount);
            a.injectBid(bt); // This updates auction.getWinner()
            return bt;
        });
        
        AuctionService.AutoBidResult result = proxyBiddingEngine.resolveBiddingWar(auction);
        
        assertNotNull(result);
        assertFalse(result.newBids.isEmpty());
        assertEquals(1, result.newBids.size());
    }

    @Test
    void resolveBiddingWarTwoBidders() throws Exception {
        Seller seller = new Seller("s1", now, "seller", "pass", "shop");
        Electronics item = new Electronics("i1", now, "Laptop", "desc", 1000, seller);
        Auction auction = new Auction("a1", now, seller, item, AuctionStatus.RUNNING, 1000, now, now.plusHours(1));
        
        Bidder b1 = new Bidder("b1", now, "bidder1", "pass", 2000.0);
        Bidder b2 = new Bidder("b2", now, "bidder2", "pass", 2000.0);
        
        AutoBid ab1 = new AutoBid("ab1", "a1", "b1", 2000, 100, now);
        AutoBid ab2 = new AutoBid("ab2", "a1", "b2", 1500, 100, now.plusSeconds(1));
        
        List<AutoBid> activeBids = new ArrayList<>();
        activeBids.add(ab1);
        activeBids.add(ab2);
        
        when(autoBidRepo.findByAuctionId("a1")).thenReturn(activeBids);
        when(userRepo.findById("b1")).thenReturn(Optional.of(b1));
        when(userRepo.findById("b2")).thenReturn(Optional.of(b2));
        
        when(biddingService.placeBid(any(), any(), anyDouble())).thenAnswer(invocation -> {
            Auction a = invocation.getArgument(0);
            Bidder b = invocation.getArgument(1);
            double amount = invocation.getArgument(2);
            BidTransaction bt = new BidTransaction("bt", now, b, a, amount);
            a.injectBid(bt);
            return bt;
        });
        
        AuctionService.AutoBidResult result = proxyBiddingEngine.resolveBiddingWar(auction);
        
        assertNotNull(result);
        verify(biddingService, atLeastOnce()).placeBid(eq(auction), eq(b1), anyDouble());
        verify(biddingService, atLeastOnce()).placeBid(eq(auction), eq(b2), anyDouble());
    }
}
