package com.auction.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AppConfigTest {

    @Test
    void testAppConfigMethods() {
        System.setProperty("JDBC_DATABASE_URL", "jdbc:postgresql://localhost/test");
        
        System.setProperty("PORT", "8888");
        assertEquals(8888, AppConfig.port());
        System.clearProperty("PORT");

        assertEquals("jdbc:postgresql://localhost/test", AppConfig.jdbcUrl());
        assertTrue(AppConfig.isPostgres());

        System.setProperty("ANTI_SNIPE_WINDOW_SECONDS", "120");
        assertEquals(120, AppConfig.antiSnipeWindowSeconds());
        System.clearProperty("ANTI_SNIPE_WINDOW_SECONDS");

        assertEquals("production", AppConfig.environment());
        assertTrue(AppConfig.isProduction());
        assertNotNull(AppConfig.serverUrl());
        assertNotNull(AppConfig.webSocketUrl());
        assertNotNull(AppConfig.httpBaseUrl());
        assertNotNull(AppConfig.diagnostics());
        
        System.clearProperty("JDBC_DATABASE_URL");
    }

    @Test
    void testInvalidJdbcUrl() {
        System.setProperty("JDBC_DATABASE_URL", "jdbc:mysql://localhost/test");
        assertThrows(IllegalStateException.class, () -> AppConfig.jdbcUrl());
        System.clearProperty("JDBC_DATABASE_URL");
    }

    @Test
    void testPostgresUriNormalization() {
        System.setProperty("DATABASE_URL", "postgres://user:pass@host:5432/db");
        String jdbc = AppConfig.jdbcUrl();
        assertTrue(jdbc.startsWith("jdbc:postgresql://host:5432/db"));
        assertTrue(jdbc.contains("user=user"));
        assertTrue(jdbc.contains("password=pass"));
        System.clearProperty("DATABASE_URL");
    }
}
