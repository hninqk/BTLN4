package com.auction.infra.repository;

import com.auction.core.model.*;
import com.auction.infra.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JdbcItemRepository – CRUD for items table.
 * Handles Art, Electronics, Vehicle polymorphism via 'category' column.
 */
public class JdbcItemRepository {

    // ─────────────────────────── CREATE ───────────────────────────

    public void save(Item item, User owner) {
        String sql;
        if (DatabaseConnection.isPostgres()) {
            sql = """
                INSERT INTO items
                    (id, name, description, starting_price, image_url, category,
                     owner_id, artist_name, created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO NOTHING
                """;
        } else {
            sql = """
                INSERT OR IGNORE INTO items
                    (id, name, description, starting_price, image_url, category,
                     owner_id, artist_name, created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, item.getId());
            ps.setString(2, item.getName());
            ps.setString(3, item.getDescription());
            ps.setDouble(4, item.getStartingPrice());
            ps.setString(5, item.getImageUrl());
            ps.setString(6, item.getCategory());
            ps.setString(7, owner.getId());
            ps.setString(9, item.getCreatedAt().toString());

            if (item instanceof Electronics e) {
                ps.setString(8, "");
            } else if (item instanceof Art a) {
                ps.setString(8, a.getArtistName());
            } else if (item instanceof Vehicle v) {
                ps.setString(8, "");
            } else {
                ps.setString(8, "");
            }

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[ItemRepo] save error: " + e.getMessage());
        }
    }

    // ─────────────────────────── READ ───────────────────────────

    public Optional<Item> findById(String id, User owner) {
        String sql = "SELECT * FROM items WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs, owner));
            }
        } catch (SQLException e) {
            System.err.println("[ItemRepo] findById error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<Item> findByOwnerId(String ownerId, User owner) {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE owner_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) items.add(mapRow(rs, owner));
            }
        } catch (SQLException e) {
            System.err.println("[ItemRepo] findByOwnerId error: " + e.getMessage());
        }
        return items;
    }

    // ─────────────────────────── UPDATE ───────────────────────────

    public void update(Item item) {
        String sql = """
            UPDATE items SET
                name=?, description=?, starting_price=?, image_url=?, category=?
            WHERE id=?
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getName());
            ps.setString(2, item.getDescription());
            ps.setDouble(3, item.getStartingPrice());
            ps.setString(4, item.getImageUrl());
            ps.setString(5, item.getCategory());
            ps.setString(6, item.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ItemRepo] update error: " + e.getMessage());
        }
    }

    // ─────────────────────────── DELETE ───────────────────────────

    public boolean deleteById(String id) {
        String sql = "DELETE FROM items WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ItemRepo] deleteById error: " + e.getMessage());
        }
        return false;
    }

    // ─────────────────────────── MAPPING ───────────────────────────

    Item mapRow(ResultSet rs, User owner) throws SQLException {
        String id          = rs.getString("id");
        String name        = rs.getString("name");
        String description = rs.getString("description");
        double price       = rs.getDouble("starting_price");
        String imageUrl    = rs.getString("image_url");
        String category    = rs.getString("category");
        LocalDateTime createdAt = LocalDateTime.parse(rs.getString("created_at"));

        Item item = switch (category) {
            case "Điện tử", "Electronics" -> new Electronics(id, createdAt, name, description, price, owner);
            case "Nghệ thuật", "Art"      -> new Art(id, createdAt, name, description, price, owner,
                                                      rs.getString("artist_name"));
            case "Xe cộ", "Vehicle"       -> new Vehicle(id, createdAt, name, description, price, owner);
            default                        -> new Electronics(id, createdAt, name, description, price, owner);
        };
        item.setImageUrl(imageUrl);
        return item;
    }
}
