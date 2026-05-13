package com.auction.util;

/**
 * ServerConfig – single source of truth for the WebSocket server URL.
 *
 * HOW TO USE NGROK:
 * 1. Start ngrok: ngrok tcp 7000
 * 2. Copy the forwarding URL, e.g. tcp://0.tcp.ngrok.io:12345
 * 3. Convert to ws:// → ws://0.tcp.ngrok.io:12345/auction
 * 4. Set the URL before launching:
 * ServerConfig.setServerUrl("ws://0.tcp.ngrok.io:12345/auction");
 * Or set system property:
 * -Dauction.server.url=ws://0.tcp.ngrok.io:12345/auction
 *
 * If no custom URL is set, defaults to localhost (same-machine mode).
 */
public final class ServerConfig {

    /** Default local server URL */
    private static final String DEFAULT_URL = "ws://localhost:7000/auction";

    /** Runtime-overridable URL (e.g. from UI) */
    private static volatile String runtimeUrl = null;

    private ServerConfig() {
    }

    /**
     * Returns the active WebSocket server URL.
     * Priority: 
     * 1. System property (-Dauction.server.url=...)
     * 2. Runtime override (setServerUrl)
     * 3. Default localhost
     */
    public static String getServerUrl() {
        // 1. Check System Property first (highest priority, set by run_with_ngrok.sh)
        String prop = System.getProperty("auction.server.url");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }

        // 2. Check runtime override
        if (runtimeUrl != null) {
            return runtimeUrl;
        }

        // 3. Fallback to default
        return DEFAULT_URL;
    }

    /**
     * Override the server URL at runtime.
     */
    public static void setServerUrl(String url) {
        runtimeUrl = url;
    }

    /** True if running against a remote server (non-localhost) */
    public static boolean isRemote() {
        String url = getServerUrl();
        return !url.contains("localhost") && !url.contains("127.0.0.1");
    }
}
