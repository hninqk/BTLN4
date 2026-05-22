package com.auction.util;

import javafx.scene.image.Image;

import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance centralized CacheManager.
 * Uses SoftReference to keep images in memory while preventing OutOfMemoryError.
 */
public class CacheManager {
    private static final CacheManager instance = new CacheManager();
    private final ConcurrentHashMap<String, SoftReference<Image>> imageCache = new ConcurrentHashMap<>();
    
    // Hard cache to prevent immediate garbage collection of recently loaded images
    private final java.util.Map<String, Image> hardCache = java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<String, Image>(100, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, Image> eldest) {
                return size() > 100;
            }
        }
    );

    private CacheManager() {}

    public static CacheManager getInstance() {
        return instance;
    }

    /**
     * Retrieves an image.
     * Check order: (1) hard LRU cache → (2) soft-reference cache → (3) miss.
     * The hard cache (100 entries, LRU) guarantees pre-loaded images survive GC
     * between the Splash screen and the first list render.
     */
    public Image getImage(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        // 1. Hard cache – guaranteed to survive GC within the 100-entry window
        Image hot = hardCache.get(url);
        if (hot != null) {
            return hot;
        }

        // 2. Soft cache – may have been GC'd under memory pressure
        SoftReference<Image> ref = imageCache.get(url);
        if (ref != null) {
            Image img = ref.get();
            if (img != null) {
                hardCache.put(url, img); // Promote back to hard cache
                return img;
            }
            // Stale soft-ref – remove to keep the map tidy
            imageCache.remove(url);
        }

        return null;
    }

    public void putImage(String url, Image image) {
        if (url != null && image != null) {
            imageCache.put(url, new SoftReference<>(image));
            hardCache.put(url, image);
        }
    }

    public void clearCache() {
        imageCache.clear();
        hardCache.clear();
    }

    public void evict(String url) {
        if (url != null) {
            imageCache.remove(url);
            hardCache.remove(url);
        }
    }
}
