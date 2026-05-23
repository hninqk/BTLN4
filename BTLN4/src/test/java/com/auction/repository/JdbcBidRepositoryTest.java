package com.auction.repository;

import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;
import com.auction.model.Electronics;
import com.auction.model.Seller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class JdbcBidRepositoryTest {

    private JdbcBidRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcBidRepository();
    }

    @Test
    void testSaveAndVerifyInDBeaver() {
        assumeTrue(System.getenv("JDBC_DATABASE_URL") != null || System.getenv("DATABASE_URL") != null,
                "Render PostgreSQL URL is required for repository integration tests.");
        long suffix = System.currentTimeMillis();
        Bidder bidder = new Bidder("JUnit_Tester_" + suffix, "pass", 1000.0);
        Seller seller = new Seller("JUnit_Seller_" + suffix, "pass", "JUnit Shop");
        Electronics item = new Electronics("JUnit item", "Repository test item", 10.0, seller);
        Auction auction = new Auction(seller, item, LocalDateTime.now().plusDays(1));
        new JdbcUserRepository().save(bidder);
        new JdbcUserRepository().save(seller);
        new JdbcAuctionRepository().save(auction);

        String uniqueId = "test-" + suffix;
        BidTransaction tx = new BidTransaction(uniqueId, LocalDateTime.now(), bidder, auction, 99.99);

        assertDoesNotThrow(() -> {
            repository.save(tx);
        }, "The JDBC repository should save without throwing an exception");

        System.out.println("Bid repository save verified for ID: " + uniqueId);
    }
}
