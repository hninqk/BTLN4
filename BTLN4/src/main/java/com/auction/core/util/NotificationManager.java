package com.auction.core.util;

import javafx.application.Platform;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationManager {

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

    public interface NotificationListener {

        void onNotificationAdded(AppNotification notification);

        void onAllRead();
    }

    private static volatile NotificationManager instance;

    public static NotificationManager getInstance() {
        if (instance == null) {
            synchronized (NotificationManager.class) {
                if (instance == null) instance = new NotificationManager();
            }
        }
        return instance;
    }

    private static final int MAX_NOTIFICATIONS = 20;

    private final Deque<AppNotification> notifications = new ArrayDeque<>();

    private final CopyOnWriteArrayList<NotificationListener> listeners = new CopyOnWriteArrayList<>();

    private NotificationManager() {}

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

    public void addReadNotification(String content) {
        AppNotification notif = new AppNotification(
                UUID.randomUUID().toString(),
                content,
                TimeSyncManager.getNow(),
                true
        );
        synchronized (notifications) {
            notifications.addLast(notif);
            while (notifications.size() > MAX_NOTIFICATIONS) {
                notifications.removeLast();
            }
        }
        dispatchAdded(notif);
    }

    public boolean containsOutbidFor(String itemName) {
        String expectedMsg = outbidMessage(itemName);
        synchronized (notifications) {
            for (AppNotification n : notifications) {
                if (n.content().equals(expectedMsg)) return true;
            }
            return false;
        }
    }

    public List<AppNotification> getNotifications() {
        synchronized (notifications) {
            return new ArrayList<>(notifications);
        }
    }

    public long getUnreadCount() {
        synchronized (notifications) {
            return notifications.stream().filter(n -> !n.read()).count();
        }
    }

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

    public void addListener(NotificationListener listener) {
        listeners.add(listener);
    }

    public void removeListener(NotificationListener listener) {
        listeners.remove(listener);
    }

    public void clear() {
        synchronized (notifications) {
            notifications.clear();
        }
        dispatchAllRead();
    }

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

    public static String outbidMessage(String itemName) {
        return "[Vượt giá] Cảnh báo! Một bidder khác đã trả giá cao hơn bạn tại sản phẩm \""
                + itemName + "\".";
    }
}
