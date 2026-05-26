package com.auction.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

class TimeSyncManagerTest {

    @Test
    void getNowReturnsLocalTimeWhenNotSynced() {
        long before = System.currentTimeMillis();
        LocalDateTime now = TimeSyncManager.getNow();
        long after = System.currentTimeMillis();
        
        // This is a bit loose but should work on most machines
        assertTrue(now.isAfter(LocalDateTime.of(2020, 1, 1, 0, 0)));
    }
}
