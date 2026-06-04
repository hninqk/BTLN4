package com.auction.core.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;

/**
 * ImgurUploader (Now utilizing Catbox.moe for reliable, free image hosting).
 *
 * Imgur's anonymous API often blocks datacenter IPs and throws 403 Forbidden. 
 * Catbox.moe is a reliable alternative that allows free anonymous uploads 
 * without API keys and returns a permanent public URL.
 *
 * Thread-safety: all methods are stateless — safe to call from any thread.
 */
public final class CatboxUploader {

    private static final String UPLOAD_URL = "https://catbox.moe/user/api.php";
    private static final int    TIMEOUT_SECS = 30;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private CatboxUploader() {}

    /**
     * Uploads a file and returns its direct image URL.
     *
     * Blocks the calling thread – always invoke from a background Task,
     * never from the JavaFX Application Thread.
     *
     * @param file local image file (JPEG / PNG / GIF / WEBP)
     * @return public HTTPS URL string
     * @throws IOException if upload fails
     */
    public static String upload(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("File not found: " + file);
        }

        String boundary = "---Boundary" + System.currentTimeMillis();
        byte[] fileBytes;
        String uploadFileName = file.getName();
        try {
            java.awt.image.BufferedImage original = javax.imageio.ImageIO.read(file);
            if (original != null) {
                int targetWidth = original.getWidth();
                int targetHeight = original.getHeight();
                int maxDim = 800; // Increased to 800px for better quality

                if (targetWidth > maxDim || targetHeight > maxDim) {
                    if (targetWidth > targetHeight) {
                        targetHeight = (targetHeight * maxDim) / targetWidth;
                        targetWidth = maxDim;
                    } else {
                        targetWidth = (targetWidth * maxDim) / targetHeight;
                        targetHeight = maxDim;
                    }
                }

                // Use ARGB to preserve transparency and prevent "caro" checkerboard artifacts
                java.awt.image.BufferedImage resized = new java.awt.image.BufferedImage(targetWidth, targetHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2d = resized.createGraphics();
                g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC); // BICUBIC for best quality
                g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
                g2d.dispose();

                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(resized, "png", baos);
                fileBytes = baos.toByteArray();
                uploadFileName = "image.png"; // Force .png extension
            } else {
                fileBytes = Files.readAllBytes(file.toPath());
            }
        } catch (Exception e) {
            System.err.println("[CatboxUploader] Image processing failed, falling back to raw file: " + e.getMessage());
            fileBytes = Files.readAllBytes(file.toPath());
        }
        
        StringBuilder sb = new StringBuilder();
        
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n");
        sb.append("fileupload\r\n");
        
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"").append(uploadFileName).append("\"\r\n");
        sb.append("Content-Type: application/octet-stream\r\n\r\n");

        byte[] headerBytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] footerBytes = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Combine into a single byte array for the request body
        byte[] body = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, body, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + fileBytes.length, footerBytes.length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(UPLOAD_URL))
                .timeout(Duration.ofSeconds(TIMEOUT_SECS))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Upload interrupted", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("Cloud HTTP " + response.statusCode() + ": " + response.body());
        }

        String link = response.body().trim();
        if (!link.startsWith("http")) {
             throw new IOException("Upload failed, invalid response: " + link);
        }
        
        return link;
    }

    /**
     * Convenience wrapper: returns an empty string (instead of throwing) on
     * failure, writing the error to stderr. Useful for non-critical image paths.
     */
    public static String uploadSilent(File file) {
        try {
            return upload(file);
        } catch (Exception e) {
            System.err.println("[CatboxUploader] Upload failed: " + e.getMessage());
            return "";
        }
    }
}
