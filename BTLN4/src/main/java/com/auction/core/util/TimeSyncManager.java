package com.auction.core.util;

import com.auction.api.http.ApiClient;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class TimeSyncManager {
    private static long syncServerTimeMillis = 0;

    private static long syncNanoTime = 0;

    private static boolean isSynced = false;

    public static void syncTimeWithServer() {
        try {

            long requestTime = System.currentTimeMillis();
            String raw = ApiClient.getInstance().getSync("/api/time/current");
            JsonObject json = ApiClient.getInstance().parseObject(raw);
            if (json.has("serverTime")) {
                long serverTime = json.get("serverTime").getAsLong();
                long responseTime = System.currentTimeMillis();

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

    public static long getCurrentTimeMillis() {
        if (!isSynced) {

            return System.currentTimeMillis();
        }
        long elapsedMillis = (System.nanoTime() - syncNanoTime) / 1_000_000L;
        return syncServerTimeMillis + elapsedMillis;
    }

    public static LocalDateTime getNow() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(getCurrentTimeMillis()), ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
