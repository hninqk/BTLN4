package com.auction.client;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ApiClientTest {

    private ApiClient apiClient;
    private HttpClient mockHttpClient;

    @BeforeEach
    void setUp() throws Exception {
        apiClient = ApiClient.getInstance();
        mockHttpClient = mock(HttpClient.class);
        
        // Inject mockHttpClient using reflection
        java.lang.reflect.Field field = ApiClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(apiClient, mockHttpClient);
    }

    @Test
    void testGetAsync() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn("{\"status\":\"ok\"}");
        
        doReturn(CompletableFuture.completedFuture(mockResponse))
                .when(mockHttpClient).sendAsync(any(), any());
        
        CompletableFuture<String> future = apiClient.getAsync("/api/test");
        String result = future.get();
        assertEquals("{\"status\":\"ok\"}", result);
    }

    @Test
    void testPostSync() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn("{\"status\":\"created\"}");
        
        doReturn(CompletableFuture.completedFuture(mockResponse))
                .when(mockHttpClient).sendAsync(any(), any());
        
        String result = apiClient.postSync("/api/test", new JsonObject());
        assertEquals("{\"status\":\"created\"}", result);
    }

    @Test
    void testParseHelpers() {
        String json = "{\"key\":\"value\"}";
        JsonObject obj = apiClient.parseObject(json);
        assertEquals("value", obj.get("key").getAsString());
        
        assertFalse(apiClient.isError(obj));
        
        obj.addProperty("error", "msg");
        assertTrue(apiClient.isError(obj));
    }
}
