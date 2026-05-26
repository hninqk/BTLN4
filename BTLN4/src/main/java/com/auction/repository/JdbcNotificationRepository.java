package com.auction.repository;

import com.auction.model.Notification;
import com.auction.model.NotificationType;
import com.auction.util.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * JdbcNotificationRepository - Repository để quản lý notifications trong database
 */
public class JdbcNotificationRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcNotificationRepository.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Tạo notification mới
     */
    public void create(Notification notification) {
        String sql = "INSERT INTO notifications (id, user_id, type, message, auction_id, is_read, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, notification.getId());
            ps.setString(2, notification.getUserId());
            ps.setString(3, notification.getType().name());
            ps.setString(4, notification.getMessage());
            ps.setString(5, notification.getAuctionId());
            ps.setBoolean(6, notification.isRead());
            ps.setString(7, notification.getCreatedAt().format(FORMATTER));

            ps.executeUpdate();
            log.debug("Created notification: {}", notification.getId());

        } catch (SQLException e) {
            log.error("Failed to create notification", e);
            throw new RuntimeException("Failed to create notification", e);
        }
    }

    /**
     * Lấy 50 notifications gần nhất của user
     */
    public List<Notification> findByUserId(String userId, int limit) {
        String sql = "SELECT * FROM notifications WHERE user_id = ? " +
                     "ORDER BY created_at DESC LIMIT ?";

        List<Notification> notifications = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    notifications.add(mapRow(rs));
                }
            }

        } catch (SQLException e) {
            log.error("Failed to find notifications for user: {}", userId, e);
        }

        return notifications;
    }

    /**
     * Đếm số notifications chưa đọc của user
     */
    public int countUnread(String userId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            log.error("Failed to count unread notifications for user: {}", userId, e);
        }

        return 0;
    }

    /**
     * Đánh dấu notification đã đọc
     */
    public void markAsRead(String notificationId) {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, notificationId);
            ps.executeUpdate();
            log.debug("Marked notification as read: ", notificationId);

        } catch (SQLException e) {
            log.error("Failed to mark notification as read: {}", notificationId, e);
        }
    }

    /**
     * Đánh dấu tất cả notifications của user đã đọc
     */
    public void markAllAsRead(String userId) {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE user_id = ? AND is_read = FALSE";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            int updated = ps.executeUpdate();
            log.debug("Marked {} notifications as read for user: {}", updated, userId);

        } catch (SQLException e) {
            log.error("Failed to mark all notifications as read for user: {}", userId, e);
        }
    }

    /**
     * Xóa notification
     */
    public void delete(String notificationId) {
        String sql = "DELETE FROM notifications WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, notificationId);
            ps.executeUpdate();
            log.debug("Deleted notification: {}", notificationId);

        } catch (SQLException e) {
            log.error("Failed to delete notification: {}", notificationId, e);
        }
    }

    /**
     * Map ResultSet row to Notification object
     */
    private Notification mapRow(ResultSet rs) throws SQLException {
        Notification notification = new Notification();
        notification.setId(rs.getString("id"));
        notification.setUserId(rs.getString("user_id"));
        notification.setType(NotificationType.valueOf(rs.getString("type")));
        notification.setMessage(rs.getString("message"));
        notification.setAuctionId(rs.getString("auction_id"));
        notification.setRead(rs.getBoolean("is_read"));
        notification.setCreatedAt(LocalDateTime.parse(rs.getString("created_at"), FORMATTER));
        return notification;
    }
}
