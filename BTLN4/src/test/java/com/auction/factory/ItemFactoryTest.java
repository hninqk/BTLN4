package com.auction.factory;

import com.auction.model.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

public class ItemFactoryTest {

    @Test
    void testArtFactory() {
        ArtFactory factory = new ArtFactory("Leonardo da Vinci", 1503);
        User owner = new Bidder("user1", LocalDateTime.now(), "username", "password", 1000.0, 0.0);
        Item item = factory.createItem("Mona Lisa", "Famous painting", 1000000.0, owner);

        assertTrue(item instanceof Art);
        assertEquals("Mona Lisa", item.getName());
        assertEquals("Famous painting", item.getDescription());
        assertEquals(1000000.0, item.getStartingPrice());
        assertEquals(owner, item.getOwner());
        assertEquals("Leonardo da Vinci", ((Art) item).getArtistName());
    }

    @Test
    void testElectronicsFactory() {
        ElectronicsFactory factory = new ElectronicsFactory(24);
        User owner = new Bidder("user1", LocalDateTime.now(), "username", "password", 1000.0, 0.0);
        Item item = factory.createItem("Smartphone", "Latest model", 800.0, owner);

        assertTrue(item instanceof Electronics);
        assertEquals("Smartphone", item.getName());
        assertEquals("Latest model", item.getDescription());
        assertEquals(800.0, item.getStartingPrice());
        assertEquals(owner, item.getOwner());
    }

    @Test
    void testVehicleFactory() {
        VehicleFactory factory = new VehicleFactory(15000.0, 2022);
        User owner = new Bidder("user1", LocalDateTime.now(), "username", "password", 1000.0, 0.0);
        Item item = factory.createItem("Tesla Model 3", "Electric car", 45000.0, owner);

        assertTrue(item instanceof Vehicle);
        assertEquals("Tesla Model 3", item.getName());
        assertEquals("Electric car", item.getDescription());
        assertEquals(45000.0, item.getStartingPrice());
        assertEquals(owner, item.getOwner());
    }
}
