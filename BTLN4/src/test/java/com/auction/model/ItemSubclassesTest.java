package com.auction.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

class ItemSubclassesTest {

    @Test
    void electronicsTest() {
        Seller seller = new Seller("seller", "pass", "shop");
        Electronics e = new Electronics("Laptop", "desc", 1000, seller);
        
        assertEquals("Electronics", e.getCategoryInfo());
        assertEquals("Điện tử", e.getCategory());
        assertEquals("Laptop", e.getName());
        assertEquals("desc", e.getDescription());
        assertEquals(1000, e.getStartingPrice());
        assertEquals(seller, e.getOwner());
    }

    @Test
    void artTest() {
        Seller seller = new Seller("seller", "pass", "shop");
        Art a = new Art("Painting", "desc", 500, seller, "artist");
        
        assertEquals("Art", a.getCategoryInfo());
        assertEquals("Nghệ thuật", a.getCategory());
        assertEquals("artist", a.getArtistName());
    }

    @Test
    void vehicleTest() {
        Seller seller = new Seller("seller", "pass", "shop");
        Vehicle v = new Vehicle("Car", "desc", 50000, seller);
        
        assertEquals("Vehicle", v.getCategoryInfo());
        assertEquals("Xe cộ", v.getCategory());
    }
}
