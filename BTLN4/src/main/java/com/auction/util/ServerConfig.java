package com.auction.util;

/**
 * ServerConfig – single source of truth for the WebSocket server URL.
 *
 * The DEFAULT_URL points to the public ngrok server.
 * Clients (friends) just run the JAR – they automatically connect here.
 *
 * The server operator can override via system property:
 * -Dauction.server.url=ws://localhost:7000/auction (to run locally)
 *
 * Priority: system property > runtime override > DEFAULT_URL (ngrok).
 */
public final class ServerConfig {

    /**
     * Public server URL – baked into every client build.
     * Connects to the live Render instance.
     */
    private static final String DEFAULT_URL = "https://btln4.onrender.com";

    /** Runtime-overridable URL */
    private static volatile String runtimeUrl = null;

    private ServerConfig() {
    }

    /**
     * Returns the active WebSocket server URL.
     * Priority:
     * 1. System property -Dauction.server.url=... (used by run_server.sh)
     * 2. Runtime override (setServerUrl)
     * 3. DEFAULT_URL – the public ngrok address
     */
    public static String getServerUrl() {
        String prop = System.getProperty("auction.server.url");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        if (runtimeUrl != null) {
            return runtimeUrl;
        }
        return DEFAULT_URL;
    }

    /** Override the server URL at runtime. */
    public static void setServerUrl(String url) {
        runtimeUrl = url;
    }

    /** True if running against a remote server (non-localhost). */
    public static boolean isRemote() {
        String url = getServerUrl();
        return !url.contains("localhost") && !url.contains("127.0.0.1");
    }
}
