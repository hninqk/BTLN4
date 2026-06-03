package com.auction.infra.repository;

import com.auction.core.model.*;
import com.auction.infra.db.DatabaseConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * JdbcAuctionRepository – CRUD cho bảng auctions.
 *
 * Tối ưu hiệu năng:
 * - Dùng SQL JOIN để lấy Auction + Seller + Item trong 1 query duy nhất
 *   (thay vì gọi userRepo.findById + itemRepo.findById = N+1 queries).
 * - Bid history được load trong 1 query riêng với JOIN users.
 * - Kết hợp với HikariCP pool → mỗi query chỉ tốn vài ms.
 */
public class JdbcAuctionRepository {

    // ─────────────────────────── SQL cơ bản ───────────────────────────

    /**
     * Query JOIN lấy auction + seller + item trong 1 lần.
     * Tránh gọi userRepo.findById() và itemRepo.findById() riêng lẻ.
     */
    private static final String SELECT_FULL = """
            SELECT
                a.id            AS a_id,
                a.status        AS a_status,
                a.highest_bid   AS a_highest_bid,
                a.start_time    AS a_start_time,
                a.end_time      AS a_end_time,
                a.created_at    AS a_created_at,

                s.id            AS s_id,
                s.username      AS s_username,
                s.password      AS s_password,
                s.shop_name     AS s_shop_name,
                s.created_at    AS s_created_at,

                i.id            AS i_id,
                i.name          AS i_name,
                i.description   AS i_description,
                i.starting_price AS i_starting_price,
                i.image_url     AS i_image_url,
                i.category      AS i_category,
                i.artist_name   AS i_artist_name,
                i.created_at    AS i_created_at

            FROM auctions a
            JOIN users s  ON a.seller_id = s.id
            JOIN items i  ON a.item_id   = i.id
            """;

    // ─────────────────────────── CREATE ───────────────────────────

    /**
     * Lưu Auction (và Item của nó) vào DB.
     * Seller phải đã tồn tại trong bảng users.
     */
    public boolean save(Auction auction) {
        // 1. Lưu item trước
        new JdbcItemRepository().save(auction.getItem(), auction.getSeller());

        // 2. Lưu auction
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
            int affectedRows = ps.executeUpdate();
            if (affectedRows > 0) {
                return true;
            }
            return findById(auction.getId()).isPresent();

        } catch (SQLException e) {
            System.err.println("[AuctionRepo] save error: " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────── READ ───────────────────────────

    /**
     * Lấy tất cả auctions — chỉ 2 queries:
     * 1. JOIN query cho auctions + sellers + items
     * 2. Batch load toàn bộ bid history
     */
    public List<Auction> findAll() {
        List<Auction> list = new ArrayList<>();
        String sql = SELECT_FULL + " ORDER BY a.created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                buildAuction(rs).ifPresent(list::add);
            }
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] findAll error: " + e.getMessage());
        }

