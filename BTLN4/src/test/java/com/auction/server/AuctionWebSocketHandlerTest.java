package com.auction.server;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import com.auction.model.*;
import com.auction.service.AuctionService;
import com.auction.service.UserService;
import com.auction.util.AppConfig;
import com.auction.util.TimeSyncManager;
import com.google.gson.JsonObject;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import java.time.LocalDateTime;
import java.util.Optional;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class AuctionWebSocketHandlerTest {

    private AuctionService auctionService;
    private AuctionWebSocketHandler handler;
    private MockedStatic<AppConfig> mockedConfig;
    private MockedStatic<TimeSyncManager> mockedTime;
    private MockedStatic<UserService> mockedUserServiceStatic;
    private UserService userService;

    @BeforeEach
    void setUp() {
        mockedConfig = mockStatic(AppConfig.class);
        mockedConfig.when(AppConfig::jdbcUrl).thenReturn("jdbc:postgresql://localhost:5432/db");
        
        mockedTime = mockStatic(TimeSyncManager.class);
        mockedTime.when(TimeSyncManager::getNow).thenReturn(LocalDateTime.of(2025, 1, 1, 12, 0));

        auctionService = mock(AuctionService.class);
        userService = mock(UserService.class);
        mockedUserServiceStatic = mockStatic(UserService.class);
        mockedUserServiceStatic.when(UserService::getInstance).thenReturn(userService);

        handler = new AuctionWebSocketHandler(auctionService);
    }

    @AfterEach
    void tearDown() {
        mockedConfig.close();
        mockedTime.close();
        mockedUserServiceStatic.close();
    }

    @Test
    void onConnectAndClose() {
        WsContext ctx = mock(WsContext.class);
        when(ctx.sessionId()).thenReturn("s1");
        
        // Use reflection to call private methods
        invokePrivate(handler, "onConnect", ctx);
        invokePrivate(handler, "onClose", ctx);
        
        verify(ctx, atLeastOnce()).sessionId();
    }

    @Test
    void handlePlaceBidSuccess() throws Exception {
        WsMessageContext ctx = mock(WsMessageContext.class);
        Session session = mock(Session.class);
        
        // Use reflection to set the final 'session' field if necessary, 
        // but wait, WsContext in Javalin 5+ has session as a public final field.
        // Actually, mocking WsMessageContext might not be enough if it accesses .session directly.
        // Let's try to set it via reflection.
        try {
            java.lang.reflect.Field field = io.javalin.websocket.WsContext.class.getField("session");
            field.setAccessible(true);
            // Removing final modifier if needed, but normally we can just set it on a mock 
            // if we are not using a mock but a real object.
            // Since it's a mock, we should just mock the getter if it has one.
            // But session is a field.
        } catch (Exception ignored) {}

        when(session.isOpen()).thenReturn(true);
        // If the code uses ctx.session directly, we need a real object or a spy.
        // Let's use reflection to set the field on the mock.
        setFinalField(ctx, "session", session);
        
        JsonObject req = new JsonObject();
        req.addProperty("type", "PLACE_BID");
        req.addProperty("auctionId", "a1");
        req.addProperty("bidderId", "b1");
        req.addProperty("amount", 1500.0);
        
        when(ctx.message()).thenReturn(req.toString());
        
        Seller seller = new Seller("s1", LocalDateTime.now(), "seller", "pass", "shop");
        Electronics item = new Electronics("i1", LocalDateTime.now(), "Laptop", "desc", 1000, seller);
        Auction auction = new Auction("a1", LocalDateTime.now(), seller, item, AuctionStatus.RUNNING, 1000, null, LocalDateTime.now().plusHours(1));
        
        Bidder bidder = new Bidder("b1", LocalDateTime.now(), "bidder", "pass", 2000.0);
        
        when(auctionService.findById("a1")).thenReturn(Optional.of(auction));
        when(userService.findById("b1")).thenReturn(Optional.of(bidder));
        
        BidTransaction bid = new BidTransaction("bt1", LocalDateTime.now(), bidder, auction, 1500.0);
        when(auctionService.placeBid(any(), any(), anyDouble())).thenReturn(bid);
        when(auctionService.resolveBiddingWar(any())).thenReturn(new AuctionService.AutoBidResult());
        
        invokePrivate(handler, "onMessage", ctx);
        
        // Should broadcast BID_UPDATE
        // Since broadcastAll uses a set of sessions, we need to add a session first
        invokePrivate(handler, "onConnect", ctx);
        invokePrivate(handler, "onMessage", ctx);
        
        verify(ctx, atLeastOnce()).send(anyString());
    }

    @Test
    void handleAutoBidRegisterSuccess() throws Exception {
        WsMessageContext ctx = mock(WsMessageContext.class);
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        setFinalField(ctx, "session", session);
        
        Auction auction = new Auction("a1", LocalDateTime.now(), null, null, AuctionStatus.RUNNING, 1000.0, null, LocalDateTime.now().plusHours(1));
        when(auctionService.findById("a1")).thenReturn(Optional.of(auction));
        when(userService.findById("b1")).thenReturn(Optional.of(new Bidder("b1", LocalDateTime.now(), "bidder", "pass", 1000.0, 0.0)));

        JsonObject req = new JsonObject();
        req.addProperty("type", "REGISTER_AUTO_BID");
        req.addProperty("auctionId", "a1");
        req.addProperty("bidderId", "b1");
        req.addProperty("maxBid", 5000.0);
        req.addProperty("increment", 100.0);
        
        when(ctx.message()).thenReturn(req.toString());
        
        invokePrivate(handler, "onMessage", ctx);
        verify(auctionService).registerAutoBid(any(), any(), eq(5000.0), eq(100.0));
    }

    @Test
    void handleJoinAuction() throws Exception {
        WsMessageContext ctx = mock(WsMessageContext.class);
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        setFinalField(ctx, "session", session);
        when(ctx.sessionId()).thenReturn("s1");
        
        JsonObject req = new JsonObject();
        req.addProperty("type", "JOIN_AUCTION");
        req.addProperty("auctionId", "a1");
        req.addProperty("userId", "u1");
        
        when(ctx.message()).thenReturn(req.toString());
        
        invokePrivate(handler, "onConnect", ctx);
        invokePrivate(handler, "onMessage", ctx);
        
        // No direct verification possible for join auction without accessing private maps, 
        // but it should not throw.
    }

    private void setFinalField(Object obj, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = io.javalin.websocket.WsContext.class.getField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            // If getField fails, try getDeclaredField
            try {
                java.lang.reflect.Field field = io.javalin.websocket.WsContext.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
            } catch (Exception e2) {
                throw new RuntimeException(e2);
            }
        }
    }

    private void invokePrivate(Object obj, String methodName, Object... args) {
        try {
            java.lang.reflect.Method method = java.util.Arrays.stream(obj.getClass().getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName) && m.getParameterCount() == args.length)
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(methodName));
            method.setAccessible(true);
            method.invoke(obj, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
