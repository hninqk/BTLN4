package com.auction.repository;

import com.auction.model.*;
import com.auction.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JdbcUserRepository – CRUD for users table.
 * Handles Admin, Seller, Bidder polymorphism via the 'role' column.
 */
public class JdbcUserRepository {

    // ─────────────────────────── CREATE ───────────────────────────

    public void save(User user) {
        String sql;
        if (DatabaseConnection.isPostgres()) {
            sql = """
                INSERT INTO users
                    (id, username, password, role, balance, frozen_balance, shop_name, rating, cntvoted, access_level, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO NOTHING
                """;
        } else {
            sql = """
                INSERT OR IGNORE INTO users
                    (id, username, password, role, balance, frozen_balance, shop_name, rating, cntvoted, access_level, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getRole());

            if (user instanceof Bidder b) {
                ps.setDouble(5, b.getAccountBalance());
                ps.setDouble(6, b.getFrozenBalance());
                ps.setNull(7, Types.VARCHAR);
                ps.setDouble(8, 0.0);
                ps.setInt(9, 0);
                ps.setInt(10, 1);
            } else if (user instanceof Seller s) {
                ps.setDouble(5, 0.0);
                ps.setDouble(6, 0.0);
                ps.setString(7, s.getShopName());
                ps.setDouble(8, s.getRating());
                ps.setInt(9, s.getCntvoted());
                ps.setInt(10, 1);
            } else if (user instanceof Admin a) {
                ps.setDouble(5, 0.0);
                ps.setDouble(6, 0.0);
                ps.setNull(7, Types.VARCHAR);
                ps.setDouble(8, 0.0);
                ps.setInt(9, 0);
                ps.setInt(10, a.getAccessLevel());
            } else {
                ps.setDouble(5, 0.0);
                ps.setDouble(6, 0.0);
                ps.setNull(7, Types.VARCHAR);
                ps.setDouble(8, 0.0);
                ps.setInt(9, 0);
                ps.setInt(10, 1);
            }

            ps.setString(11, user.getCreatedAt().toString());
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[UserRepo] save error: " + e.getMessage());
        }
    }

    // ─────────────────────────── READ ───────────────────────────

    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[UserRepo] findAll error: " + e.getMessage());
        }
        return users;
    }

    public Optional<User> findById(String id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[UserRepo] findById error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[UserRepo] findByUsername error: " + e.getMessage());
        }
        return Optional.empty();
    }

    // ─────────────────────────── UPDATE ───────────────────────────

    public void update(User user) {
        String sql = """
            UPDATE users SET
                username=?, password=?, balance=?, frozen_balance=?, shop_name=?,
                rating=?, cntvoted=?, access_level=?
            WHERE id=?
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());

            if (user instanceof Bidder b) {
                ps.setDouble(3, b.getAccountBalance());
                ps.setDouble(4, b.getFrozenBalance());
                ps.setNull(5, Types.VARCHAR);
                ps.setDouble(6, 0.0);
                ps.setInt(7, 0);
                ps.setInt(8, 1);
            } else if (user instanceof Seller s) {
                ps.setDouble(3, 0.0);
                ps.setDouble(4, 0.0);
                ps.setString(5, s.getShopName());
                ps.setDouble(6, s.getRating());
                ps.setInt(7, s.getCntvoted());
                ps.setInt(8, 1);
            } else if (user instanceof Admin a) {
                ps.setDouble(3, 0.0);
                ps.setDouble(4, 0.0);
                ps.setNull(5, Types.VARCHAR);
                ps.setDouble(6, 0.0);
                ps.setInt(7, 0);
                ps.setInt(8, a.getAccessLevel());
            } else {
                ps.setDouble(3, 0.0);
                ps.setDouble(4, 0.0);
                ps.setNull(5, Types.VARCHAR);
                ps.setDouble(6, 0.0);
                ps.setInt(7, 0);
                ps.setInt(8, 1);
            }

            ps.setString(9, user.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[UserRepo] update error: " + e.getMessage());
        }
    }

    /**
     * Atomic update chỉ frozen_balance – dùng trong placeBid() để không
     * ghi đè các field khác trong lúc transaction đang xử lý.
     *
     * @param userId       ID của bidder
     * @param frozenBalance giá trị frozenBalance mới
     */
    public void updateFrozenBalance(String userId, double frozenBalance) {
        String sql = "UPDATE users SET frozen_balance = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, Math.max(0.0, frozenBalance));
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[UserRepo] updateFrozenBalance error: " + e.getMessage());
        }
    }

    // ─────────────────────────── DELETE ───────────────────────────

    public boolean deleteById(String id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserRepo] deleteById error: " + e.getMessage());
        }
        return false;
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[UserRepo] existsByUsername error: " + e.getMessage());
        }
        return false;
    }

    // ─────────────────────────── MAPPING ───────────────────────────

    private User mapRow(ResultSet rs) throws SQLException {
        String id        = rs.getString("id");
        String username  = rs.getString("username");
        String password  = rs.getString("password");
        String role      = rs.getString("role");
        LocalDateTime createdAt = LocalDateTime.parse(rs.getString("created_at"));

        return switch (role) {
            case "Bidder" -> {
                double balance = rs.getDouble("balance");
                double frozen  = safeGetDouble(rs, "frozen_balance", 0.0);
                yield new Bidder(id, createdAt, username, password, balance, frozen);
            }
            case "Seller" -> {
                Seller s = new Seller(id, createdAt, username, password,
                                      rs.getString("shop_name"),
                                      rs.getDouble("rating"),
                                      rs.getInt("cntvoted"));
                yield s;
            }
            case "Admin" -> {
                Admin a = new Admin(id, createdAt, username, password,
                                    rs.getInt("access_level"));
                yield a;
            }
            default -> throw new SQLException("Unknown role: " + role);
        };
    }

    /**
     * Đọc giá trị double từ ResultSet, trả về defaultValue nếu cột chưa tồn tại
     * (backward compatibility khi DB cũ chưa có cột frozen_balance).
     */
    private double safeGetDouble(ResultSet rs, String column, double defaultValue) {
        try {
            return rs.getDouble(column);
        } catch (SQLException e) {
            return defaultValue;
        }
    }
}
