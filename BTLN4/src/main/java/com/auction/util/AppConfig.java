package com.auction.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Production-only runtime configuration.
 * The Java client always targets the Render web service. The server always
 * requires a Render PostgreSQL JDBC URL from environment variables.
 */
public final class AppConfig {

    private static final String RENDER_SERVER_URL = "https://btln4.onrender.com";
    private static final Map<String, String> DOTENV = loadDotEnv();

    private AppConfig() {
    }

    public static String environment() {
        return "production";
    }

    public static boolean isProduction() {
        return true;
    }

    public static int port() {
        return intValue("PORT", 10000);
    }

    public static String serverUrl() {
        return RENDER_SERVER_URL;
    }

    public static String webSocketUrl() {
        String url = serverUrl()
                .replaceFirst("^https://", "wss://")
                .replaceFirst("^http://", "ws://");
        return url.endsWith("/auction") ? url : url + "/auction";
    }

    public static String httpBaseUrl() {
        String url = serverUrl()
                .replaceFirst("^wss://", "https://")
                .replaceFirst("^ws://", "http://");
        int slashAfterHost = url.indexOf('/', url.indexOf("://") + 3);
        return slashAfterHost > 0 ? url.substring(0, slashAfterHost) : url;
    }

    public static String jdbcUrl() {
        String explicit = firstValue(
                "JDBC_DATABASE_URL",
                "DATABASE_URL",
                "DB_URL");
        if (explicit == null || explicit.isBlank()) {
            throw new IllegalStateException(
                    "Render PostgreSQL is required. Set JDBC_DATABASE_URL or DATABASE_URL.");
        }
        String jdbcUrl = normalizeJdbcUrl(explicit);
        if (!jdbcUrl.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("Only Render PostgreSQL is supported. Invalid JDBC URL: "
                    + maskedJdbcUrl(jdbcUrl));
        }
        return jdbcUrl;
    }

    public static boolean isPostgres() {
        return jdbcUrl().startsWith("jdbc:postgresql:");
    }

    public static long antiSnipeWindowSeconds() {
        return longValue("ANTI_SNIPE_WINDOW_SECONDS", 60);
    }

    public static long antiSnipeExtensionSeconds() {
        return longValue("ANTI_SNIPE_EXTENSION_SECONDS", 180);
    }

    public static String diagnostics() {
        return "env=" + environment()
                + ", port=" + port()
                + ", serverUrl=" + serverUrl()
                + ", db=" + maskedJdbcUrl(jdbcUrl())
                + ", antiSnipeWindowSeconds=" + antiSnipeWindowSeconds()
                + ", antiSnipeExtensionSeconds=" + antiSnipeExtensionSeconds();
    }

    private static String value(String key, String fallback) {
        String found = firstValue(key);
        return found == null || found.isBlank() ? fallback : found;
    }

    private static int intValue(String key, int fallback) {
        String raw = value(key, Integer.toString(fallback));
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long longValue(String key, long fallback) {
        String raw = value(key, Long.toString(fallback));
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String firstValue(String... keys) {
        for (String key : keys) {
            String systemValue = System.getProperty(key);
            if (systemValue != null && !systemValue.isBlank()) {
                return systemValue;
            }
            String envValue = System.getenv(key);
            if (envValue != null && !envValue.isBlank()) {
                return envValue;
            }
            String dotEnvValue = DOTENV.get(key);
            if (dotEnvValue != null && !dotEnvValue.isBlank()) {
                return dotEnvValue;
            }
        }
        return null;
    }

    private static String normalizeJdbcUrl(String url) {
        if (url.startsWith("jdbc:")) {
            return url;
        }
        if (url.startsWith("postgres://")) {
            return postgresUriToJdbc(url);
        }
        if (url.startsWith("postgresql://")) {
            return postgresUriToJdbc(url);
        }
        return url;
    }

    private static String postgresUriToJdbc(String url) {
        URI uri = URI.create(url);
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://")
                .append(uri.getHost());
        if (uri.getPort() > 0) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append(uri.getPath());

        String query = uri.getQuery();
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            String[] parts = userInfo.split(":", 2);
            String user = decode(parts[0]);
            String password = parts.length > 1 ? decode(parts[1]) : "";
            query = appendQuery(query, "user", user);
            query = appendQuery(query, "password", password);
        }

        if (query != null && !query.isBlank()) {
            jdbc.append('?').append(query);
        }
        return jdbc.toString();
    }

    private static String appendQuery(String query, String key, String value) {
        String pair = key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
        return query == null || query.isBlank() ? pair : query + "&" + pair;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String maskedJdbcUrl(String jdbcUrl) {
        return jdbcUrl.replaceAll("(?i)(://[^:/@]+:)[^@]+@", "$1****@");
    }

    private static Map<String, String> loadDotEnv() {
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; current != null && i < 5; i++) {
            Path candidate = current.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return parseDotEnv(candidate);
            }
            current = current.getParent();
        }
        return Map.of();
    }

    private static Map<String, String> parseDotEnv(Path path) {
        Map<String, String> values = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int equals = trimmed.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, equals).trim();
                String value = trimmed.substring(equals + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException ignored) {
            return Map.of();
        }
        return values;
    }
}
