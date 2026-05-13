package com.auction.util;

/**
 * ServerConfig – single source of truth for the WebSocket server URL.
 *
 * HOW TO USE NGROK:
 *   1. Start ngrok: ngrok tcp 7000
 *   2. Copy the forwarding URL, e.g. tcp://0.tcp.ngrok.io:12345
 *   3. Convert to ws:// → ws://0.tcp.ngrok.io:12345/auction
 *   4. Set the URL before launching:
 *        ServerConfig.setServerUrl("ws://0.tcp.ngrok.io:12345/auction");
 *      Or set system property:  -Dauction.server.url=ws://0.tcp.ngrok.io:12345/auction
 *
 * If no custom URL is set, defaults to localhost (same-machine mode).
 */
public final class ServerConfig {

    /** Default local server URL */
    private static final String DEFAULT_URL = "ws://localhost:7000/auction";

    /** Runtime-overridable URL (e.g. ngrok) */
    private static volatile String serverUrl = null;

    private ServerConfig() {}

    /**
     * Returns the active WebSocket server URL.
     * Priority: runtime set → system property → default localhost.
     */
    public static String getServerUrl() {
        if (serverUrl != null) return serverUrl;
        String prop = System.getProperty("auction.server.url");
        if (prop != null && !prop.isBlank()) return prop;
        return DEFAULT_URL;
    }

    /**
     * Override the server URL at runtime (e.g. from a settings dialog or ngrok URL).
     * Pass null to revert to default.
     */
    public static void setServerUrl(String url) {
        serverUrl = url;
    }

    /** True if running against a remote server (non-localhost) */
    public static boolean isRemote() {
        return !getServerUrl().contains("localhost") && !getServerUrl().contains("127.0.0.1");
    }
}
