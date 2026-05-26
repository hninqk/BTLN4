package com.auction.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.auction.model.*;
import com.auction.repository.*;
import com.auction.util.AppConfig;
import com.auction.util.TimeSyncManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class AuctionServiceTest {

    private MockedStatic<AppConfig> mockedConfig;
    private MockedStatic<TimeSyncManager> mockedTime;
    private LocalDateTime nowTime = LocalDateTime.of(2025, 1, 1, 12, 0);

    @BeforeEach
    void setUp() {
        mockedConfig = mockStatic(AppConfig.class);
        mockedConfig.when(AppConfig::jdbcUrl).thenReturn("jdbc:postgresql://localhost:5432/db");
        
        mockedTime = mockStatic(TimeSyncManager.class);
        mockedTime.when(TimeSyncManager::getNow).thenReturn(nowTime);
    }

    @AfterEach
    void tearDown() {
        mockedConfig.close();
        mockedTime.close();
    }

    @Test
    void getAllAuctions() {
        Admin dummyAdmin = new Admin("admin", "pass");
        Auction dummyAuction = new Auction(null, null, nowTime);
        
        try (MockedConstruction<JdbcAuctionRepository> mockedAuctionRepo = mockConstruction(JdbcAuctionRepository.class,
                (mock, context) -> {
                    List<Auction> list = new ArrayList<>();
                    list.add(dummyAuction);
                    when(mock.findAll()).thenReturn(list);
                });
             MockedConstruction<JdbcUserRepository> mockedUserRepo = mockConstruction(JdbcUserRepository.class,
                (mock, context) -> {
                    when(mock.findAll()).thenReturn(List.of(dummyAdmin));
                });
             MockedConstruction<JdbcBidRepository> mockedBidRepo = mockConstruction(JdbcBidRepository.class)) {
            
            AuctionService service = createNewInstance();
            List<Auction> result = service.getAllAuctions();
            assertEquals(1, result.size());
        }
    }

    @Test
    void createAuction() {
        Admin dummyAdmin = new Admin("admin", "pass");
        
        try (MockedConstruction<JdbcAuctionRepository> mockedAuctionRepo = mockConstruction(JdbcAuctionRepository.class);
             MockedConstruction<JdbcUserRepository> mockedUserRepo = mockConstruction(JdbcUserRepository.class,
                (mock, context) -> {
                    when(mock.findAll()).thenReturn(List.of(dummyAdmin));
                });
             MockedConstruction<JdbcBidRepository> mockedBidRepo = mockConstruction(JdbcBidRepository.class)) {
            
            AuctionService service = createNewInstance();
            Seller seller = new Seller("seller", "pass", "shop");
            Electronics item = new Electronics("Laptop", "desc", 1000, seller);
            
            Auction result = service.createAuction(seller, item, nowTime.plusDays(1));
            
            assertNotNull(result);
            assertEquals(seller, result.getSeller());
            assertEquals(item, result.getItem());
            assertEquals(AuctionStatus.PENDING, result.getStatus());
        }
    }

    private AuctionService createNewInstance() {
        try {
            java.lang.reflect.Constructor<AuctionService> constructor = AuctionService.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
