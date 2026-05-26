package com.auction.client;

import com.auction.util.AppConfig;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AuctionClientTest {

    @Test
    void testConnectAndSend() {
        try (var mockedHttpClient = mockStatic(HttpClient.class);
             var mockedAppConfig = mockStatic(AppConfig.class)) {
             
            HttpClient mockClient = mock(HttpClient.class);
            WebSocket.Builder mockBuilder = mock(WebSocket.Builder.class);
            WebSocket mockWebSocket = mock(WebSocket.class);

            mockedHttpClient.when(HttpClient::newHttpClient).thenReturn(mockClient);
            when(mockClient.newWebSocketBuilder()).thenReturn(mockBuilder);
            
            org.mockito.ArgumentCaptor<WebSocket.Listener> listenerCaptor = org.mockito.ArgumentCaptor.forClass(WebSocket.Listener.class);
            when(mockBuilder.buildAsync(any(URI.class), listenerCaptor.capture()))
                    .thenReturn(CompletableFuture.completedFuture(mockWebSocket));
            
            mockedAppConfig.when(AppConfig::webSocketUrl).thenReturn("ws://localhost:7000/auction");

            AuctionClient client = new AuctionClient();
            client.connect(mock(Consumer.class), mock(Consumer.class));
            
            WebSocket.Listener listener = listenerCaptor.getValue();
            listener.onOpen(mockWebSocket);
            
            assertTrue(client.isConnected());
            client.send("msg");
            verify(mockWebSocket).sendText(eq("msg"), eq(true));
            
            client.disconnect();
            verify(mockWebSocket).sendClose(anyInt(), anyString());
        }
    }
}
