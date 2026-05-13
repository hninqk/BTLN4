package com.auction.util;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.File;

public final class ImageLoaderUtil {
    private ImageLoaderUtil() {
    }

    public static Image loadItemImage(String imageUrl, double width, double height) {
        String url = imageUrl == null ? "" : imageUrl.trim();
        try {
            if (!url.isEmpty()) {
                if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file:")) {
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
