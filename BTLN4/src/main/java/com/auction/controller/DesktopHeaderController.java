package com.auction.controller;

import com.auction.util.NotificationManager;
import com.auction.util.NotificationManager.AppNotification;
import com.auction.util.SessionManager;
import com.auction.util.TimeSyncManager;
import com.auction.model.User;
import com.auction.model.Bidder;
import com.auction.service.AppFacade;
import com.auction.service.AuctionWebSocketService;
import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
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
import javafx.stage.Screen;
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
public class DesktopHeaderController implements NotificationManager.NotificationListener, com.auction.service.AuctionWebSocketService.AuctionWebSocketListener {

    private final java.util.Map<String, Double> pendingAcks = new java.util.concurrent.ConcurrentHashMap<>();

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
    private Popup    toastPopup;     // Auto-fading “new bid” toast shown to all viewers

    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_FMT   = DateTimeFormatter.ofPattern("HH:mm");

    // ── Initialize ────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        startClock();
        buildNotifPopup();
        buildToastPopup();

        // Subscribe to notification updates (Đăng ký Observer)
        NotificationManager.getInstance().addListener(this);
        SessionManager.getInstance().addWsListener(this);

        // Initial load
        List<AppNotification> initial = NotificationManager.getInstance().getNotifications();
        if (notifListBox != null) {
            notifListBox.getChildren().clear();
            for (AppNotification n : initial) {
                notifListBox.getChildren().add(buildNotifRow(n));
            }
        }
        updateBadgeUI();

