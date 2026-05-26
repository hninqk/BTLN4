package com.auction.model;

/**
 * NotificationType - Các loại thông báo trong hệ thống
 */
public enum NotificationType {
    // Bidder notifications
    BID_OUTBID("Bid của bạn đã bị vượt"),
    AUCTION_ENDED_WON("Bạn đã thắng đấu giá"),
    AUCTION_ENDED_LOST("Đấu giá đã kết thúc"),

    // Seller notifications
    AUCTION_APPROVED("Phiên đấu giá đã được duyệt"),
    AUCTION_REJECTED("Phiên đấu giá đã bị từ chối"),
    NEW_BID("Có người đặt giá mới"),
    AUCTION_ENDED_SOLD("Sản phẩm đã được bán"),
    AUCTION_ENDED_UNSOLD("Sản phẩm chưa được bán"),

    // Admin notifications
    NEW_AUCTION_PENDING("Phiên đấu giá mới chờ duyệt");

    private final String displayName;

    NotificationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
