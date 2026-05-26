package com.auction.model;

import java.time.LocalDateTime;

/**
 * Notification - Model đại diện cho thông báo trong hệ thống
 */
public class Notification {
    private String id;
    private String userId;
    private NotificationType type;
    private String message;
    private String auctionId;
    private boolean isRead;
    private LocalDateTime createdAt;

    public Notification() {
        this.id = java.util.UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.isRead = false;
    }

    public Notification(String userId, NotificationType type, String message, String auctionId) {
        this();
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.auctionId = auctionId;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Notification{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", type=" + type +
                ", message='" + message + '\'' +
                ", auctionId='" + auctionId + '\'' +
                ", isRead=" + isRead +
                ", createdAt=" + createdAt +
                '}';
    }
}
