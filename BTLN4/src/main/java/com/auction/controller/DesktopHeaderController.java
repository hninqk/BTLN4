package com.auction.controller;

import com.auction.service.NotificationHub;
import com.auction.util.SessionManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller cho DesktopHeader (Global Component).
 * Quản lý đồng hồ thời gian thực và khay thông báo (Notification Tray).
 */
public class DesktopHeaderController implements NotificationHub.Listener {

    @FXML
    private Label clockLabel;
    @FXML
    private Label notificationBadge;
    @FXML
    private HBox bellIconContainer;

    // Popup tray
    @FXML
    private VBox notificationPopup;
    @FXML
    private ListView<NotificationHub.NotificationEvent> notificationList;
    @FXML
    private Label emptyNotificationLabel;

    private Timeline clock;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss - dd/MM/yyyy");

    // Lưu tối đa 15 thông báo
    private final ObservableList<NotificationHub.NotificationEvent> notifications = FXCollections.observableArrayList();
    private int unreadCount = 0;

    @FXML
    public void initialize() {
        // Khởi tạo đồng hồ
        startClock();

        // Cấu hình danh sách thông báo
        notificationList.setItems(notifications);
        notificationList.setCellFactory(lv -> new NotificationCell());

        // Ẩn popup mặc định
        notificationPopup.setVisible(false);
        notificationPopup.setManaged(false);

        updateBadge();
        updateEmptyState();

        // Xử lý sự kiện click chuông
        bellIconContainer.setOnMouseClicked(e -> toggleNotificationPopup());

        // Đăng ký nhận thông báo (chỉ gọi 1 lần khi load component)
        NotificationHub.getInstance().addListener(this);

        // Đảm bảo kết nối
        NotificationHub.getInstance().ensureConnected();
    }

    private void startClock() {
        clock = new Timeline(new KeyFrame(Duration.ZERO, e -> {
            LocalDateTime now = com.auction.util.TimeSyncManager.getNow();
            clockLabel.setText(now.format(formatter));
        }), new KeyFrame(Duration.seconds(1)));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    @Override
    public void onNotification(NotificationHub.NotificationEvent event) {
        // Nhận event từ NotificationHub (đã được đảm bảo chạy trên FX Thread)
        // Thêm vào đầu list
        notifications.add(0, event);

        // Giới hạn 15 thông báo
        if (notifications.size() > 15) {
            notifications.remove(15, notifications.size());
        }

        // Micro-animation for bell icon (shake/pulse)
        javafx.animation.RotateTransition rt = new javafx.animation.RotateTransition(Duration.millis(80),
                bellIconContainer);
        rt.setByAngle(15);
        rt.setCycleCount(6);
        rt.setAutoReverse(true);
        rt.play();

        // Slide-in / auto-open notification tray
        if (!notificationPopup.isVisible()) {
            notificationPopup.setVisible(true);
            notificationPopup.setManaged(true);
            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.millis(250),
                    notificationPopup);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();
            unreadCount = 0; // reset since it auto-opened
        } else {
            unreadCount++;
        }

        updateBadge();
        updateEmptyState();
    }

    private void toggleNotificationPopup() {
        boolean isVisible = !notificationPopup.isVisible();
        notificationPopup.setVisible(isVisible);
        notificationPopup.setManaged(isVisible);

        if (isVisible) {
            // Khi mở ra thì reset số lượng chưa đọc
            unreadCount = 0;
            updateBadge();
        }
    }

    private void updateBadge() {
        if (unreadCount > 0) {
            notificationBadge.setText(unreadCount > 9 ? "9+" : String.valueOf(unreadCount));
            notificationBadge.setVisible(true);
            notificationBadge.setManaged(true);
        } else {
            notificationBadge.setVisible(false);
            notificationBadge.setManaged(false);
        }
    }

    private void updateEmptyState() {
        boolean isEmpty = notifications.isEmpty();
        emptyNotificationLabel.setVisible(isEmpty);
        emptyNotificationLabel.setManaged(isEmpty);
        notificationList.setVisible(!isEmpty);
        notificationList.setManaged(!isEmpty);
    }

    @FXML
    private void handleClearNotifications() {
        notifications.clear();
        updateEmptyState();
        unreadCount = 0;
        updateBadge();
        notificationPopup.setVisible(false);
        notificationPopup.setManaged(false);
    }

    /**
     * Dọn dẹp resource (chủ yếu là tắt đồng hồ, hủy đăng ký Listener)
     * Gọi khi Scene chứa Header bị thay thế hoàn toàn.
     * Vì JavaFX không có hook tự động cho sự kiện "component destroyed",
     * ta có thể phụ thuộc vào việc hệ thống không giữ strong reference,
     * nhưng để tránh memory leak từ NotificationHub, ta NÊN remove Listener.
     */
    public void cleanup() {
        if (clock != null)
            clock.stop();
        NotificationHub.getInstance().removeListener(this);
    }

    // ── Custom ListCell ──────────────────────────────────────────────────────

    private static class NotificationCell extends ListCell<NotificationHub.NotificationEvent> {
        private final VBox root;
        private final Label titleLabel;
        private final Label timeLabel;
        private final Label msgLabel;
        private final FontIcon icon;
        private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm - dd/MM");

        public NotificationCell() {
            root = new VBox(4);
            root.getStyleClass().add("notification-item");

            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);

            icon = new FontIcon();
            icon.setIconSize(16);

            titleLabel = new Label();
            titleLabel.getStyleClass().add("notification-title");

            timeLabel = new Label();
            timeLabel.getStyleClass().add("notification-time");

            header.getChildren().addAll(icon, titleLabel, timeLabel);

            msgLabel = new Label();
            msgLabel.getStyleClass().add("notification-msg");
            msgLabel.setWrapText(true);

            root.getChildren().addAll(header, msgLabel);

            // Xử lý background màu luân phiên / hover qua CSS
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(NotificationHub.NotificationEvent item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                msgLabel.setText(item.message());
                timeLabel.setText(item.timestamp().format(timeFmt));

                // Set style dựa trên type
                icon.getStyleClass().removeAll("icon-danger", "icon-success", "icon-warning", "icon-info");

                switch (item.type()) {
                    case OUTBID -> {
                        titleLabel.setText("Bị Vượt Giá");
                        icon.setIconLiteral("fas-exclamation-circle");
                        icon.getStyleClass().add("icon-warning");
                    }
                    case AUCTION_END -> {
                        titleLabel.setText("Phiên Kết Thúc");
                        icon.setIconLiteral("fas-gavel");
                        icon.getStyleClass().add("icon-info");
                    }
                    case AUCTION_APPROVED -> {
                        titleLabel.setText("Sản Phẩm Đã Duyệt");
                        icon.setIconLiteral("fas-check-circle");
                        icon.getStyleClass().add("icon-success");
                    }
                    case AUCTION_CANCELED -> {
                        titleLabel.setText("Phiên Bị Hủy");
                        icon.setIconLiteral("fas-times-circle");
                        icon.getStyleClass().add("icon-danger");
                    }
                }

                setGraphic(root);
            }
        }
    }
}
