package com.auction.server;

import static org.junit.jupiter.api.Assertions.*;

import com.auction.model.*;
import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuctionSerializerTest {

    @Test
    void auctionToJsonAndBack() {
        LocalDateTime now = LocalDateTime.of(2025, 1, 1, 12, 0);
        Seller seller = new Seller("s1", now, "seller", "pass", "shop");
        Electronics item = new Electronics("i1", now, "Laptop", "desc", 1000, seller);
        Auction auction = new Auction("a1", now, seller, item, AuctionStatus.RUNNING, 1000, now, now.plusHours(1));
        
        JsonObject json = AuctionSerializer.auctionToJson(auction);
        
        assertNotNull(json);
        assertEquals("a1", json.get("auctionId").getAsString());
        assertEquals("Laptop", json.get("itemName").getAsString());
        
        Auction back = AuctionSerializer.auctionFromJson(json);
        assertNotNull(back);
        assertEquals(auction.getId(), back.getId());
        assertEquals(auction.getHighestBid(), back.getHighestBid());
        assertEquals(auction.getStatus(), back.getStatus());
    }

    @Test
    void allItemTypesToJson() {
        LocalDateTime now = LocalDateTime.now();
        Seller seller = new Seller("s1", "pass", "shop");
        
        Art art = new Art("i1", now, "Art", "desc", 100, seller, "Artist");
        JsonObject artJson = AuctionSerializer.auctionToJson(new Auction(seller, art, now));
        assertEquals("Nghệ thuật", artJson.get("category").getAsString());
        
        Vehicle veh = new Vehicle("i2", now, "Car", "desc", 100, seller);
        JsonObject vehJson = AuctionSerializer.auctionToJson(new Auction(seller, veh, now));
        assertEquals("Xe cộ", vehJson.get("category").getAsString());
        
        Electronics el = new Electronics("i3", now, "Phone", "desc", 100, seller);
        JsonObject elJson = AuctionSerializer.auctionToJson(new Auction(seller, el, now));
        assertEquals("Điện tử", elJson.get("category").getAsString());
    }

    @Test
    void allUserTypesToJson() {
        Admin admin = new Admin("admin", "pass");
        assertEquals("Admin", AuctionSerializer.userToJson(admin).get("role").getAsString());
        
        Seller seller = new Seller("seller", "pass", "shop");
        assertEquals("Seller", AuctionSerializer.userToJson(seller).get("role").getAsString());
        
        Bidder bidder = new Bidder("bidder", "pass", 100.0);
        assertEquals("Bidder", AuctionSerializer.userToJson(bidder).get("role").getAsString());
    }

    @Test
    void testNulls() {
        assertNull(AuctionSerializer.userFromJson(null));
        assertNull(AuctionSerializer.auctionFromJson(null));
        assertNotNull(AuctionSerializer.userToJson(null)); // Returns empty JsonObject
        assertNotNull(AuctionSerializer.auctionToJson(null)); // Returns empty JsonObject
    }
}
