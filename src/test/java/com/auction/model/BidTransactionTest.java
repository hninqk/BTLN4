package com.auction.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class BidTransactionTest {

    @Test
    @DisplayName("Khởi tạo BidTransaction: Các thuộc tính được gán đúng")
    void testBidTransactionInitialization() {
        Bidder bidder = new Bidder("user1", "pass", 500);
        Auction auction = new Auction(null, null, LocalDateTime.now().plusHours(1));
        double amount = 150.0;
        
        BidTransaction bid = new BidTransaction("bid123", LocalDateTime.now(), bidder, auction, amount);

        assertEquals(bidder, bid.getBidder(), "Bidder phải khớp");
        assertEquals(auction, bid.getAuction(), "Auction phải khớp");
        assertEquals(amount, bid.getAmount(), "Số tiền phải khớp");
        assertNotNull(bid.getTimestamp(), "Timestamp không được null");
        assertEquals("bid123", bid.getId(), "ID phải khớp");
    }
}
