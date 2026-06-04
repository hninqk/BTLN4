package com.auction.infra.repository;

import java.sql.*;

import com.auction.core.model.AutoBid;
import com.auction.infra.db.DatabaseConnection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JdbcAutoBidRepository {

    public void save(AutoBid autoBid) {
        String sql;
        if (DatabaseConnection.isPostgres()) {
            sql = """
                INSERT INTO auto_bids (id, auction_id, bidder_id, max_bid, created_at)
                VALUES (?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET max_bid = EXCLUDED.max_bid, created_at = EXCLUDED.created_at
                """;
        } else {
            sql = """
                INSERT OR REPLACE INTO auto_bids (id, auction_id, bidder_id, max_bid, created_at)
                VALUES (?,?,?,?,?)
                """;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, autoBid.getId());
            ps.setString(2, autoBid.getAuctionId());
            ps.setString(3, autoBid.getBidderId());
            ps.setDouble(4, autoBid.getMaxBid());
            ps.setString(5, autoBid.getCreatedAt().toString());
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[AutoBidRepo] save error: " + e.getMessage());
        }
    }

    public AutoBid findByAuctionIdAndBidderId(String auctionId, String bidderId) {
        String sql = "SELECT * FROM auto_bids WHERE auction_id=? AND bidder_id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            ps.setString(2, bidderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new AutoBid(
                            rs.getString("id"),
                            rs.getString("auction_id"),
                            rs.getString("bidder_id"),
                            rs.getDouble("max_bid"),
                            LocalDateTime.parse(rs.getString("created_at"))
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[AutoBidRepo] findByAuctionIdAndBidderId error: " + e.getMessage());
        }
        return null;
    }

    public List<AutoBid> findByAuctionId(String auctionId) {
        List<AutoBid> list = new ArrayList<>();
        String sql = "SELECT * FROM auto_bids WHERE auction_id=? ORDER BY max_bid DESC, created_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AutoBid ab = new AutoBid(
                            rs.getString("id"),
                            rs.getString("auction_id"),
                            rs.getString("bidder_id"),
                            rs.getDouble("max_bid"),
                            LocalDateTime.parse(rs.getString("created_at"))
                    );
                    list.add(ab);
                }
            }
        } catch (SQLException e) {
            System.err.println("[AutoBidRepo] findByAuctionId error: " + e.getMessage());
        }
        return list;
    }

    public void deleteByAuctionIdAndBidderId(String auctionId, String bidderId) {
        String sql = "DELETE FROM auto_bids WHERE auction_id=? AND bidder_id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            ps.setString(2, bidderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[AutoBidRepo] deleteByAuctionIdAndBidderId error: " + e.getMessage());
        }
    }
}
