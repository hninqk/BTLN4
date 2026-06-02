package com.auction.infra.db;
import com.auction.ui.util.*;
import com.auction.core.util.*;
import com.auction.api.config.*;
import com.auction.infra.db.*;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DatabaseConnection – cung cấp Connection qua HikariCP Connection Pool.
 *
 * Lý do dùng Pool:
 * - Mở một kết nối TCP (đặc biệt tới PostgreSQL từ xa) tốn 0.5-1 giây.
 * - HikariCP giữ sẵn các kết nối đã mở, mỗi getConnection() chỉ mất vài micro-giây.
 * - Pool size mặc định 10 (cấu hình trong buildConfig).
 */
public class DatabaseConnection {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);
    private static final HikariDataSource POOL;
    private static volatile boolean tablesCreated = false;

    static {
        POOL = buildPool();
    }

    // ─────────────────────────── Cấu hình Pool ───────────────────────────

    private static HikariDataSource buildPool() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            log.warn("Driver not found: {}", e.getMessage());
        }

        String jdbcUrl = AppConfig.jdbcUrl();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5_000);
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(1_800_000);
        config.setConnectionTestQuery("SELECT 1");
        log.info("Using Render PostgreSQL via HikariCP");

        config.setPoolName("AuctionPool");
        return new HikariDataSource(config);
    }

    // ─────────────────────────── Public API ───────────────────────────

    public static boolean isPostgres() {
        return true;
    }

    private DatabaseConnection() {}

    /**
     * Lấy Connection từ pool (cực nhanh – không mở kết nối mới).
     * Caller phải đóng Connection trong try-with-resources để trả về pool.
     */
    public static Connection getConnection() throws SQLException {
        Connection connection = POOL.getConnection();

        // Tạo bảng chỉ một lần khi lần đầu lấy connection
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

    private static void migrateAuctionStatusConstraint(Statement st) {
        String sql = """
                SELECT conname
                FROM pg_constraint
                WHERE conrelid = 'auctions'::regclass
                  AND contype = 'c'
                  AND pg_get_constraintdef(oid) ILIKE '%status%'
                  AND pg_get_constraintdef(oid) NOT ILIKE '%UPCOMING%'
                """;
        try (ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String constraintName = rs.getString("conname").replace("\"", "\"\"");
                st.execute("ALTER TABLE auctions DROP CONSTRAINT IF EXISTS \"" + constraintName + "\"");
                log.info("Migration applied: dropped outdated auctions status constraint {}", constraintName);
            }
        } catch (Exception ignored) {
            // Existing deployments without a status CHECK need no migration.
        }
    }

    public static void initialize() {
        try (Connection conn = getConnection()) {
            log.info("Database ready (HikariCP pool active)");
        } catch (SQLException e) {
            log.error("Failed to initialize database", e);
            throw new IllegalStateException("Failed to initialize database", e);
        }
    }

    // ─────────────────────────── Schema ───────────────────────────

    private static void createTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            String realType = isPostgres() ? "DOUBLE PRECISION" : "REAL";

            st.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "id             TEXT PRIMARY KEY, "
                    + "username       TEXT NOT NULL UNIQUE, "
                    + "password       TEXT NOT NULL, "
                    + "role           TEXT NOT NULL, "
                    + "balance        " + realType + " DEFAULT 0.0, "
                    + "frozen_balance " + realType + " DEFAULT 0.0, "
                    + "shop_name      TEXT, "
                    + "created_at     TEXT NOT NULL)");

            // Migration: thêm cột frozen_balance nếu DB cũ chưa có
            try {
                st.execute("ALTER TABLE users ADD COLUMN frozen_balance " + realType + " DEFAULT 0.0");
                log.info("Migration applied: added frozen_balance column to users");
            } catch (Exception ignored) {
                // Column already exists.
            }

            st.execute("CREATE TABLE IF NOT EXISTS items ("
                    + "id              TEXT PRIMARY KEY, "
                    + "name            TEXT NOT NULL, "
                    + "description     TEXT, "
                    + "starting_price  " + realType + " NOT NULL, "
                    + "image_url       TEXT DEFAULT '', "
                    + "category        TEXT NOT NULL, "
                    + "owner_id        TEXT NOT NULL, "
                    + "artist_name     TEXT DEFAULT '', "
                    + "created_at      TEXT NOT NULL, "
                    + "FOREIGN KEY (owner_id) REFERENCES users(id))");

            st.execute("CREATE TABLE IF NOT EXISTS auctions ("
                    + "id          TEXT PRIMARY KEY, "
                    + "seller_id   TEXT NOT NULL, "
                    + "item_id     TEXT NOT NULL, "
                    + "status      TEXT NOT NULL, "
                    + "highest_bid " + realType + " NOT NULL DEFAULT 0.0, "
                    + "start_time  TEXT, "
                    + "end_time    TEXT NOT NULL, "
                    + "created_at  TEXT NOT NULL, "
                    + "FOREIGN KEY (seller_id) REFERENCES users(id), "
                    + "FOREIGN KEY (item_id)   REFERENCES items(id))");

            migrateAuctionStatusConstraint(st);

            st.execute("CREATE TABLE IF NOT EXISTS bid_transactions ("
                    + "id         TEXT PRIMARY KEY, "
                    + "bidder_id  TEXT NOT NULL, "
                    + "auction_id TEXT NOT NULL, "
                    + "amount     " + realType + " NOT NULL, "
                    + "created_at TEXT NOT NULL, "
                    + "FOREIGN KEY (bidder_id) REFERENCES users(id), "
                    + "FOREIGN KEY (auction_id) REFERENCES auctions(id))");

            st.execute("CREATE TABLE IF NOT EXISTS auto_bids ("
                    + "id         TEXT PRIMARY KEY, "
                    + "auction_id TEXT NOT NULL, "
                    + "bidder_id  TEXT NOT NULL, "
                    + "max_bid    " + realType + " NOT NULL, "
                    + "increment  " + realType + " NOT NULL, "
                    + "created_at TEXT NOT NULL, "
                    + "FOREIGN KEY (auction_id) REFERENCES auctions(id), "
                    + "FOREIGN KEY (bidder_id) REFERENCES users(id))");

            // Index để tăng tốc độ query phổ biến
            st.execute("CREATE INDEX IF NOT EXISTS idx_auctions_seller ON auctions(seller_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_bids_auction ON bid_transactions(auction_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_items_owner ON items(owner_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_autobids_auction ON auto_bids(auction_id)");

            log.info("Tables and indexes ready");
            log.info("Schema includes frozen_balance for fund-freezing support");
        }
    }
}
