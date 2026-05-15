package com.auction.repository;

import com.auction.model.*;
import com.auction.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JdbcBidRepository – CRUD for bid_transactions table.
 */
public class JdbcBidRepository implements BidRepository {

    private final JdbcUserRepository userRepo = new JdbcUserRepository();
    private final JdbcAuctionRepository auctionRepo = new JdbcAuctionRepository();

    // ─────────────────────────── CREATE ───────────────────────────

    @Override
    public void save(BidTransaction tx) {
        String sql;
        if (DatabaseConnection.isPostgres()) {
            sql = """
                INSERT INTO bid_transactions (id, bidder_id, auction_id, amount, created_at)
                VALUES (?,?,?,?,?)
                ON CONFLICT (id) DO NOTHING
                """;
        } else {
            sql = """
                INSERT OR IGNORE INTO bid_transactions (id, bidder_id, auction_id, amount, created_at)
                VALUES (?,?,?,?,?)
                """;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tx.getId());
            ps.setString(2, tx.getBidder().getId());
            ps.setString(3, tx.getAuction().getId());
            ps.setDouble(4, tx.getAmount());
            ps.setString(5, tx.getTimestamp().toString());
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[BidRepo] save error: " + e.getMessage());
        }
    }

    // ─────────────────────────── READ ───────────────────────────

    public List<BidTransaction> findAll() {
        List<BidTransaction> list = new ArrayList<>();
        String sql = "SELECT * FROM bid_transactions ORDER BY created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                buildBid(rs).ifPresent(list::add);
            }
        } catch (SQLException e) {
            System.err.println("[BidRepo] findAll error: " + e.getMessage());
        }
        return list;
    }

    public List<BidTransaction> findByAuctionId(String auctionId) {
        List<BidTransaction> list = new ArrayList<>();
        String sql = "SELECT * FROM bid_transactions WHERE auction_id=? ORDER BY created_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) buildBid(rs).ifPresent(list::add);
            }
        } catch (SQLException e) {
            System.err.println("[BidRepo] findByAuctionId error: " + e.getMessage());
        }
        return list;
    }

    public List<BidTransaction> findByBidderId(String bidderId) {
        List<BidTransaction> list = new ArrayList<>();
        String sql = "SELECT * FROM bid_transactions WHERE bidder_id=? ORDER BY created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) buildBid(rs).ifPresent(list::add);
            }
        } catch (SQLException e) {
            System.err.println("[BidRepo] findByBidderId error: " + e.getMessage());
        }
        return list;
    }

    public Optional<BidTransaction> findById(String id) {
        String sql = "SELECT * FROM bid_transactions WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return buildBid(rs);
            }
        } catch (SQLException e) {
            System.err.println("[BidRepo] findById error: " + e.getMessage());
        }
        return Optional.empty();
    }

    // ─────────────────────────── DELETE ───────────────────────────

    public boolean deleteById(String id) {
        String sql = "DELETE FROM bid_transactions WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[BidRepo] deleteById error: " + e.getMessage());
        }
        return false;
    }

    // ─────────────────────────── MAPPING ───────────────────────────

    private Optional<BidTransaction> buildBid(ResultSet rs) throws SQLException {
        String id        = rs.getString("id");
        String bidderId  = rs.getString("bidder_id");
        String auctionId = rs.getString("auction_id");
        double amount    = rs.getDouble("amount");
        LocalDateTime ts = LocalDateTime.parse(rs.getString("created_at"));

        Optional<User> bidderOpt = userRepo.findById(bidderId);
        Optional<Auction> auctionOpt = auctionRepo.findById(auctionId);

        if (bidderOpt.isEmpty() || !(bidderOpt.get() instanceof Bidder bidder)) return Optional.empty();
        if (auctionOpt.isEmpty()) return Optional.empty();

        return Optional.of(new BidTransaction(id, ts, bidder, auctionOpt.get(), amount));
    }
}