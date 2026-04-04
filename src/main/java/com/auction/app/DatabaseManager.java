package com.auction.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String URL = "jdbc:sqlite:auction_data.db";

    public static void initializeDB() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Added last_active to track online users
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE, " +
                    "password TEXT, " +
                    "role TEXT, " +
                    "last_active DATETIME DEFAULT CURRENT_TIMESTAMP)");

            // Added description to items
            stmt.execute("CREATE TABLE IF NOT EXISTS items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT, " +
                    "description TEXT, " +
                    "current_bid REAL, " +
                    "highest_bidder TEXT, " +
                    "seller_id INTEGER, " +
                    "end_time DATETIME, " +
                    "status TEXT)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}