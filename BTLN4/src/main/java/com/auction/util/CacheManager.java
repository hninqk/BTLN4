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

    private CacheManager() {}

    public static CacheManager getInstance() {
        return instance;
    }

    /**
     * Retrieves an image from the cache or loads it proactively.
     */
    public Image getImage(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        SoftReference<Image> ref = imageCache.get(url);
        if (ref != null) {
            Image img = ref.get();
            if (img != null) {
                return img; // Cache hit
            }
        }

        // Cache miss or garbage collected
        return null;
    }

    public void putImage(String url, Image image) {
        if (url != null && image != null) {
            imageCache.put(url, new SoftReference<>(image));
        }
    }

    public void clearCache() {
        imageCache.clear();
    }
}
