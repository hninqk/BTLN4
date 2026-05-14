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
        auction.startAuction(); 
        
        // Đặt giá lần 1 (100.0)
        auction.placeBid(createBid(100.0));

        // Đặt giá lần 2 (150.0 - hợp lệ)
        assertDoesNotThrow(() -> auction.placeBid(createBid(150.0))); 
    }

    @Test
    @DisplayName("Ngoại lệ: Đặt giá bằng, thấp hơn, bằng 0 hoặc số âm -> Ném InvalidBidException")
    void testPlaceInvalidBids() throws Exception {
        auction.startAuction();
        auction.placeBid(createBid(100.0)); // Thiết lập giá hiện tại là 100.0

        double[] invalidAmounts = {100.0, 99.9, 0.0, -50.0};

        for (double amount : invalidAmounts) {
            assertThrows(InvalidBidException.class, () -> {
                auction.placeBid(createBid(amount));
            }, "Bid must be higher than the current bid"); 
        }
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

        // 3. Thử đặt giá sau khi kết thúc
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