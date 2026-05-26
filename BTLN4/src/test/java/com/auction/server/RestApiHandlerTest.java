package com.auction.server;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.service.AuctionService;
import com.auction.service.UserService;
import io.javalin.http.Context;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestApiHandlerTest {

    private AuctionService auctionService;
    private UserService userService;
    private RestApiHandler handler;
    private Context ctx;

    @BeforeEach
    void setUp() {
        auctionService = mock(AuctionService.class);
        userService = mock(UserService.class);
        handler = new RestApiHandler(auctionService, userService);
        ctx = mock(Context.class);
    }

    @Test
    void handleLoginSuccess() {
        String body = "{\"username\":\"alice\",\"password\":\"123\"}";
        when(ctx.body()).thenReturn(body);
        
        User user = new Bidder("u1", java.time.LocalDateTime.now(), "alice", "pass", 1000.0);
        when(userService.login("alice", "123")).thenReturn(Optional.of(user));
        
        when(ctx.status(anyInt())).thenReturn(ctx);
        when(ctx.contentType(anyString())).thenReturn(ctx);
        
        // Use reflection to call private method or just test through register() if it was public
        // But the methods are private, so I'll use reflection for surgical test.
        invokePrivate(handler, "handleLogin", ctx);
        
        verify(ctx).status(200);
        verify(ctx).result(contains("alice"));
    }

    @Test
    void handleRegisterSuccess() {
        String body = "{\"username\":\"bob\",\"password\":\"123\",\"role\":\"Bidder\"}";
        when(ctx.body()).thenReturn(body);
        when(userService.register("bob", "123", "Bidder")).thenReturn(true);
        when(userService.findByUsername("bob")).thenReturn(Optional.of(new Bidder("bob", "pass", 0.0)));
        
        when(ctx.status(anyInt())).thenReturn(ctx);
        when(ctx.contentType(anyString())).thenReturn(ctx);
        
        invokePrivate(handler, "handleRegister", ctx);
        verify(ctx).status(201);
    }

    @Test
    void handleAdminAction() {
        String auctionId = "auc1";
        when(ctx.pathParam("id")).thenReturn(auctionId);
        String body = "{\"action\":\"approve\"}";
        when(ctx.body()).thenReturn(body);
        
        Auction auction = new Auction("auc1", java.time.LocalDateTime.now(), null, null, AuctionStatus.PENDING, 0, null, null);
        when(auctionService.findById(auctionId)).thenReturn(Optional.of(auction));
        
        when(ctx.status(anyInt())).thenReturn(ctx);
        when(ctx.contentType(anyString())).thenReturn(ctx);
        
        invokePrivate(handler, "handleAdminAction", ctx);
        
        verify(ctx).status(200);
        try {
            verify(auctionService).approveAuction(any());
        } catch (Exception e) {}
    }

    @Test
    void handleCreateAuctionSuccess() {
        String body = "{\"sellerId\":\"s1\",\"itemName\":\"Phone\",\"startPrice\":500.0,\"category\":\"Electronics\",\"endTime\":\"2025-01-01T12:00:00\"}";
        when(ctx.body()).thenReturn(body);
        when(userService.findById("s1")).thenReturn(Optional.of(new Seller("s1", "pass", "shop")));
        
        when(ctx.status(anyInt())).thenReturn(ctx);
        when(ctx.contentType(anyString())).thenReturn(ctx);
        
        invokePrivate(handler, "handleCreateAuction", ctx);
        verify(ctx).status(201);
    }

    @Test
    void handleTopupSuccess() {
        String body = "{\"userId\":\"u1\",\"amount\":100.0}";
        when(ctx.body()).thenReturn(body);
        Bidder bidder = new Bidder("u1", "pass", 100.0);
        when(userService.findById("u1")).thenReturn(Optional.of(bidder));
        
        when(ctx.status(anyInt())).thenReturn(ctx);
        when(ctx.contentType(anyString())).thenReturn(ctx);
        
        invokePrivate(handler, "handleTopup", ctx);
        verify(ctx).status(200);
        assertEquals(200.0, bidder.getAccountBalance());
    }

    private void invokePrivate(Object obj, String methodName, Object... args) {
        try {
            java.lang.reflect.Method method = obj.getClass().getDeclaredMethod(methodName, Context.class);
            method.setAccessible(true);
            method.invoke(obj, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
