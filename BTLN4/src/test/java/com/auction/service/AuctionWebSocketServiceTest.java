package com.auction.service;

import com.auction.client.AuctionClient;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AuctionWebSocketServiceTest {

    private AuctionWebSocketService.AuctionWebSocketListener mockListener;
    private MockedStatic<Platform> mockedPlatform;

    @BeforeEach
    void setUp() {
        mockListener = mock(AuctionWebSocketService.AuctionWebSocketListener.class);
        mockedPlatform = mockStatic(Platform.class);
        // Mock Platform.runLater to execute immediately
        mockedPlatform.when(() -> Platform.runLater(any())).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        mockedPlatform.close();
    }

    @Test
    void testHandleMessage() {
        try (MockedConstruction<AuctionClient> mockedClient = mockConstruction(AuctionClient.class)) {
            AuctionWebSocketService service = new AuctionWebSocketService("auc1", mockListener);
            
            // Use reflection to call private handleWsMessage
            try {
                java.lang.reflect.Method method = AuctionWebSocketService.class.getDeclaredMethod("handleWsMessage", String.class);
                method.setAccessible(true);
                
                JsonObject bidUpdate = new JsonObject();
                bidUpdate.addProperty("type", "BID_UPDATE");
                bidUpdate.addProperty("amount", 1000.0);
                
                method.invoke(service, bidUpdate.toString());
                verify(mockListener).onBidUpdate(any());
                
                JsonObject balanceUpdate = new JsonObject();
                balanceUpdate.addProperty("type", "BALANCE_UPDATE");
                method.invoke(service, balanceUpdate.toString());
                verify(mockListener).onBalanceUpdate(any());

                JsonObject errorMsg = new JsonObject();
                errorMsg.addProperty("error", "some error");
                method.invoke(service, errorMsg.toString());
                verify(mockListener).onWsError("some error");

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
