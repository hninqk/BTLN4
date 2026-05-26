package com.auction.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

public class ModelTest {

    @Test
    void testAdmin() {
        Admin admin = new Admin("admin", "pass");
        assertEquals("admin", admin.getUsername());
        assertEquals("pass", admin.getPassword());
        assertEquals("Admin", admin.getRole());
        assertNotNull(admin.getId());
        assertNotNull(admin.getCreatedAt());

        Admin admin2 = new Admin("id1", LocalDateTime.now(), "admin2", "pass2");
        assertEquals("id1", admin2.getId());
        assertEquals("Admin", admin2.getRole());
    }

    @Test
    void testSeller() {
        Seller seller = new Seller("seller", "pass", "My Shop");
        assertEquals("My Shop", seller.getShopName());
        assertEquals("Seller", seller.getRole());
        
        seller.setShopName("New Shop");
        assertEquals("New Shop", seller.getShopName());

        seller.setUsername("newuser");
        seller.setPassword("newpass");
        assertEquals("newuser", seller.getUsername());
        assertEquals("newpass", seller.getPassword());
    }

    @Test
    void testBidderBalance() {
        Bidder bidder = new Bidder("bidder", "pass", 1000.0);
        assertEquals(1000.0, bidder.getAccountBalance());
        assertEquals(0.0, bidder.getFrozenBalance());
        assertEquals(1000.0, bidder.getAvailableBalance());

        bidder.addBalance(500.0);
        assertEquals(1500.0, bidder.getAccountBalance());

        assertTrue(bidder.freezeFunds(500.0));
        assertEquals(1500.0, bidder.getAccountBalance());
        assertEquals(500.0, bidder.getFrozenBalance());
        assertEquals(1000.0, bidder.getAvailableBalance());

        assertFalse(bidder.freezeFunds(1100.0));
        assertFalse(bidder.freezeFunds(-10.0));

        bidder.unfreezeFunds(200.0);
        assertEquals(300.0, bidder.getFrozenBalance());
        bidder.unfreezeFunds(-10.0);
        assertEquals(300.0, bidder.getFrozenBalance());

        assertTrue(bidder.deductBalance(500.0));
        assertEquals(1000.0, bidder.getAccountBalance());
        assertEquals(0.0, bidder.getFrozenBalance()); // It was 300, deduct 500 makes it 0

        assertFalse(bidder.deductBalance(2000.0));
        assertFalse(bidder.deductBalance(-10.0));
    }

    @Test
    void testAutoBid() {
        LocalDateTime now = LocalDateTime.now();
        AutoBid autoBid = new AutoBid("id1", "auc1", "bid1", 500.0, 10.0, now);
        assertEquals("id1", autoBid.getId());
        assertEquals("auc1", autoBid.getAuctionId());
        assertEquals("bid1", autoBid.getBidderId());
        assertEquals(500.0, autoBid.getMaxBid());
        assertEquals(10.0, autoBid.getIncrement());
        assertEquals(now, autoBid.getCreatedAt());

        autoBid.setId("id2");
        autoBid.setAuctionId("auc2");
        autoBid.setBidderId("bid2");
        autoBid.setMaxBid(600.0);
        autoBid.setIncrement(20.0);
        LocalDateTime later = now.plusHours(1);
        autoBid.setCreatedAt(later);

        assertEquals("id2", autoBid.getId());
        assertEquals("auc2", autoBid.getAuctionId());
        assertEquals("bid2", autoBid.getBidderId());
        assertEquals(600.0, autoBid.getMaxBid());
        assertEquals(20.0, autoBid.getIncrement());
        assertEquals(later, autoBid.getCreatedAt());
    }
    
    @Test
    void testAuctionStatus() {
        assertEquals("PENDING", AuctionStatus.PENDING.name());
        assertEquals("OPEN", AuctionStatus.OPEN.name());
        assertEquals("RUNNING", AuctionStatus.RUNNING.name());
        assertEquals("CLOSED", AuctionStatus.CLOSED.name());
        assertEquals("CANCELED", AuctionStatus.CANCELED.name());
    }
}
