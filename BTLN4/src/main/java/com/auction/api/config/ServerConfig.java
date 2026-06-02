package com.auction.api.config;
import com.auction.ui.util.*;
import com.auction.core.util.*;
import com.auction.api.config.*;
import com.auction.infra.db.*;

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
