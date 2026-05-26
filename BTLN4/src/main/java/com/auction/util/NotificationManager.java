package com.auction.util;

import com.auction.model.Notification;
import com.auction.model.NotificationType;
// import com.auction.server.NotificationWebSocketHandler;
import com.auction.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NotificationManager - Singleton để tạo và broadcast notifications
 * NOTE: WebSocket broadcasting temporarily disabled
 */
public class NotificationManager {

    private static final Logger log = LoggerFactory.getLogger(NotificationManager.class);
    private static NotificationManager instance;

    private final NotificationService notificationService;
    // private NotificationWebSocketHandler wsHandler;

    private NotificationManager() {
        this.notificationService = new NotificationService();
    }

    public static synchronized NotificationManager getInstance() {
        if (instance == null) {
            instance = new NotificationManager();
        }
        return instance;
    }

    /**
     * Set WebSocket handler (called from ServerMain)
     */
    // public void setWebSocketHandler(NotificationWebSocketHandler handler) {
    //     this.wsHandler = handler;
    // }

    /**
     * Create and broadcast notification
     */
    public void sendNotification(String userId, NotificationType type, String message, String auctionId) {
        try {
            // Create notification in database
            Notification notification = notificationService.createNotification(userId, type, message, auctionId);

            // Broadcast via WebSocket if handler is set
            // if (wsHandler != null) {
            //     wsHandler.broadcastNotification(notification);
            // }

            log.info("Sent notification to user {}: {}", userId, type);
        } catch (Exception e) {
            log.error("Failed to send notification", e);
        }
    }

    /**
     * Notify bidder that they were outbid
     */
    public void notifyBidOutbid(String bidderId, String auctionId, String itemName, double newBid) {
        String message = String.format("Giá đặt của bạn cho '%s' đã bị vượt. Giá mới: %,.0f ₫", itemName, newBid);
        sendNotification(bidderId, NotificationType.BID_OUTBID, message, auctionId);
    }

    /**
     * Notify bidder that auction ended (won)
     */
    public void notifyAuctionEndedWon(String bidderId, String auctionId, String itemName, double finalPrice) {
        String message = String.format("Chúc mừng! Bạn đã thắng đấu giá '%s' với giá %,.0f ₫", itemName, finalPrice);
        sendNotification(bidderId, NotificationType.AUCTION_ENDED_WON, message, auctionId);
    }

    /**
     * Notify bidder that auction ended (lost)
     */
    public void notifyAuctionEndedLost(String bidderId, String auctionId, String itemName) {
        String message = String.format("Đấu giá '%s' đã kết thúc. Rất tiếc bạn không thắng lần này.", itemName);
        sendNotification(bidderId, NotificationType.AUCTION_ENDED_LOST, message, auctionId);
    }

    /**
     * Notify seller that auction was approved
     */
    public void notifyAuctionApproved(String sellerId, String auctionId, String itemName) {
        String message = String.format("Phiên đấu giá '%s' đã được duyệt và sẵn sàng bắt đầu.", itemName);
        sendNotification(sellerId, NotificationType.AUCTION_APPROVED, message, auctionId);
    }

    /**
     * Notify seller that auction was rejected
     */
    public void notifyAuctionRejected(String sellerId, String auctionId, String itemName) {
        String message = String.format("Phiên đấu giá '%s' đã bị từ chối.", itemName);
        sendNotification(sellerId, NotificationType.AUCTION_REJECTED, message, auctionId);
    }

    /**
     * Notify seller of new bid
     */
    public void notifyNewBid(String sellerId, String auctionId, String itemName, double bidAmount, String bidderName) {
        String message = String.format("%s đã đặt giá %,.0f ₫ cho '%s'", bidderName, bidAmount, itemName);
        sendNotification(sellerId, NotificationType.NEW_BID, message, auctionId);
    }

    /**
     * Notify seller that auction ended (sold)
     */
    public void notifyAuctionEndedSold(String sellerId, String auctionId, String itemName, double finalPrice) {
        String message = String.format("Sản phẩm '%s' đã được bán với giá %,.0f ₫", itemName, finalPrice);
        sendNotification(sellerId, NotificationType.AUCTION_ENDED_SOLD, message, auctionId);
    }

    /**
     * Notify seller that auction ended (unsold)
     */
    public void notifyAuctionEndedUnsold(String sellerId, String auctionId, String itemName) {
        String message = String.format("Phiên đấu giá '%s' đã kết thúc nhưng chưa có người mua.", itemName);
        sendNotification(sellerId, NotificationType.AUCTION_ENDED_UNSOLD, message, auctionId);
    }

    /**
     * Notify all admins of new pending auction
     */
    public void notifyAdminsNewAuction(String auctionId, String itemName, String sellerName) {
        String message = String.format("%s đã đăng bán '%s' - Chờ duyệt", sellerName, itemName);

        // Get all admin users and send notification to each
        try {
            com.auction.service.UserService.getInstance()
                .getAllUsers()
                .stream()
                .filter(user -> "ADMIN".equals(user.getRole()))
                .forEach(admin -> sendNotification(admin.getId(), NotificationType.NEW_AUCTION_PENDING, message, auctionId));
        } catch (Exception e) {
            log.error("Failed to notify admins", e);
        }
    }
}
