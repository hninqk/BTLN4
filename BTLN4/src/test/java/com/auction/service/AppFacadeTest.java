package com.auction.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.auction.client.ApiClient;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.User;
import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class AppFacadeTest {

    private ApiClient mockApi;
    private MockedStatic<ApiClient> mockedApiStatic;
    private AppFacade appFacade;

    @BeforeEach
    void setUp() {
        mockApi = mock(ApiClient.class);
        mockedApiStatic = mockStatic(ApiClient.class);
        mockedApiStatic.when(ApiClient::getInstance).thenReturn(mockApi);
        appFacade = createNewInstance();
    }

    @AfterEach
    void tearDown() {
        mockedApiStatic.close();
    }

    @Test
    void loginSuccess() throws Exception {
        JsonObject userJson = new JsonObject();
        userJson.addProperty("id", "u1");
        userJson.addProperty("username", "alice");
        userJson.addProperty("role", "Bidder");
        userJson.addProperty("createdAt", "2025-01-01T12:00:00");
        userJson.addProperty("balance", 1000.0);
        
        when(mockApi.postSync(eq("/api/login"), any())).thenReturn(userJson.toString());
        when(mockApi.parseObject(anyString())).thenReturn(userJson);
        when(mockApi.isError(any())).thenReturn(false);
        
        Optional<User> result = appFacade.login("alice", "123");
        
        assertTrue(result.isPresent());
        assertEquals("alice", result.get().getUsername());
    }

    @Test
    void registerSuccess() throws Exception {
        JsonObject userJson = new JsonObject();
        userJson.addProperty("id", "u2");
        userJson.addProperty("username", "bob");
        userJson.addProperty("role", "Bidder");
        userJson.addProperty("createdAt", LocalDateTime.now().toString());
        
        when(mockApi.postSync(eq("/api/register"), any())).thenReturn(userJson.toString());
        when(mockApi.parseObject(anyString())).thenReturn(userJson);
        when(mockApi.isError(any())).thenReturn(false);
        
        Optional<User> result = appFacade.register("bob", "123", "Bidder");
        assertTrue(result.isPresent());
        assertEquals("bob", result.get().getUsername());
    }

    @Test
    void testGetAllUsers() throws Exception {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        JsonObject u1 = new JsonObject();
        u1.addProperty("id", "u1");
        u1.addProperty("username", "u1");
        u1.addProperty("role", "Admin");
        u1.addProperty("createdAt", LocalDateTime.now().toString());
        array.add(u1);
        
        when(mockApi.getSync("/api/users")).thenReturn(array.toString());
        when(mockApi.parseArray(anyString())).thenReturn(array);
        
        List<User> result = appFacade.getAllUsers();
        assertEquals(1, result.size());
    }

    @Test
    void testTopupBalance() throws Exception {
        Bidder bidder = new Bidder("id1", LocalDateTime.now(), "alice", "pass", 100.0, 0.0);
        JsonObject resultJson = new JsonObject();
        resultJson.addProperty("id", "id1");
        resultJson.addProperty("username", "alice");
        resultJson.addProperty("role", "Bidder");
        resultJson.addProperty("balance", 200.0);
        resultJson.addProperty("createdAt", LocalDateTime.now().toString());
        
        when(mockApi.postSync(eq("/api/users/topup"), any())).thenReturn(resultJson.toString());
        when(mockApi.parseObject(anyString())).thenReturn(resultJson);
        when(mockApi.isError(any())).thenReturn(false);
        
        Bidder updated = appFacade.topupBalance(bidder, 100.0);
        assertEquals(200.0, updated.getAccountBalance());
    }

    @Test
    void testFindAuctionById() throws Exception {
        JsonObject auctionJson = new JsonObject();
        auctionJson.addProperty("auctionId", "auc1");
        auctionJson.addProperty("itemName", "Item1");
        auctionJson.addProperty("sellerId", "s1");
        auctionJson.addProperty("sellerUsername", "seller1");
        auctionJson.addProperty("status", "OPEN");
        auctionJson.addProperty("highestBid", 500.0);
        auctionJson.addProperty("endTime", LocalDateTime.now().toString());
        
        when(mockApi.getSync("/api/auctions/auc1")).thenReturn(auctionJson.toString());
        when(mockApi.parseObject(anyString())).thenReturn(auctionJson);
        when(mockApi.isError(any())).thenReturn(false);
        
        Optional<Auction> result = appFacade.findAuctionById("auc1");
        assertTrue(result.isPresent());
        assertEquals("auc1", result.get().getId());
    }

    private AppFacade createNewInstance() {
        try {
            java.lang.reflect.Constructor<AppFacade> constructor = AppFacade.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
