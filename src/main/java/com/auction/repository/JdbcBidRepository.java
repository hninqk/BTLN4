package com.auction.repository;

import com.auction.model.BidTransaction;
import com.auction.util.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

public class JdbcBidRepository {
    public void save(BidTransaction tx) {
        String sql = "INSERT INTO bid_transactions (id, bidder_name, auction_id, amount, created_at) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tx.getId());
            pstmt.setString(2, tx.getBidder().getUsername());
            pstmt.setString(3, tx.getAuction().getId());
            pstmt.setDouble(4, tx.getAmount());
            pstmt.setTimestamp(5, Timestamp.valueOf(tx.getTimestamp()));

            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}