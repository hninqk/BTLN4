package com.auction.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DatabaseConnection – provides pooled database connections using HikariCP.
 */
public class DatabaseConnection {

    private static final String URL = computeUrl();
    private static final HikariDataSource dataSource;
    private static volatile boolean tablesCreated = false;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        
        // Performance & Pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(20000);
        config.setMaxLifetime(1800000); // 30 minutes

        if (URL.startsWith("jdbc:sqlite:")) {
            // SQLite specific performance tweaks
            config.addDataSourceProperty("foreign_keys", "true");
            config.addDataSourceProperty("journal_mode", "WAL");
            config.setConnectionInitSql("PRAGMA foreign_keys = ON; PRAGMA journal_mode = WAL;");
            // SQLite is usually single-threaded access to the file anyway, so keep pool small
            config.setMaximumPoolSize(5); 
        } else if (URL.startsWith("jdbc:postgresql:")) {
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }

        dataSource = new HikariDataSource(config);
    }

    private static String computeUrl() {
        // Priority 1: Environment variable (Render PostgreSQL)
        String envUrl = System.getenv("JDBC_DATABASE_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            return envUrl;
        }

        // Priority 2: System property (Custom SQLite path)
        String customPath = System.getProperty("database.path");
        if (customPath != null && !customPath.isBlank()) {
            return "jdbc:sqlite:" + customPath;
        }

        try {
            java.net.URL loc = DatabaseConnection.class
                    .getProtectionDomain().getCodeSource().getLocation();
            java.io.File codeFile = new java.io.File(loc.toURI());
            java.io.File dbDir;
            if (codeFile.isDirectory()) {
                dbDir = codeFile.getParentFile().getParentFile();
            } else {
                dbDir = codeFile.getParentFile();
            }
            String path = new java.io.File(dbDir, "db.auction").getAbsolutePath();
            return "jdbc:sqlite:" + path;
        } catch (Exception e) {
            return "jdbc:sqlite:db.auction";
        }
    }

    public static boolean isPostgres() {
        return URL.startsWith("jdbc:postgresql:");
    }

    private DatabaseConnection() {}

    public static Connection getConnection() throws SQLException {
        Connection connection = dataSource.getConnection();

        // Create tables only once per JVM run
        if (!tablesCreated) {
            synchronized (DatabaseConnection.class) {
                if (!tablesCreated) {
                    createTables(connection);
                    tablesCreated = true;
                }
            }
        }

        return connection;
    }

    public static void initialize() {
        try (Connection conn = getConnection()) {
            System.out.println("[DB] Database ready.");
        } catch (SQLException e) {
            System.err.println("[DB] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            // PostgreSQL syntax is mostly compatible, but we use REAL/DOUBLE PRECISION carefully
            String realType = URL.startsWith("jdbc:postgresql:") ? "DOUBLE PRECISION" : "REAL";

            st.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id           TEXT PRIMARY KEY, " +
                    "username     TEXT NOT NULL UNIQUE, " +
                    "password     TEXT NOT NULL, " +
                    "role         TEXT NOT NULL, " +
                    "balance      " + realType + " DEFAULT 0.0, " +
                    "shop_name    TEXT, " +
                    "rating       " + realType + " DEFAULT 0.0, " +
                    "cntvoted     INTEGER DEFAULT 0, " +
                    "access_level INTEGER DEFAULT 1, " +
                    "created_at   TEXT NOT NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS items (" +
                    "id              TEXT PRIMARY KEY, " +
                    "name            TEXT NOT NULL, " +
                    "description     TEXT, " +
                    "starting_price  " + realType + " NOT NULL, " +
                    "image_url       TEXT DEFAULT '', " +
                    "category        TEXT NOT NULL, " +
                    "owner_id        TEXT NOT NULL, " +
                    "warranty_months INTEGER DEFAULT 0, " +
                    "artist_name     TEXT DEFAULT '', " +
                    "year_created    INTEGER DEFAULT 0, " +
                    "mileage         " + realType + " DEFAULT 0.0, " +
                    "year            INTEGER DEFAULT 0, " +
                    "created_at      TEXT NOT NULL, " +
                    "FOREIGN KEY (owner_id) REFERENCES users(id))");

            st.execute("CREATE TABLE IF NOT EXISTS auctions (" +
                    "id          TEXT PRIMARY KEY, " +
                    "seller_id   TEXT NOT NULL, " +
                    "item_id     TEXT NOT NULL, " +
                    "status      TEXT NOT NULL, " +
                    "highest_bid " + realType + " NOT NULL DEFAULT 0.0, " +
                    "start_time  TEXT, " +
                    "end_time    TEXT NOT NULL, " +
                    "created_at  TEXT NOT NULL, " +
                    "FOREIGN KEY (seller_id) REFERENCES users(id), " +
                    "FOREIGN KEY (item_id)   REFERENCES items(id))");

            st.execute("CREATE TABLE IF NOT EXISTS bid_transactions (" +
                    "id         TEXT PRIMARY KEY, " +
                    "bidder_id  TEXT NOT NULL, " +
                    "auction_id TEXT NOT NULL, " +
                    "amount     " + realType + " NOT NULL, " +
                    "created_at TEXT NOT NULL, " +
                    "FOREIGN KEY (bidder_id) REFERENCES users(id), " +
                    "FOREIGN KEY (auction_id) REFERENCES auctions(id))");

            System.out.println("[DB] Tables ready.");
        }
    }
}