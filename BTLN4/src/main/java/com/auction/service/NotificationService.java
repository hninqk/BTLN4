package com.auction.service;

import com.auction.model.Notification;
import com.auction.model.NotificationType;
import com.auction.repository.JdbcNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * NotificationService - Service layer để quản lý notifications
 */
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final JdbcNotificationRepository repository;

    public NotificationService() {
        this.repository = new JdbcNotificationRepository();
    }

    /**
     * Tạo và lưu notification mới
     */
    public Notification createNotification(String userId, NotificationType type, String message, String auctionId) {
        Notification notification = new Notification(userId, type, message, auctionId);
        repository.create(notification);
        log.info("Created notification for user {}: {}", userId, type);
        return notification;
    }

    /**
     * Lấy danh sách notifications của user (50 cái gần nhất)
     */
    public List<Notification> getUserNotifications(String userId) {
        return repository.findByUserId(userId, 50);
    }

    /**
     * Đếm số notifications chưa đọc
     */
    public int getUnreadCount(String userId) {
        return repository.countUnread(userId);
    }

    /**
     * Đánh dấu notification đã đọc
     */
    public void markAsRead(String notificationId) {
        repository.markAsRead(notificationId);
    }

    /**
     * Đánh dấu tất cả notifications đã đọc
     */
    public void markAllAsRead(String userId) {
        repository.markAllAsRead(userId);
    }

    /**
     * Xóa notification
     */
    public void deleteNotification(String notificationId) {
        repository.delete(notificationId);
    }
}
