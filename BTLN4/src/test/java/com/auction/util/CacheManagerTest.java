package com.auction.util;

import javafx.scene.image.Image;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CacheManagerTest {

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = CacheManager.getInstance();
        cacheManager.clearCache();
    }

    @Test
    void testPutAndGet() {
        Image mockImage = mock(Image.class);
        cacheManager.putImage("url1", mockImage);
        
        assertEquals(mockImage, cacheManager.getImage("url1"));
        assertNull(cacheManager.getImage("url2"));
        assertNull(cacheManager.getImage(null));
        assertNull(cacheManager.getImage(" "));
    }

    @Test
    void testEvict() {
        Image mockImage = mock(Image.class);
        cacheManager.putImage("url1", mockImage);
        cacheManager.evict("url1");
        
        assertNull(cacheManager.getImage("url1"));
    }

    @Test
    void testClearCache() {
        Image mockImage = mock(Image.class);
        cacheManager.putImage("url1", mockImage);
        cacheManager.clearCache();
        
        assertNull(cacheManager.getImage("url1"));
    }
}
