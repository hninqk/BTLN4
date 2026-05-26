package com.auction.util;

import javafx.application.Platform;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NotificationManager – singleton lưu tối đa 15 thông báo gần nhất.
 *
 * Cung cấp:
 *  - addNotification(content) để push thông báo từ bất kỳ thread nào.
 *  - Listener pattern để DesktopHeaderController nhận cập nhật UI.
 *  - Static factory methods sinh nội dung theo vai trò (Bidder/Seller/Admin).
 */
public class NotificationManager {

    // ── Data record ─────────────────────────────────────────────────────────────

    public record AppNotification(
            String id,
            String content,
            LocalDateTime time,
            boolean read
    ) {
        public AppNotification markRead() {
            return new AppNotification(id, content, time, true);
        }
    }

    // ── Listener ─────────────────────────────────────────────────────────────────

    public interface NotificationListener {
        /** Called on the JavaFX Application Thread when a single notification is added. */
        void onNotificationAdded(AppNotification notification);
        /** Called when all notifications are marked as read. */
        void onAllRead();
    }

    // ── Singleton ─────────────────────────────────────────────────────────────────

    private static volatile NotificationManager instance;

    public static NotificationManager getInstance() {
        if (instance == null) {
            synchronized (NotificationManager.class) {
                if (instance == null) instance = new NotificationManager();
            }
        }
        return instance;
    }

    // ── State ────────────────────────────────────────────────────────────────────

    private static final int MAX_NOTIFICATIONS = 15;

    /** Head = newest notification. */
    private final Deque<AppNotification> notifications = new ArrayDeque<>();

    private final CopyOnWriteArrayList<NotificationListener> listeners = new CopyOnWriteArrayList<>();

    private NotificationManager() {}

    // ── API ──────────────────────────────────────────────────────────────────────

    /** Push a new notification (safe to call from any thread). */
    public void addNotification(String content) {
        AppNotification notif = new AppNotification(
                UUID.randomUUID().toString(),
                content,
                TimeSyncManager.getNow(),
                false
        );
        synchronized (notifications) {
            notifications.addFirst(notif);
            while (notifications.size() > MAX_NOTIFICATIONS) {
                notifications.removeLast();
            }
        }
        dispatchAdded(notif);
    }

    /** Return a snapshot of notifications (newest first). */
    public List<AppNotification> getNotifications() {
        synchronized (notifications) {
            return new ArrayList<>(notifications);
        }
    }

    /** Count unread notifications. */
    public long getUnreadCount() {
        synchronized (notifications) {
            return notifications.stream().filter(n -> !n.read()).count();
        }
    }

    /** Mark all notifications as read. */
    public void markAllRead() {
        synchronized (notifications) {
            List<AppNotification> updated = notifications.stream()
                    .map(AppNotification::markRead)
                    .toList();
            notifications.clear();
            notifications.addAll(updated);
        }
        dispatchAllRead();
    }

    /** Register a listener. */
    public void addListener(NotificationListener listener) {
        listeners.add(listener);
    }

    /** Unregister a listener. */
    public void removeListener(NotificationListener listener) {
        listeners.remove(listener);
    }

    // ── Dispatch ─────────────────────────────────────────────────────────────────

    private void dispatchAdded(AppNotification notif) {
        if (Platform.isFxApplicationThread()) {
            for (NotificationListener l : listeners) l.onNotificationAdded(notif);
        } else {
            Platform.runLater(() -> {
                for (NotificationListener l : listeners) l.onNotificationAdded(notif);
            });
        }
    }

    private void dispatchAllRead() {
        if (Platform.isFxApplicationThread()) {
            for (NotificationListener l : listeners) l.onAllRead();
        } else {
            Platform.runLater(() -> {
                for (NotificationListener l : listeners) l.onAllRead();
            });
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // ROLE-BASED CONTENT FACTORY METHODS
    // ════════════════════════════════════════════════════════════════════════════

    // ── Bidder events ─────────────────────────────────────────────────────────────

    /** Bidder: có phiên đấu giá mới mở. */
    public static String bidderNewAuction(String itemName) {
        return "[Sản phẩm mới] Phiên đấu giá cho sản phẩm \"" + itemName
                + "\" đã chính thức mở. Tham gia đặt giá ngay!";
    }

    /** Bidder: bị người khác vượt giá. */
    public static String bidderOutbid(String itemName) {
        return "[Vượt giá] Cảnh báo! Một bidder khác đã trả giá cao hơn bạn tại sản phẩm \""
                + itemName + "\".";
    }

    /** Bidder thắng cuộc. */
    public static String bidderWon(String itemName, double finalPrice) {
        return "[Chúc mừng] Bạn đã THẮNG CUỘC phiên đấu giá sản phẩm \""
                + itemName + "\" với mức giá chốt là "
                + String.format("%,.0f", finalPrice) + "đ!";
    }

    /** Bidder thua cuộc. */
    public static String bidderLost(String itemName, String winnerName, double finalPrice) {
        return "[Kết thúc] Phiên đấu giá \"" + itemName
                + "\" đã khép lại. Người thắng cuộc: " + winnerName
                + " với giá " + String.format("%,.0f", finalPrice) + "đ.";
    }

    // ── Seller events ─────────────────────────────────────────────────────────────

    /** Seller: Admin duyệt sản phẩm. */
    public static String sellerApproved(String itemName) {
        return "[Phê duyệt] Sản phẩm \"" + itemName
                + "\" của bạn đã được Admin duyệt và đưa vào sàn.";
    }

    /** Seller: Admin hủy phiên đấu giá. */
    public static String sellerCanceled(String itemName) {
        return "[Hủy phiên] Ban quản trị đã hủy phiên đấu giá sản phẩm \""
                + itemName + "\" của bạn do vi phạm điều khoản.";
    }

    /** Seller: có người đặt giá mới. */
    public static String sellerNewBid(String itemName, double amount, String bidderName) {
        return "[Lượt đặt giá] Sản phẩm \"" + itemName
                + "\" của bạn vừa nhận được một mức giá mới: "
                + String.format("%,.0f", amount) + "đ từ người dùng " + bidderName + ".";
    }

    /** Seller: phiên đấu giá kết thúc. */
    public static String sellerAuctionClosed(String itemName, double finalPrice, String winnerName) {
        return "[Kết thúc] Phiên đấu giá sản phẩm \"" + itemName
                + "\" của bạn đã hoàn thành. Giá chốt: "
                + String.format("%,.0f", finalPrice) + "đ. Người mua: " + winnerName + ".";
    }

    // ── Admin events ─────────────────────────────────────────────────────────────

    /** Admin: có sản phẩm mới cần duyệt. */
    public static String adminPendingApproval(String sellerName, String itemName) {
        return "[Yêu cầu duyệt] Người bán " + sellerName
                + " vừa đăng bán một sản phẩm mới: \"" + itemName
                + "\". Vui lòng kiểm tra và phê duyệt.";
    }
}
