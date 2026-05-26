package com.auction.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BidderTest {

    private Bidder bidder;

    @BeforeEach
    void setUp() {
        bidder = new Bidder("testuser", "password123", 1000.0);
    }

    @Test
    @DisplayName("Thêm số dư hợp lệ: Số dư tăng lên")
    void testAddBalanceValid() {
        bidder.AddBalance(500.0);
        assertEquals(1500.0, bidder.getAccountBalance(), "Số dư phải được cộng thêm");
    }

    @Test
    @DisplayName("Thêm số dư không hợp lệ (số âm): Số dư không đổi")
    void testAddBalanceInvalid() {
        bidder.AddBalance(-200.0);
        assertEquals(1000.0, bidder.getAccountBalance(), "Số dư không được thay đổi khi cộng số âm");
    }

    @Test
    @DisplayName("Trừ số dư hợp lệ: Số dư giảm xuống và trả về true")
    void testDeductBalanceValid() {
        boolean result = bidder.deductBalance(300.0);
        assertTrue(result, "Phải trả về true khi trừ thành công");
        assertEquals(700.0, bidder.getAccountBalance(), "Số dư phải được trừ đi");
    }

    @Test
    @DisplayName("Trừ số dư không hợp lệ (lớn hơn số dư hiện có): Số dư không đổi và trả về false")
    void testDeductBalanceInsufficient() {
        boolean result = bidder.deductBalance(1500.0);
        assertFalse(result, "Phải trả về false khi không đủ số dư");
        assertEquals(1000.0, bidder.getAccountBalance(), "Số dư phải được giữ nguyên");
    }

    @Test
    @DisplayName("Trừ số dư không hợp lệ (số âm): Số dư không đổi và trả về false")
    void testDeductBalanceNegative() {
        boolean result = bidder.deductBalance(-100.0);
        assertFalse(result, "Phải trả về false khi trừ số âm");
        assertEquals(1000.0, bidder.getAccountBalance(), "Số dư phải được giữ nguyên");
    }

    @Test
    @DisplayName("Đóng băng tiền: frozenBalance tăng và availableBalance giảm")
    void testFreezeFunds() {
        assertTrue(bidder.freezeFunds(300.0));
        assertEquals(300.0, bidder.getFrozenBalance());
        assertEquals(700.0, bidder.getAvailableBalance());
        assertEquals(1000.0, bidder.getAccountBalance());
    }

    @Test
    @DisplayName("Đóng băng tiền vượt quá số dư khả dụng: trả về false")
    void testFreezeFundsInsufficient() {
        assertFalse(bidder.freezeFunds(1100.0));
        assertEquals(0.0, bidder.getFrozenBalance());
    }

    @Test
    @DisplayName("Hoàn trả tiền đóng băng: frozenBalance giảm và availableBalance tăng")
    void testUnfreezeFunds() {
        bidder.freezeFunds(500.0);
        bidder.unfreezeFunds(200.0);
        assertEquals(300.0, bidder.getFrozenBalance());
        assertEquals(700.0, bidder.getAvailableBalance());
    }

    @Test
    @DisplayName("Deduct balance should also reduce frozen balance")
    void testDeductBalanceReducesFrozen() {
        bidder.freezeFunds(500.0);
        assertTrue(bidder.deductBalance(500.0));
        assertEquals(500.0, bidder.getAccountBalance());
        assertEquals(0.0, bidder.getFrozenBalance());
        assertEquals(500.0, bidder.getAvailableBalance());
    }
}