        loadBidHistoryBatch(list);
        return list;
    }

    /**
     * Lấy 1 auction theo ID — 2 queries (JOIN + bid history).
     */
    public Optional<Auction> findById(String id) {
        String sql = SELECT_FULL + " WHERE a.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Optional<Auction> auctionOpt = buildAuction(rs);
                    auctionOpt.ifPresent(a -> {
                        loadBidHistory(a, id);
                        List<AutoBid> autoBids = new com.auction.infra.repository.JdbcAutoBidRepository().findByAuctionId(id);
                        for (AutoBid ab : autoBids) {
                            a.injectAutoBid(ab);
                        }
                    });
                    return auctionOpt;
                }
            }
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] findById error: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Lấy auctions theo seller — 2 queries.
     */
    public List<Auction> findBySellerId(String sellerId) {
        List<Auction> list = new ArrayList<>();
        String sql = SELECT_FULL + " WHERE a.seller_id = ? ORDER BY a.created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    buildAuction(rs).ifPresent(list::add);
                }
            }
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] findBySellerId error: " + e.getMessage());
        }
        loadBidHistoryBatch(list);
        return list;
    }

    // ─────────────────────────── UPDATE ───────────────────────────

    public void updateStatus(String auctionId, AuctionStatus newStatus, double highestBid,
                              LocalDateTime startTime) {
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

    public void updateEndTime(String auctionId, LocalDateTime endTime) {
        String sql = "UPDATE auctions SET end_time=? WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, endTime != null ? endTime.toString() : null);
            ps.setString(2, auctionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] updateEndTime error: " + e.getMessage());
        }
    }

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

    /**
     * Xây dựng Auction từ ResultSet của JOIN query (không gọi thêm query nào).
     * Bid history CHƯA được load ở đây — caller phải gọi loadBidHistory/loadBidHistoryBatch.
     */
    private Optional<Auction> buildAuction(ResultSet rs) throws SQLException {
        String id = rs.getString("a_id");

        // ── Seller (từ JOIN, không cần query riêng) ──
        Seller seller = new Seller(
                rs.getString("s_id"),
                LocalDateTime.parse(rs.getString("s_created_at")),
                rs.getString("s_username"),
                rs.getString("s_password"),
                rs.getString("s_shop_name")
        );

        // ── Item (từ JOIN, không cần query riêng) ──
        String category = rs.getString("i_category");
        LocalDateTime itemCreatedAt = LocalDateTime.parse(rs.getString("i_created_at"));
        String itemId = rs.getString("i_id");
        String itemName = rs.getString("i_name");
        String itemDesc = rs.getString("i_description");
        double itemPrice = rs.getDouble("i_starting_price");

        Item item = switch (category) {
            case "Điện tử", "Electronics" -> new Electronics(
                    itemId, itemCreatedAt, itemName, itemDesc, itemPrice, seller);
            case "Nghệ thuật", "Art" -> new Art(
                    itemId, itemCreatedAt, itemName, itemDesc, itemPrice, seller,
                    rs.getString("i_artist_name"));
            case "Xe cộ", "Vehicle" -> new Vehicle(
                    itemId, itemCreatedAt, itemName, itemDesc, itemPrice, seller);
            default -> new Electronics(itemId, itemCreatedAt, itemName, itemDesc, itemPrice, seller);
        };
        item.setImageUrl(rs.getString("i_image_url"));

        // ── Auction status ──
        String statusStr = rs.getString("a_status");
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

        String startStr = rs.getString("a_start_time");
        LocalDateTime startTime   = startStr != null ? LocalDateTime.parse(startStr) : null;
        LocalDateTime endTime     = LocalDateTime.parse(rs.getString("a_end_time"));
        LocalDateTime createdAt   = LocalDateTime.parse(rs.getString("a_created_at"));
        double highestBid         = rs.getDouble("a_highest_bid");

        return Optional.of(new Auction(id, createdAt, seller, item, status, highestBid, startTime, endTime));
    }

    // ─────────────────────────── Bid History ───────────────────────────

    /**
     * Load bid history cho 1 auction (2 queries total khi gọi findById).
     */
    private void loadBidHistory(Auction auction, String auctionId) {
        String sql = """
            SELECT bt.id, bt.bidder_id, bt.amount, bt.created_at,
                   u.username, u.password, u.balance, u.frozen_balance, u.created_at AS user_created
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
                    auction.injectBid(mapBid(rs, auction));
                }
            }
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] loadBidHistory error: " + e.getMessage());
        }
    }

    /**
     * Batch load bid history cho nhiều auctions trong 1 query duy nhất.
     * Thay vì N queries (1 cho mỗi auction), chỉ cần 1 query với IN clause.
     * Đây là giải pháp chính cho vấn đề N+1 khi gọi findAll().
     */
    private void loadBidHistoryBatch(List<Auction> auctions) {
        if (auctions.isEmpty()) return;

        // Tạo map để lookup auction nhanh
        Map<String, Auction> auctionMap = new HashMap<>();
        for (Auction a : auctions) {
            auctionMap.put(a.getId(), a);
        }

        // Tạo IN clause: (?, ?, ?, ...)
        String placeholders = "?,".repeat(auctions.size());
        placeholders = placeholders.substring(0, placeholders.length() - 1); // bỏ dấu , cuối

        String sql = """
            SELECT bt.id, bt.bidder_id, bt.auction_id, bt.amount, bt.created_at,
                   u.username, u.password, u.balance, u.frozen_balance, u.created_at AS user_created
            FROM bid_transactions bt
            JOIN users u ON bt.bidder_id = u.id
            WHERE bt.auction_id IN (""" + placeholders + """
            )
            ORDER BY bt.auction_id, bt.created_at ASC
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // Bind tất cả auction ID
            for (int i = 0; i < auctions.size(); i++) {
                ps.setString(i + 1, auctions.get(i).getId());
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String auctionId = rs.getString("auction_id");
                    Auction auction = auctionMap.get(auctionId);
                    if (auction != null) {
                        auction.injectBid(mapBid(rs, auction));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[AuctionRepo] loadBidHistoryBatch error: " + e.getMessage());
        }
    }

    /**
     * Map 1 row từ bid_transactions JOIN users thành BidTransaction.
     */
    private BidTransaction mapBid(ResultSet rs, Auction auction) throws SQLException {
        double balance = rs.getDouble("balance");
        double frozen;
        try {
            frozen = rs.getDouble("frozen_balance");
        } catch (SQLException e) {
            frozen = 0.0; // backward compat nếu cột chưa tồn tại
        }
        Bidder bidder = new Bidder(
                rs.getString("bidder_id"),
                LocalDateTime.parse(rs.getString("user_created")),
                rs.getString("username"),
                rs.getString("password"),
                balance,
                frozen
        );
        return new BidTransaction(
                rs.getString("id"),
                LocalDateTime.parse(rs.getString("created_at")),
                bidder,
                auction,
                rs.getDouble("amount")
        );
    }
}
