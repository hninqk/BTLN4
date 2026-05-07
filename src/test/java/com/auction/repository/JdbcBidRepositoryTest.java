package com.auction.repository;

import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

public class JdbcBidRepositoryTest {

    private JdbcBidRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcBidRepository();
    }

    @Test
    void testSaveAndVerifyInDBeaver() {
        Bidder bidder = new Bidder("JUnit_Tester", "test@unit.com", "pass", 1000.0);
        Auction auction = new Auction(null, null, LocalDateTime.now().plusDays(1));

        String uniqueId = "test-" + System.currentTimeMillis();
        BidTransaction tx = new BidTransaction(uniqueId, LocalDateTime.now(), bidder, auction, 99.99);

        assertDoesNotThrow(() -> {
            repository.save(tx);
        }, "The JDBC repository should save without throwing an exception");

        System.out.println("Test finished! Now go to DBeaver and look for ID: " + uniqueId);
    }
}