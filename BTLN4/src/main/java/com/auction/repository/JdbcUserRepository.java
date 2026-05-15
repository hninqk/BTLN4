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
                    (id, username, password, role, balance, shop_name, rating, cntvoted, access_level, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO NOTHING
                """;
        } else {
            sql = """
                INSERT OR IGNORE INTO users
                    (id, username, password, role, balance, shop_name, rating, cntvoted, access_level, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
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
                ps.setNull(6, Types.VARCHAR);
                ps.setDouble(7, 0.0);
                ps.setInt(8, 0);
                ps.setInt(9, 1);
            } else if (user instanceof Seller s) {
                ps.setDouble(5, 0.0);
                ps.setString(6, s.getShopName());
                ps.setDouble(7, s.getRating());
                ps.setInt(8, s.getCntvoted());
                ps.setInt(9, 1);
            } else if (user instanceof Admin a) {
                ps.setDouble(5, 0.0);
                ps.setNull(6, Types.VARCHAR);
                ps.setDouble(7, 0.0);
                ps.setInt(8, 0);
                ps.setInt(9, a.getAccessLevel());
            } else {
                ps.setDouble(5, 0.0);
                ps.setNull(6, Types.VARCHAR);
                ps.setDouble(7, 0.0);
                ps.setInt(8, 0);
                ps.setInt(9, 1);
            }

            ps.setString(10, user.getCreatedAt().toString());
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
                username=?, password=?, balance=?, shop_name=?,
                rating=?, cntvoted=?, access_level=?
            WHERE id=?
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());

            if (user instanceof Bidder b) {
                ps.setDouble(3, b.getAccountBalance());
                ps.setNull(4, Types.VARCHAR);
                ps.setDouble(5, 0.0);
                ps.setInt(6, 0);
                ps.setInt(7, 1);
            } else if (user instanceof Seller s) {
                ps.setDouble(3, 0.0);
                ps.setString(4, s.getShopName());
                ps.setDouble(5, s.getRating());
                ps.setInt(6, s.getCntvoted());
                ps.setInt(7, 1);
            } else if (user instanceof Admin a) {
                ps.setDouble(3, 0.0);
                ps.setNull(4, Types.VARCHAR);
                ps.setDouble(5, 0.0);
                ps.setInt(6, 0);
                ps.setInt(7, a.getAccessLevel());
            } else {
                ps.setDouble(3, 0.0);
                ps.setNull(4, Types.VARCHAR);
                ps.setDouble(5, 0.0);
                ps.setInt(6, 0);
                ps.setInt(7, 1);
            }

            ps.setString(8, user.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[UserRepo] update error: " + e.getMessage());
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
                Bidder b = new Bidder(id, createdAt, username, password,
                                      rs.getDouble("balance"));
                yield b;
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
}
