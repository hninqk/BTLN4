package com.auction.core.util;

import javafx.scene.image.Image;
import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {
    private static final CacheManager instance = new CacheManager();

    private final ConcurrentHashMap<String, SoftReference<Image>> imageCache = new ConcurrentHashMap<>();

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

    public Image getImage(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        Image hot = hardCache.get(url);
        if (hot != null) {
            return hot;
        }

        SoftReference<Image> ref = imageCache.get(url);
        if (ref != null) {
            Image img = ref.get();
            if (img != null) {
                hardCache.put(url, img);
                return img;
            }

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
