package com.auction.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DatabaseConnection – provides fresh SQLite connections per call.
 *
 * DESIGN NOTE: SQLite JDBC does not support multiple concurrent ResultSets
 * on a single shared Connection (causes "stmt pointer is closed" errors when
 * nested repository calls create new Statements while an outer ResultSet is
 * still open).  The solution is to open a new connection for every call to
 * getConnection() and let callers close it in their try-with-resources block.
 *
 * The one-time table-creation step is still guarded by a static flag so it
 * only runs once per JVM process, not on every query.
 */
public class DatabaseConnection {

    private static final String URL = "jdbc:sqlite:db.auction";
    private static volatile boolean tablesCreated = false;

    private DatabaseConnection() {}

    /**
     * Opens and returns a brand-new JDBC Connection.
     * The caller MUST close it (try-with-resources is the right pattern).
     * Tables are created on the very first call (idempotent – IF NOT EXISTS).
     */
    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(URL);

        // Enable WAL mode and foreign keys for every connection
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
        }

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

    /** Called from Main.start() for early initialization (optional but good practice). */
    public static void initialize() {
        try (Connection conn = getConnection()) {
            System.out.println("[DB] Database ready: " + URL);
        } catch (SQLException e) {
            System.err.println("[DB] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private static void createTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id           TEXT PRIMARY KEY,
                    username     TEXT NOT NULL UNIQUE,
                    password     TEXT NOT NULL,
                    role         TEXT NOT NULL,
                    balance      REAL    DEFAULT 0.0,
                    shop_name    TEXT,
                    rating       REAL    DEFAULT 0.0,
                    cntvoted     INTEGER DEFAULT 0,
                    access_level INTEGER DEFAULT 1,
                    created_at   TEXT NOT NULL
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS items (
                    id              TEXT PRIMARY KEY,
                    name            TEXT NOT NULL,
                    description     TEXT,
                    starting_price  REAL NOT NULL,
                    image_url       TEXT DEFAULT '',
                    category        TEXT NOT NULL,
                    owner_id        TEXT NOT NULL,
                    warranty_months INTEGER DEFAULT 0,
                    artist_name     TEXT    DEFAULT '',
                    year_created    INTEGER DEFAULT 0,
                    mileage         REAL    DEFAULT 0.0,
                    year            INTEGER DEFAULT 0,
                    created_at      TEXT NOT NULL,
                    FOREIGN KEY (owner_id) REFERENCES users(id)
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS auctions (
                    id          TEXT PRIMARY KEY,
                    seller_id   TEXT NOT NULL,
                    item_id     TEXT NOT NULL,
                    status      TEXT NOT NULL,
                    highest_bid REAL NOT NULL DEFAULT 0.0,
                    start_time  TEXT,
                    end_time    TEXT NOT NULL,
                    created_at  TEXT NOT NULL,
                    FOREIGN KEY (seller_id) REFERENCES users(id),
                    FOREIGN KEY (item_id)   REFERENCES items(id)
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS bid_transactions (
                    id         TEXT PRIMARY KEY,
                    bidder_id  TEXT NOT NULL,
                    auction_id TEXT NOT NULL,
                    amount     REAL NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (bidder_id)  REFERENCES users(id),
                    FOREIGN KEY (auction_id) REFERENCES auctions(id)
                )
                """);

            System.out.println("[DB] Tables ready.");
        }
    }
}