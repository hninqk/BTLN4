package com.auction.model;

import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionTest {

    private Auction auction;

    @BeforeEach
    void setUp() {
        auction = new Auction(null, null, LocalDateTime.now().plusHours(2));
    }

    private BidTransaction createBid(double amount) {
        return new BidTransaction(
                "bid_test_01", 
                LocalDateTime.now(), 
                null, 
                this.auction, 
                amount
        );
    }

    @Test
    @DisplayName("Đặt giá hợp lệ: Giá được cập nhật thành công")
    void testPlaceValidBid() throws Exception {
        auction.startAuction(); // Chuyển status sang RUNNING
        
        // Đặt giá 100.0
        BidTransaction validBid = createBid(100.0);
        auction.placeBid(validBid);

        // Đặt tiếp 150.0 (hợp lệ)
        assertDoesNotThrow(() -> auction.placeBid(createBid(150.0))); 
    }

    @Test
    @DisplayName("Ngoại lệ: Đặt giá thấp hơn giá hiện tại hoặc giá trị âm")
    void testPlaceBidLowerThanCurrentOrNegative() throws Exception {
        auction.startAuction();
        auction.placeBid(createBid(100.0)); // Giá hiện tại đang là 100

        // 1. Test đặt giá thấp hơn (90)
        Exception exception1 = assertThrows(InvalidBidException.class, () -> {
            auction.placeBid(createBid(90.0));
        });
        assertTrue(exception1.getMessage().contains("Bid must be higher"));

        // 2. Test đặt giá trị âm (-50)
        Exception exception2 = assertThrows(InvalidBidException.class, () -> {
            auction.placeBid(createBid(-50.0));
        });
        assertTrue(exception2.getMessage().contains("Bid must be positive"));
    }

    @Test
    @DisplayName("Ngoại lệ: Đấu giá khi phiên chưa mở hoặc đã kết thúc")
    void testPlaceBidInvalidStatus() {
        // 1. Thử đặt giá khi mới khởi tạo (Trạng thái OPEN, chưa RUNNING)
        assertThrows(InvalidStatusException.class, () -> {
            auction.placeBid(createBid(100.0));
        });

        // 2. Chuyển sang RUNNING rồi kết thúc phiên
        assertDoesNotThrow(() -> auction.startAuction());
        assertDoesNotThrow(() -> auction.finishAuction()); // Chuyển sang PAID

        // Thử đặt giá sau khi kết thúc
        assertThrows(InvalidStatusException.class, () -> {
            auction.placeBid(createBid(200.0));
        });
    }

    @Test
    @DisplayName("Kết thúc phiên đấu giá: Chuyển trạng thái thành công")
    void testFinishAuction() throws Exception {
        auction.startAuction();
        auction.finishAuction();
        
        // Không thể start hoặc finish một phiên đã PAID
        assertThrows(InvalidStatusException.class, () -> auction.startAuction());
        assertThrows(InvalidStatusException.class, () -> auction.finishAuction());
    }
}