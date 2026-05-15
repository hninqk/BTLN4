package com.auction.repository;

import com.auction.model.*;
import com.auction.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JdbcAuctionRepository – CRUD for auctions table.
 * Depends on JdbcUserRepository and JdbcItemRepository to reconstruct object graph.
 */
public class JdbcAuctionRepository {

    private final JdbcUserRepository userRepo = new JdbcUserRepository();
    private final JdbcItemRepository itemRepo = new JdbcItemRepository();

    // ─────────────────────────── CREATE ───────────────────────────

    /**
     * Persists an Auction (and its Item) to the database.
     * The Seller must already exist in users table.
     */
    public void save(Auction auction) {
        // 1. Persist item first
        itemRepo.save(auction.getItem(), auction.getSeller());

        // 2. Persist auction
        String sql;
        if (DatabaseConnection.isPostgres()) {
            sql = """
                INSERT INTO auctions
                    (id, seller_id, item_id, status, highest_bid, start_time, end_time, created_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO NOTHING
                """;
        } else {
            sql = """
                INSERT OR IGNORE INTO auctions
                    (id, seller_id, item_id, status, highest_bid, start_time, end_time, created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, auction.getId());
            ps.setString(2, auction.getSeller().getId());
            ps.setString(3, auction.getItem().getId());
            ps.setString(4, auction.getStatus().name());
            ps.setDouble(5, auction.getHighestBid());
            ps.setString(6, auction.getStartTime() != null ? auction.getStartTime().toString() : null);
            ps.setString(7, auction.getEndTime().toString());
            ps.setString(8, auction.getCreatedAt().toString());

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[AuctionRepo] save error: " + e.getMessage());
        }
    }

    // ─────────────────────────── READ ───────────────────────────

    public List<Auction> findAll() {
        List<Auction> list = new ArrayList<>();
        String sql = "SELECT * FROM auctions ORDER BY created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                buildAuction(rs).ifPresent(list::add);
            }
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] findAll error: " + e.getMessage());
        }
        return list;
    }

    public Optional<Auction> findById(String id) {
        String sql = "SELECT * FROM auctions WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return buildAuction(rs);
            }
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] findById error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<Auction> findBySellerId(String sellerId) {
        List<Auction> list = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE seller_id = ? ORDER BY created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) buildAuction(rs).ifPresent(list::add);
            }
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] findBySellerId error: " + e.getMessage());
        }
        return list;
    }

    // ─────────────────────────── UPDATE ───────────────────────────

    public void updateStatus(String auctionId, AuctionStatus newStatus, double highestBid,
                              java.time.LocalDateTime startTime) {
        String sql = "UPDATE auctions SET status=?, highest_bid=?, start_time=? WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus.name());
            ps.setDouble(2, highestBid);
            ps.setString(3, startTime != null ? startTime.toString() : null);
            ps.setString(4, auctionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] updateStatus error: " + e.getMessage());
        }
    }

    // (No 3-arg overload — all callers pass startTime explicitly to avoid wiping it with null)

    // ─────────────────────────── DELETE ───────────────────────────

    public boolean deleteById(String id) {
        String sql = "DELETE FROM auctions WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] deleteById error: " + e.getMessage());
        }
        return false;
    }

    // ─────────────────────────── MAPPING ───────────────────────────

    private Optional<Auction> buildAuction(ResultSet rs) throws SQLException {
        String id        = rs.getString("id");
        String sellerId  = rs.getString("seller_id");
        String itemId    = rs.getString("item_id");
        String statusStr = rs.getString("status");
        double highBid   = rs.getDouble("highest_bid");
        String startStr  = rs.getString("start_time");
        String endStr    = rs.getString("end_time");
        String createdStr= rs.getString("created_at");

        Optional<User> sellerOpt = userRepo.findById(sellerId);
        if (sellerOpt.isEmpty() || !(sellerOpt.get() instanceof Seller seller)) return Optional.empty();

        Optional<Item> itemOpt = itemRepo.findById(itemId, seller);
        if (itemOpt.isEmpty()) return Optional.empty();

        LocalDateTime startTime = startStr != null ? LocalDateTime.parse(startStr) : null;
        LocalDateTime endTime   = LocalDateTime.parse(endStr);
        LocalDateTime createdAt = LocalDateTime.parse(createdStr);

        AuctionStatus status;
        try {
            status = AuctionStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            status = switch (statusStr) {
                case "PAID", "CLOSE" -> AuctionStatus.CLOSED;
                case "PENDING"       -> AuctionStatus.PENDING;
                default              -> AuctionStatus.CANCELED;
            };
        }

        Auction auction = new Auction(id, createdAt, seller, itemOpt.get(), status, highBid, startTime, endTime);

        // ── BUG FIX: Load bid history from DB into the auction object ──────────
        // Without this, getBidHistory() always returns empty (chart / count never updates).
        loadBidHistory(auction, id);

        return Optional.of(auction);
    }

    /**
     * Loads bid_transactions for the given auctionId and injects them into the auction.
     * Uses a lightweight query that avoids re-fetching the full auction (no recursion).
     */
    private void loadBidHistory(Auction auction, String auctionId) {
        String sql = """
            SELECT bt.id, bt.bidder_id, bt.amount, bt.created_at,
                   u.username, u.password, u.balance, u.created_at AS user_created
            FROM bid_transactions bt
            JOIN users u ON bt.bidder_id = u.id
            WHERE bt.auction_id = ?
            ORDER BY bt.created_at ASC
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String bidId   = rs.getString("id");
                    double amount  = rs.getDouble("amount");
                    LocalDateTime ts = LocalDateTime.parse(rs.getString("created_at"));
                    // Reconstruct bidder inline (avoids recursive findById → buildAuction loop)
                    Bidder bidder = new Bidder(
                            rs.getString("bidder_id"),
                            LocalDateTime.parse(rs.getString("user_created")),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getDouble("balance")
                    );
                    // Inject directly into the auction's bid history
                    auction.injectBid(new BidTransaction(bidId, ts, bidder, auction, amount));
                }
            }
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] loadBidHistory error: " + e.getMessage());
        }
    }
}
