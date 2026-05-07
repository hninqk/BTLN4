package com.auction.manager;

import com.auction.model.Auction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionManagerTest {

    private AuctionManager manager;

    @BeforeEach
    void setUp() {
        manager = AuctionManager.getInstance();
        manager.getAuctions().clear();
    }

    @AfterEach
    void tearDown() {
        manager.getAuctions().clear();
    }

    @Test
    @DisplayName("Kiểm tra Singleton: Luôn trả về cùng một instance")
    void testSingletonInstance() {
        AuctionManager instance1 = AuctionManager.getInstance();
        AuctionManager instance2 = AuctionManager.getInstance();
        assertSame(instance1, instance2, "Cả 2 biến phải trỏ cùng về một đối tượng");
    }

    @Test
    @DisplayName("Tạo phiên đấu giá: Auction được thêm vào danh sách")
    void testCreateAuction() {
        Auction auction = new Auction(null, null, LocalDateTime.now().plusHours(1));
        manager.createAuction(auction);

        List<Auction> auctions = manager.getAuctions();
        assertEquals(1, auctions.size(), "Danh sách phải có 1 phần tử");
        assertEquals(auction, auctions.get(0), "Auction lấy ra phải khớp với auction đã thêm");
    }

    @Test
    @DisplayName("Tìm kiếm phiên đấu giá theo ID: Trả về đúng hoặc null")
    void testFindAuctionById() {
        Auction auction1 = new Auction(null, null, LocalDateTime.now().plusHours(1));
        Auction auction2 = new Auction(null, null, LocalDateTime.now().plusHours(2));

        manager.createAuction(auction1);
        manager.createAuction(auction2);

        assertEquals(auction1, manager.findAuctionById(auction1.getId()), "Phải tìm thấy Auction 1");
    }

    @Test
    @DisplayName("Xóa phiên đấu giá: Bị loại khỏi danh sách nếu ID tồn tại")
    void testRemoveAuction() {
        Auction auction = new Auction(null, null, LocalDateTime.now().plusHours(1));
        manager.createAuction(auction);

        assertEquals(1, manager.getAuctions().size());

        manager.removeAuction(auction.getId());
        assertEquals(0, manager.getAuctions().size(), "Danh sách phải rỗng sau khi xóa");
    }
}
