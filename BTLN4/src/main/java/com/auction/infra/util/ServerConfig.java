package com.auction.infra.util;

/**
 * Compatibility wrapper for client code that still asks for the server URL.
 * New configuration belongs in AppConfig.
 */
public final class ServerConfig {

    private ServerConfig() {
    }

    public static String getServerUrl() {
        return AppConfig.serverUrl();
    }

    public static boolean isRemote() {
        return true;
    }
}
