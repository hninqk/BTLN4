package com.auction.infra.util;

import javafx.application.Platform;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NotificationManager – singleton lưu thông báo mới nhất.
 *
 * Cung cấp:
 *  - addNotification(content) để push thông báo từ bất kỳ thread nào.
 *  - Listener pattern để DesktopHeaderController nhận cập nhật UI.
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

    private static final int MAX_NOTIFICATIONS = 20;

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

    /** Push a previously read notification (used for restoring state on login). */
    public void addReadNotification(String content) {
        AppNotification notif = new AppNotification(
                UUID.randomUUID().toString(),
                content,
                TimeSyncManager.getNow(),
                true
        );
        synchronized (notifications) {
            notifications.addLast(notif); // Add to end so it doesn't push down new ones
            while (notifications.size() > MAX_NOTIFICATIONS) {
                notifications.removeLast();
            }
        }
        dispatchAdded(notif);
    }

    /** Checks if an outbid notification for this item already exists. */
    public boolean containsOutbidFor(String itemName) {
        String expectedMsg = outbidMessage(itemName);
        synchronized (notifications) {
            for (AppNotification n : notifications) {
                if (n.content().equals(expectedMsg)) return true;
            }
            return false;
        }
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

    /** Clear all notifications (used on logout). */
    public void clear() {
        synchronized (notifications) {
            notifications.clear();
        }
        dispatchAllRead();
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
    // CONTENT FACTORY
    // ════════════════════════════════════════════════════════════════════════════

    /** Thông báo vượt giá. */
    public static String outbidMessage(String itemName) {
        return "[Vượt giá] Cảnh báo! Một bidder khác đã trả giá cao hơn bạn tại sản phẩm \""
                + itemName + "\".";
    }
}
