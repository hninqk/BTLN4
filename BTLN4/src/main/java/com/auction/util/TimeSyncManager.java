package com.auction.util;

import com.auction.client.ApiClient;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Client-Server Time Synchronization
 * Helps calculate time offset between local clock and server clock to prevent fraud.
 */
public class TimeSyncManager {
    private static long syncServerTimeMillis = 0;
    private static long syncNanoTime = 0;
    private static boolean isSynced = false;

    /**
     * Call API to get server time and calculate offset.
     */
    public static void syncTimeWithServer() {
        try {
            // Get current timestamp before API call for more accurate offset
            long requestTime = System.currentTimeMillis();
            String raw = ApiClient.getInstance().getSync("/api/time/current");
            JsonObject json = ApiClient.getInstance().parseObject(raw);
            if (json.has("serverTime")) {
                long serverTime = json.get("serverTime").getAsLong();
                long responseTime = System.currentTimeMillis();
                
                
                // Account for network latency (round trip time / 2)
                long rtt = responseTime - requestTime;
                syncServerTimeMillis = serverTime + (rtt / 2);
                syncNanoTime = System.nanoTime();
                isSynced = true;
                
                System.out.println("[TimeSync] Server time synchronized. Base: " + syncServerTimeMillis + "ms (RTT: " + rtt + "ms)");
            }
        } catch (Exception e) {
            System.err.println("[TimeSync] Failed to sync time with server: " + e.getMessage());
        }
    }

    /**
     * Get the current time in milliseconds with server offset applied.
     */
    public static long getCurrentTimeMillis() {
        if (!isSynced) {
            // Fallback to local time if not yet synced
            return System.currentTimeMillis();
        }
        long elapsedMillis = (System.nanoTime() - syncNanoTime) / 1_000_000L;
        return syncServerTimeMillis + elapsedMillis;
    }

    /**
     * Get current synchronized LocalDateTime.
     */
    public static LocalDateTime getNow() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(getCurrentTimeMillis()), ZoneId.systemDefault());
    }
}
