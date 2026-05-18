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

public final class ImageLoaderUtil {
    private ImageLoaderUtil() {
    }

    public static Image loadItemImage(String imageUrl, double width, double height) {
        String url = imageUrl == null ? "" : imageUrl.trim();
        try {
            if (!url.isEmpty()) {
                if (url.startsWith("data:image/") && url.contains(";base64,")) {
                    try {
                        String base64Data = url.substring(url.indexOf(";base64,") + 8);
                        byte[] decoded = Base64.getDecoder().decode(base64Data);
                        try (ByteArrayInputStream bis = new ByteArrayInputStream(decoded)) {
                            return new Image(bis, width, height, true, true);
                        }
                    } catch (Exception e) {
                        System.err.println("[ImageLoaderUtil] Error decoding base64 image: " + e.getMessage());
                    }
                } else if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file:")) {
                    Image remote = new Image(url, width, height, true, true, true);
                    if (!remote.isError()) {
                        return remote;
                    }
                } else {
                    File localFile = new File(url);
                    if (localFile.exists()) {
                        Image local = new Image(localFile.toURI().toString(), width, height, true, true, true);
                        if (!local.isError()) {
                            return local;
                        }
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
        int w = Math.max(width, 32);
        int h = Math.max(height, 24);
        WritableImage image = new WritableImage(w, h);
        PixelWriter px = image.getPixelWriter();

        Color bg = Color.web("#E5E7EB");
        Color border = Color.web("#9CA3AF");
        Color cross = Color.web("#6B7280");

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px.setColor(x, y, bg);
            }
        }

        for (int x = 0; x < w; x++) {
            px.setColor(x, 0, border);
            px.setColor(x, h - 1, border);
        }
        for (int y = 0; y < h; y++) {
            px.setColor(0, y, border);
            px.setColor(w - 1, y, border);
        }

        int min = Math.min(w, h);
        for (int i = 0; i < min; i++) {
            int x1 = i;
            int y1 = i;
            int x2 = w - 1 - i;
            int y2 = i;
            px.setColor(x1, y1, cross);
            px.setColor(x2, y2, cross);
        }
        return image;
    }
}
