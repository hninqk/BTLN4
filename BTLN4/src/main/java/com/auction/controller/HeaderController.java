package com.auction.controller;

// import com.auction.client.NotificationWebSocketClient;
// import com.auction.model.Notification;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * HeaderController - Controller cho sticky header với notification
 * NOTE: Notification features temporarily disabled until WebSocket client is fixed
 */
public class HeaderController {

    private static final Logger log = LoggerFactory.getLogger(HeaderController.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Button notificationButton;
    @FXML private StackPane notificationBadge;
    @FXML private Label badgeLabel;
    @FXML private VBox notificationDropdown;
    @FXML private VBox notificationList;
    @FXML private Button markAllReadButton;

    // private final NotificationWebSocketClient wsClient = NotificationWebSocketClient.getInstance();
    private boolean dropdownVisible = false;

    @FXML
    public void initialize() {
        // Temporarily disable notification features
        if (notificationButton != null) {
            notificationButton.setVisible(false);
            notificationButton.setManaged(false);
        }
        if (notificationDropdown != null) {
            notificationDropdown.setVisible(false);
            notificationDropdown.setManaged(false);
        }

        /* TODO: Re-enable when NotificationWebSocketClient is fixed
        // Setup WebSocket callbacks
        setupWebSocketCallbacks();

        // Connect to WebSocket if user is logged in
        var currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null && !wsClient.isConnected()) {
            wsClient.connect(currentUser.getId());
        }

        // Close dropdown when clicking outside
        Platform.runLater(() -> {
            if (notificationButton.getScene() != null) {
                notificationButton.getScene().addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    if (dropdownVisible && !isClickInsideDropdown(event)) {
                        hideDropdown();
                    }
                });
            }
        });
        */
    }

    /**
     * Set header title and subtitle
     */
    public void setTitle(String title, String subtitle) {
        if (titleLabel != null) {
            titleLabel.setText(title);
        }
        if (subtitleLabel != null) {
            subtitleLabel.setText(subtitle);
            subtitleLabel.setVisible(subtitle != null && !subtitle.isEmpty());
            subtitleLabel.setManaged(subtitle != null && !subtitle.isEmpty());
        }
    }

    /* TODO: Re-enable notification methods when WebSocketClient is fixed

    private void setupWebSocketCallbacks() {
        wsClient.setOnNewNotification(notification -> {
            log.info("New notification received: {}", notification.getMessage());
            updateUnreadCount();
        });

        wsClient.setOnUnreadCountUpdate(count -> {
            updateBadge(count);
        });

        wsClient.setOnNotificationListUpdate(notifications -> {
            renderNotificationList(notifications);
        });
    }

    @FXML
    private void handleNotificationClick() {
        if (dropdownVisible) {
            hideDropdown();
        } else {
            showDropdown();
        }
    }

    private void showDropdown() {
        notificationDropdown.setVisible(true);
        notificationDropdown.setManaged(true);
        dropdownVisible = true;
    }

    private void hideDropdown() {
        notificationDropdown.setVisible(false);
        notificationDropdown.setManaged(false);
        dropdownVisible = false;
    }

    private boolean isClickInsideDropdown(MouseEvent event) {
        return notificationDropdown.getBoundsInParent().contains(event.getX(), event.getY()) ||
               notificationButton.getBoundsInParent().contains(event.getX(), event.getY());
    }

    @FXML
    private void handleMarkAllRead() {
        wsClient.markAllAsRead();
    }

    private void updateBadge(int count) {
        Platform.runLater(() -> {
            if (count > 0) {
                badgeLabel.setText(String.valueOf(Math.min(count, 99)));
                notificationBadge.setVisible(true);
                notificationBadge.setManaged(true);
            } else {
                notificationBadge.setVisible(false);
                notificationBadge.setManaged(false);
            }
        });
    }

    private void updateUnreadCount() {
        // This will trigger via WebSocket callback
    }

    private void renderNotificationList(List<Notification> notifications) {
        Platform.runLater(() -> {
            notificationList.getChildren().clear();

            if (notifications.isEmpty()) {
                Label emptyLabel = new Label("Không có thông báo");
                emptyLabel.setStyle("-fx-text-fill: -theme-text-secondary; -fx-padding: 20; -fx-font-size: 13px;");
                notificationList.getChildren().add(emptyLabel);
                return;
            }

            for (Notification notification : notifications) {
                VBox item = createNotificationItem(notification);
                notificationList.getChildren().add(item);
            }
        });
    }

    private VBox createNotificationItem(Notification notification) {
        VBox item = new VBox(6);
        item.setPadding(new Insets(12, 12, 12, 12));
        item.setCursor(Cursor.HAND);

        String bgColor = notification.isRead() ? "transparent" : "-theme-bid-panel";
        item.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 6;");

        item.setOnMouseEntered(e -> item.setStyle("-fx-background-color: -theme-hover; -fx-background-radius: 6;"));
        item.setOnMouseExited(e -> item.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 6;"));

        Label typeLabel = new Label(notification.getType().getDisplayName());
        typeLabel.setStyle("-fx-text-fill: -theme-primary; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label messageLabel = new Label(notification.getMessage());
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-text-fill: -theme-text; -fx-font-size: 13px;");

        Label timeLabel = new Label(notification.getCreatedAt().format(TIME_FORMATTER));
        timeLabel.setStyle("-fx-text-fill: -theme-text-secondary; -fx-font-size: 11px;");

        item.getChildren().addAll(typeLabel, messageLabel, timeLabel);
        item.setOnMouseClicked(e -> handleNotificationClick(notification));

        return item;
    }

    private void handleNotificationClick(Notification notification) {
        if (!notification.isRead()) {
            wsClient.markAsRead(notification.getId());
        }

        var currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getId();
        String userRole = currentUser.getRole();

        try {
            if ("ADMIN".equals(userRole) && notification.getType().name().contains("PENDING")) {
                NavigationManager.getInstance().navigateTo(
                    NavigationManager.ADMIN_MGMT,
                    "Quản lý phiên đấu giá",
                    null
                );
            } else if (notification.getAuctionId() != null) {
                // Others: go to auction detail
                // Need to fetch auction first
                com.auction.service.AppFacade.getInstance()
                    .findAuctionById(notification.getAuctionId())
                    .ifPresent(auction -> {
                        try {
                            NavigationManager.getInstance().navigateTo(
                                NavigationManager.AUCTION_DETAIL,
                                "Chi tiết đấu giá",
                                auction
                            );
                        } catch (IOException ex) {
                            log.error("Failed to navigate to auction detail", ex);
                        }
                    });
            }
        } catch (IOException ex) {
            log.error("Failed to navigate", ex);
        }

        hideDropdown();
    }

    public void cleanup() {
        // Don't disconnect WebSocket here as it's shared across pages
    }
    */
}
