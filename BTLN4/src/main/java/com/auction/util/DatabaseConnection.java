package com.auction.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DatabaseConnection – cung cấp Connection qua HikariCP Connection Pool.
 *
 * Lý do dùng Pool:
 * - Mở một kết nối TCP (đặc biệt tới PostgreSQL từ xa) tốn 0.5-1 giây.
 * - HikariCP giữ sẵn các kết nối đã mở, mỗi getConnection() chỉ mất vài micro-giây.
 * - Pool size mặc định 10 (cấu hình trong buildConfig).
 *
 * SQLite WAL mode được bật sau khi tạo pool để hỗ trợ đọc đồng thời.
 */
public class DatabaseConnection {

    private static final HikariDataSource POOL;
    private static volatile boolean tablesCreated = false;

    static {
        POOL = buildPool();
        // Áp dụng PRAGMA WAL cho SQLite ngay khi khởi tạo pool
        if (!isPostgres()) {
            applySqlitePragmas();
        }
    }

    // ─────────────────────────── Cấu hình Pool ───────────────────────────

    private static HikariDataSource buildPool() {
        // Đảm bảo driver được load
        try {
            Class.forName("org.sqlite.JDBC");
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] Warning: Driver not found: " + e.getMessage());
        }

        String jdbcUrl = resolveJdbcUrl();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);

        if (jdbcUrl.startsWith("jdbc:postgresql:")) {
            // PostgreSQL: pool lớn hơn để xử lý nhiều client đồng thời
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(5_000);      // 5 giây timeout kết nối
            config.setIdleTimeout(300_000);           // 5 phút idle
            config.setMaxLifetime(1_800_000);         // 30 phút tuổi thọ tối đa
            config.setConnectionTestQuery("SELECT 1");
            System.out.println("[DB] Using PostgreSQL via HikariCP: " + jdbcUrl);
        } else {
            // SQLite: chỉ cần 1 connection vì SQLite là single-writer
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(10_000);
            System.out.println("[DB] Using SQLite via HikariCP: " + jdbcUrl);
        }

        config.setPoolName("AuctionPool");
        return new HikariDataSource(config);
    }

    private static String resolveJdbcUrl() {
        // Priority 1: Environment variable (Render PostgreSQL)
        String envUrl = System.getenv("JDBC_DATABASE_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            return envUrl;
        }

        // Priority 2: System property (Custom path)
        String customPath = System.getProperty("database.path");
        if (customPath != null && !customPath.isBlank()) {
            return "jdbc:sqlite:" + customPath;
        }

        // Priority 3: Giải quyết đường dẫn dựa vào vị trí class-file
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
            System.out.println("[DB] Falling back to local SQLite: " + path);
            return "jdbc:sqlite:" + path;
        } catch (Exception e) {
            System.err.println("[DB] Cannot resolve location, using CWD SQLite");
            return "jdbc:sqlite:db.auction";
        }
    }

    private static void applySqlitePragmas() {
        try (Connection conn = POOL.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA synchronous = NORMAL");
            st.execute("PRAGMA cache_size = -8000");   // 8 MB cache
            st.execute("PRAGMA temp_store = MEMORY");
        } catch (SQLException e) {
            System.err.println("[DB] Warning: PRAGMA setup failed: " + e.getMessage());
        }
    }

    // ─────────────────────────── Public API ───────────────────────────

    public static boolean isPostgres() {
        return POOL.getJdbcUrl() != null && POOL.getJdbcUrl().startsWith("jdbc:postgresql:");
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

    public static void initialize() {
        try (Connection conn = getConnection()) {
            System.out.println("[DB] Database ready (HikariCP pool active).");
        } catch (SQLException e) {
            System.err.println("[DB] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
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
                    + "rating         " + realType + " DEFAULT 0.0, "
                    + "cntvoted       INTEGER DEFAULT 0, "
                    + "access_level   INTEGER DEFAULT 1, "
                    + "created_at     TEXT NOT NULL)");

            // Migration: thêm cột frozen_balance nếu DB cũ chưa có
            try {
                st.execute("ALTER TABLE users ADD COLUMN frozen_balance " + realType + " DEFAULT 0.0");
                System.out.println("[DB] Migration: added frozen_balance column to users.");
            } catch (Exception ignored) {
                // Cột đã tồn tại – bỏ qua (SQLite/Postgres đều ném lỗi khi ADD COLUMN duplicate)
            }

            st.execute("CREATE TABLE IF NOT EXISTS items ("
                    + "id              TEXT PRIMARY KEY, "
                    + "name            TEXT NOT NULL, "
                    + "description     TEXT, "
                    + "starting_price  " + realType + " NOT NULL, "
                    + "image_url       TEXT DEFAULT '', "
                    + "category        TEXT NOT NULL, "
                    + "owner_id        TEXT NOT NULL, "
                    + "warranty_months INTEGER DEFAULT 0, "
                    + "artist_name     TEXT DEFAULT '', "
                    + "year_created    INTEGER DEFAULT 0, "
                    + "mileage         " + realType + " DEFAULT 0.0, "
                    + "year            INTEGER DEFAULT 0, "
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

            System.out.println("[DB] Tables & indexes ready.");
            System.out.println("[DB] Schema includes frozen_balance for fund-freezing support.");
        }
    }
}