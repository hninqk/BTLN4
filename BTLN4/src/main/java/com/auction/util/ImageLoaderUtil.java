package com.auction.util;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.Base64;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ImageLoaderUtil {
    
    // Standard LRU Cache to prevent re-downloading images from the network
    private static final int MAX_CACHE_SIZE = 100;
    private static final Map<String, Image> IMAGE_CACHE = Collections.synchronizedMap(
        new LinkedHashMap<String, Image>(MAX_CACHE_SIZE + 1, .75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        }
    );
    private ImageLoaderUtil() {
    }

    public static Image loadItemImage(String imageUrl, double width, double height) {
        String url = imageUrl == null ? "" : imageUrl.trim();
        if (url.isEmpty()) return createFallbackImage((int) width, (int) height);

        String cacheKey = url + "_" + width + "_" + height;
        Image cached = IMAGE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            if (url.startsWith("data:image/") && url.contains(";base64,")) {
                try {
                    String base64Data = url.substring(url.indexOf(";base64,") + 8);
                    byte[] decoded = Base64.getDecoder().decode(base64Data);
                    try (ByteArrayInputStream bis = new ByteArrayInputStream(decoded)) {
                        Image img = new Image(bis, width, height, true, true);
                        IMAGE_CACHE.put(cacheKey, img);
                        return img;
                    }
                } catch (Exception e) {
                    System.err.println("[ImageLoaderUtil] Error decoding base64 image: " + e.getMessage());
                }
            } else if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file:")) {
                Image remote = new Image(url, width, height, true, true, true); // true = background loading
                if (!remote.isError()) {
                    IMAGE_CACHE.put(cacheKey, remote);
                    return remote;
                }
            } else {
                File localFile = new File(url);
                if (localFile.exists()) {
                    Image local = new Image(localFile.toURI().toString(), width, height, true, true, true);
                    if (!local.isError()) {
                        IMAGE_CACHE.put(cacheKey, local);
                        return local;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return createFallbackImage((int) width, (int) height);
    }

    /**
     * Converts a local file to a JPEG Base64 data URL, automatically resizing it
     * on-the-fly to a maximum dimension of 600px and compressing it to reduce
     * memory usage and PostgreSQL storage requirements.
     */
    public static String convertFileToBase64AndResize(File file) {
        try {
            BufferedImage original = ImageIO.read(file);
            if (original == null) return "";

            // Target max dimension: 600px for balanced quality/storage ratio
            int targetWidth = original.getWidth();
            int targetHeight = original.getHeight();
            int maxDim = 600;

            if (targetWidth > maxDim || targetHeight > maxDim) {
                if (targetWidth > targetHeight) {
                    targetHeight = (targetHeight * maxDim) / targetWidth;
                    targetWidth = maxDim;
                } else {
                    targetWidth = (targetWidth * maxDim) / targetHeight;
                    targetHeight = maxDim;
                }
            }

            // Create scaled instance and fill white background to support transparency safely in JPEG format
            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = resized.createGraphics();
            g2d.setColor(java.awt.Color.WHITE);
            g2d.fillRect(0, 0, targetWidth, targetHeight);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
            g2d.dispose();

            // Compress to JPEG byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();

            // Encode to Base64
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            return "data:image/jpeg;base64," + base64;
        } catch (Exception e) {
            System.err.println("[ImageLoaderUtil] Error encoding/resizing image: " + e.getMessage());
            try {
                // Fallback: Read raw bytes directly
                byte[] bytes = Files.readAllBytes(file.toPath());
                String base64 = Base64.getEncoder().encodeToString(bytes);
                return "data:image/jpeg;base64," + base64;
            } catch (Exception ex) {
                return "";
            }
        }
    }

    private static Image createFallbackImage(int width, int height) {
        int w = Math.max(width, 1);
        int h = Math.max(height, 1);
        WritableImage image = new WritableImage(w, h);
        PixelWriter px = image.getPixelWriter();
        Color bg = Color.TRANSPARENT;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px.setColor(x, y, bg);
            }
        }
        return image;
    }
}
