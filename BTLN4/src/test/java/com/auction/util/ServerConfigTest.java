package com.auction.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ServerConfigTest {
    @Test
    void testServerConfig() {
        assertNotNull(ServerConfig.getServerUrl());
        assertTrue(ServerConfig.isRemote());
    }
}
