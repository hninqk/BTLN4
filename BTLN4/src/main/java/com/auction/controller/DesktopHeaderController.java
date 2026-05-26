package com.auction.controller;

import com.auction.util.NotificationManager;
import com.auction.util.NotificationManager.AppNotification;
import com.auction.util.TimeSyncManager;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * DesktopHeaderController – Controller cho thanh header toàn cục.
 *
 * Chức năng:
 *  • Đồng hồ real-time cập nhật mỗi giây (clock bên trái).
 *  • Chấm xanh lá (#10b981) biểu thị trạng thái online/đồng bộ.
 *  • Button chuông với kích thước cố định (không bị méo khi click).
 *  • Popup thông báo (javafx.stage.Popup – luôn đè lên trên mọi node).
 *  • setAutoHide(true) → tự đóng khi click ra ngoài.
 *  • Lắng nghe NotificationManager và cập nhật badge + danh sách tự động.
 */
public class DesktopHeaderController implements NotificationManager.NotificationListener {

    // ── FXML bindings ─────────────────────────────────────────────────────────
    @FXML private HBox    headerRoot;
    @FXML private Circle  onlineDot;
    @FXML private Label   clockLabel;
    @FXML private StackPane bellPane;
    @FXML private Button  bellButton;
    @FXML private Label   badgeLabel;

    // ── Internal state ────────────────────────────────────────────────────────
    private Timeline clockTimeline;
    private Popup    notifPopup;
    private VBox     notifListBox;   // Container for notification rows inside popup

    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_FMT   = DateTimeFormatter.ofPattern("HH:mm");

    // ── Initialize ────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        startClock();
        buildNotifPopup();

        // Subscribe to notification updates (Đăng ký Observer)
        NotificationManager.getInstance().addListener(this);

        // Initial load
        List<AppNotification> initial = NotificationManager.getInstance().getNotifications();
        if (notifListBox != null) {
            notifListBox.getChildren().clear();
            for (AppNotification n : initial) {
                notifListBox.getChildren().add(buildNotifRow(n));
            }
        }
        updateBadgeUI();
    }

    // ── Clock ─────────────────────────────────────────────────────────────────

    private void startClock() {
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (clockLabel != null) {
                clockLabel.setText(TimeSyncManager.getNow().format(CLOCK_FMT));
            }
        }));
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
        // Set immediately without waiting 1 second
        if (clockLabel != null) {
            clockLabel.setText(TimeSyncManager.getNow().format(CLOCK_FMT));
        }
    }

    // ── Popup build ───────────────────────────────────────────────────────────

    /**
     * Khởi tạo Popup một lần duy nhất.
     * Popup hoạt động như một Window độc lập, tự động đè lên TẤT CẢ các node
     * khác trên giao diện — giải quyết hoàn toàn vấn đề Z-Index.
     */
    private void buildNotifPopup() {
        notifPopup = new Popup();
        notifPopup.setAutoHide(true);   // Click ngoài → tự đóng
        notifPopup.setHideOnEscape(true);

        // ── Wrapper card ─────────────────────────────────────────────────────
        VBox wrapper = new VBox();
        wrapper.getStyleClass().add("notif-popup-wrapper");
        wrapper.setStyle(
            "-fx-background-color: -theme-surface;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: -theme-border;" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 18, 0, 0, 6);"
        );
        wrapper.setPrefWidth(340);
        wrapper.setMaxWidth(340);

        // ── Header row ───────────────────────────────────────────────────────
        HBox header = new HBox();
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setSpacing(8);
        header.setPadding(new Insets(12, 14, 10, 14));
        header.setStyle("-fx-border-color: transparent transparent -theme-border transparent; -fx-border-width: 1;");

        Label title = new Label("🔔  Thông báo");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: -theme-text;");
        HBox.setHgrow(title, Priority.ALWAYS);
        title.setMaxWidth(Double.MAX_VALUE);

        Button markAllBtn = new Button("Đọc tất cả");
        markAllBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: -theme-primary;" +
            "-fx-font-size: 11px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 2 6;"
        );
        markAllBtn.setOnAction(e -> {
            NotificationManager.getInstance().markAllRead();
        });

        header.getChildren().addAll(title, markAllBtn);

        // ── Scrollable notification list ─────────────────────────────────────
        notifListBox = new VBox(0);
        notifListBox.setFillWidth(true);

        ScrollPane scrollPane = new ScrollPane(notifListBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPrefHeight(380);
        scrollPane.setMaxHeight(380);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        // ── Footer ───────────────────────────────────────────────────────────
        HBox footer = new HBox();
        footer.setAlignment(javafx.geometry.Pos.CENTER);
        footer.setPadding(new Insets(8, 14, 10, 14));
        footer.setStyle("-fx-border-color: -theme-border transparent transparent transparent; -fx-border-width: 1;");

        Label footerLbl = new Label("Hiển thị 15 thông báo gần nhất");
        footerLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: -theme-text-muted;");
        footer.getChildren().add(footerLbl);

        wrapper.getChildren().addAll(header, scrollPane, footer);
        notifPopup.getContent().add(wrapper);
    }

    // ── Bell click handler ────────────────────────────────────────────────────

    @FXML
    private void handleBellClick() {
        if (notifPopup == null || bellButton == null) return;

        if (notifPopup.isShowing()) {
            notifPopup.hide();
            return;
        }

        // Mark all as read when opening popup
        NotificationManager.getInstance().markAllRead();

        // Refresh all rows visually
        if (notifListBox != null) {
            for (javafx.scene.Node node : notifListBox.getChildren()) {
                if (node instanceof HBox row) {
                    row.setStyle("-fx-background-color: transparent; -fx-border-color: transparent transparent -theme-border transparent; -fx-border-width: 1;");
                    if (row.getChildren().get(0) instanceof Circle dot) {
                        dot.setFill(javafx.scene.paint.Color.TRANSPARENT);
                    }
                }
            }
        }

        updateBadgeUI();

        // Position popup below the bell button
        javafx.geometry.Bounds bounds = bellButton.localToScreen(bellButton.getBoundsInLocal());
        if (bounds != null) {
            double x = bounds.getMaxX() - 340; // right-align with bell
            double y = bounds.getMaxY() + 4;
            notifPopup.show(bellButton.getScene().getWindow(), x, y);
        }
    }

    // ── Notification list refresh ─────────────────────────────────────────────

    @Override
    public void onNotificationAdded(AppNotification notification) {
        // BẮT BUỘC bao bọc bên trong Platform.runLater (Chống nuốt lỗi ngầm)
        Platform.runLater(() -> {
            if (notifListBox != null) {
                // Xóa label "Không có thông báo nào" nếu có
                if (notifListBox.getChildren().size() == 1 && notifListBox.getChildren().get(0) instanceof Label) {
                    notifListBox.getChildren().clear();
                }

                // Khi có thông báo mới, dùng .add(0, newNoti) để đẩy lên đầu bảng
                notifListBox.getChildren().add(0, buildNotifRow(notification));

                // Kiểm tra nếu .size() > 15 thì lập tức gọi .remove(15) để xóa phần tử cũ nhất
                if (notifListBox.getChildren().size() > 15) {
                    notifListBox.getChildren().remove(15);
                }
            }
            updateBadgeUI();
        });
    }

    @Override
    public void onAllRead() {
        Platform.runLater(() -> {
            if (notifListBox != null) {
                for (javafx.scene.Node node : notifListBox.getChildren()) {
                    if (node instanceof HBox row) {
                        row.setStyle("-fx-background-color: transparent; -fx-border-color: transparent transparent -theme-border transparent; -fx-border-width: 1;");
                        if (row.getChildren().size() > 0 && row.getChildren().get(0) instanceof Circle dot) {
                            dot.setFill(javafx.scene.paint.Color.TRANSPARENT);
                        }
                    }
                }
            }
            updateBadgeUI();
        });
    }

    private void updateBadgeUI() {
        long unread = NotificationManager.getInstance().getUnreadCount();
        if (badgeLabel != null) {
            if (unread > 0) {
                badgeLabel.setText(unread > 9 ? "9+" : String.valueOf(unread));
                badgeLabel.setVisible(true);
                if (bellButton != null && !bellButton.getStyleClass().contains("bell-unread")) {
                    bellButton.getStyleClass().add("bell-unread");
                }
            } else {
                badgeLabel.setVisible(false);
                if (bellButton != null) {
                    bellButton.getStyleClass().remove("bell-unread");
                }
            }
        }
    }

    private HBox buildNotifRow(AppNotification notif) {
        HBox row = new HBox(10);
        row.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setStyle(
            notif.read()
                ? "-fx-background-color: transparent; -fx-border-color: transparent transparent -theme-border transparent; -fx-border-width: 1;"
                : "-fx-background-color: rgba(99,102,241,0.07); -fx-border-color: transparent transparent -theme-border transparent; -fx-border-width: 1;"
        );

        // Unread dot indicator
        Circle dot = new Circle(4);
        dot.setFill(notif.read()
                ? javafx.scene.paint.Color.TRANSPARENT
                : javafx.scene.paint.Color.web("#6366f1"));
        dot.setManaged(true);

        // Content
        VBox content = new VBox(3);
        HBox.setHgrow(content, Priority.ALWAYS);

        Label contentLbl = new Label(notif.content());
        contentLbl.setWrapText(true);
        contentLbl.setStyle(
            "-fx-text-fill: -theme-text;" +
            "-fx-font-size: 12.5px;" +
            (notif.read() ? "" : "-fx-font-weight: bold;")
        );
        contentLbl.setMaxWidth(270);

        Label timeLbl = new Label(notif.time().format(TIME_FMT));
        timeLbl.setStyle("-fx-text-fill: -theme-text-muted; -fx-font-size: 11px;");

        content.getChildren().addAll(contentLbl, timeLbl);
        row.getChildren().addAll(dot, content);

        // Hover effect
        row.setOnMouseEntered(e -> row.setStyle(
            row.getStyle() + "-fx-background-color: rgba(99,102,241,0.12);"
        ));
        row.setOnMouseExited(e -> row.setStyle(
            notif.read()
                ? "-fx-background-color: transparent; -fx-border-color: transparent transparent -theme-border transparent; -fx-border-width: 1;"
                : "-fx-background-color: rgba(99,102,241,0.07); -fx-border-color: transparent transparent -theme-border transparent; -fx-border-width: 1;"
        ));

        return row;
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void cleanup() {
        if (clockTimeline != null) clockTimeline.stop();
        NotificationManager.getInstance().removeListener(this);
        if (notifPopup != null && notifPopup.isShowing()) notifPopup.hide();
    }
}