        // Dynamically evaluate outbid state based on current DB reality
        evaluateOutbidState();
    }

    private void evaluateOutbidState() {
        User me = SessionManager.getInstance().getCurrentUser();
        if (!(me instanceof Bidder)) return;

        CompletableFuture.supplyAsync(() -> AppFacade.getInstance().getPublicAuctions())
            .thenAccept(auctions -> {
                for (com.auction.model.Auction a : auctions) {
                    if (a.getStatus() != com.auction.model.AuctionStatus.RUNNING) continue;

                    // Fetch detailed auction from remote server to get the bid history
                    AppFacade.getInstance().findAuctionById(a.getId()).ifPresent(fullAuction -> {
                        double myMaxBid = 0;
                        boolean hasBid = false;
                        for (com.auction.model.BidTransaction bt : fullAuction.getBidHistory()) {
                            if (bt.getBidder().getId().equals(me.getId())) {
                                hasBid = true;
                                if (bt.getAmount() > myMaxBid) {
                                    myMaxBid = bt.getAmount();
                                }
                            }
                        }

                        // Formula: Has bid AND user's highest bid < current highest bid
                        if (hasBid && myMaxBid < fullAuction.getHighestBid()) {
                            String prefKey = "ack_" + me.getId() + "_" + fullAuction.getId();
                            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(DesktopHeaderController.class);
                            double ackedBid = prefs.getDouble(prefKey, 0.0);

                            if (fullAuction.getHighestBid() > ackedBid) {
                                NotificationManager.getInstance().addNotification(
                                    NotificationManager.outbidMessage(fullAuction.getItem().getName())
                                );
                                pendingAcks.put(prefKey, fullAuction.getHighestBid());
                            }
                        }
                    });
                }
            });
    }

    @Override
    public void onOutbid(JsonObject json) {
        String bidderId = json.get("bidderId").getAsString();
        String itemName = json.get("itemName").getAsString();

        User me = SessionManager.getInstance().getCurrentUser();
        if (me instanceof Bidder myBidder && myBidder.getId().equals(bidderId)) {
            NotificationManager.getInstance().addNotification(
                    NotificationManager.outbidMessage(itemName));
        }
    }

    @Override public void onWsConnected() {}
    @Override public void onWsDisconnected(String err) {}
    @Override public void onWsError(String err) {}
    @Override
    public void onBidUpdate(JsonObject j) {
        String bidderName = j.has("bidderUsername") ? j.get("bidderUsername").getAsString() : "Bidder";
        double amount     = j.has("amount")         ? j.get("amount").getAsDouble()         : 0;

        // Do not show the toast to the person who just placed the bid
        User me = SessionManager.getInstance().getCurrentUser();
        if (me != null && me.getUsername().equals(bidderName)) {
            return;
        }

        Platform.runLater(() -> showBidToast(bidderName, amount));
    }
    @Override public void onAutoBidLog(JsonObject j) {}
    @Override public void onAutoBidAck(JsonObject j) {}
    @Override public void onAutoBidStatus(JsonObject j) {}
    @Override public void onAutoBidDeactivated(JsonObject j) {}
    @Override public void onStatusChanged(JsonObject j) {}
    @Override public void onBalanceUpdate(JsonObject j) {}
    @Override public void onFullSync(JsonObject j) {}
    @Override public void onLegacyBidUpdate(JsonObject j) {}

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
        scrollPane.setPrefHeight(85);
        scrollPane.setMaxHeight(85);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        // ── Footer ───────────────────────────────────────────────────────────
        HBox footer = new HBox();
        footer.setAlignment(javafx.geometry.Pos.CENTER);
        footer.setPadding(new Insets(8, 14, 10, 14));
        footer.setStyle("-fx-border-color: -theme-border transparent transparent transparent; -fx-border-width: 1;");

        Label footerLbl = new Label("Thông báo mới nhất");
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

        // Save pending acknowledgments so the bell doesn't glow again for these bids
        if (!pendingAcks.isEmpty()) {
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(DesktopHeaderController.class);
            pendingAcks.forEach((key, bid) -> prefs.putDouble(key, bid));
            pendingAcks.clear();
        }

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

                // Kiểm tra nếu .size() > 20 thì lập tức gọi .remove(20) để xóa phần tử cũ nhất
                if (notifListBox.getChildren().size() > 20) {
                    notifListBox.getChildren().remove(20);
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

    // ── Toast popup ───────────────────────────────────────────────────────────────────

    private void buildToastPopup() {
        toastPopup = new Popup();
        toastPopup.setAutoFix(true);
        toastPopup.setAutoHide(false);
    }

    /**
     * Displays a floating, auto-fading “new bid” toast at the bottom-right of
     * the screen. Triggered for ALL connected viewers when a BID_UPDATE arrives
     * (not just the bidder). Timeline: fade-in 300ms → hold 3.5s → fade-out 800ms.
     */
    private void showBidToast(String bidderName, double amount) {
        if (toastPopup == null) return;
        if (toastPopup.isShowing()) toastPopup.hide();

        // ── Build card ────────────────────────────────────────────────────────────
        Label icon = new Label("\uD83D\uDD28"); // 🔨 hammer
        icon.setStyle("-fx-font-size: 20px;");

        Label nameLbl = new Label(bidderName + " vừa đặt giá");
        nameLbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: rgba(167,243,208,0.90);");

        Label amountLbl = new Label(String.format("%,.0f ₫", amount));
        amountLbl.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #ECFDF5;");

        VBox textBox = new VBox(2, nameLbl, amountLbl);

        HBox card = new HBox(12, icon, textBox);
        card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        card.setStyle(
            "-fx-background-color: linear-gradient(to right, #064e3b, #065f46);" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: rgba(52,211,153,0.55);" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.55),20,0,0,6);" +
            "-fx-padding: 12 18 12 14;"
        );
        card.setPrefWidth(280);
        card.setOpacity(0.0);

        toastPopup.getContent().clear();
        toastPopup.getContent().add(card);

        // ── Position: bottom-right of the primary screen ─────────────────────
        javafx.stage.Window window = (bellButton != null && bellButton.getScene() != null)
                ? bellButton.getScene().getWindow() : null;
        if (window == null) return;

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        toastPopup.show(window,
                screenBounds.getMaxX() - 310,
                screenBounds.getMaxY() - 110);

        // ── Animate ────────────────────────────────────────────────────────────
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), card);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(800), card);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> toastPopup.hide());

        // After fade-in completes, start 3.5s hold then fade out
        Timeline autoFade = new Timeline(
                new KeyFrame(Duration.seconds(3.5), e -> fadeOut.play()));
        fadeIn.setOnFinished(e -> autoFade.play());
        fadeIn.play();
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void cleanup() {
        if (clockTimeline != null) clockTimeline.stop();
        NotificationManager.getInstance().removeListener(this);
        SessionManager.getInstance().removeWsListener(this);
        if (notifPopup != null && notifPopup.isShowing()) notifPopup.hide();
    }
}
