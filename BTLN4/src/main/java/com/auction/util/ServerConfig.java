package com.auction.util;

/**
 * ServerConfig – single source of truth for the server URL.
 *
 * Always connects to the live Render instance.
 * Local server override has been removed intentionally.
 */
public final class ServerConfig {

    /** Production server URL on Render. */
    private static final String SERVER_URL = "https://btln4.onrender.com";

    private ServerConfig() {
    }

    /** Returns the Render server URL. Always points to production. */
    public static String getServerUrl() {
        return SERVER_URL;
    }

    /** Always true – client only connects to the remote Render server. */
    public static boolean isRemote() {
        return true;
    }
}

