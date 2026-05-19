package com.auction.controller;

import com.auction.model.*;
import com.auction.service.AppFacade;
import com.auction.util.HotItemCache;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.Cursor;
import javafx.scene.layout.Priority;
import javafx.application.Platform;
import javafx.util.Duration;
import com.auction.util.ImageLoaderUtil;

/**
 * DashboardController – system overview.
 * Uses AppFacade — no direct service/repository access.
 *
 * Tối ưu: loadData() chạy query trên background thread để không block FX
 * thread.
 */
public class DashboardController {

    @FXML
    private Label welcomeLabel;
    @FXML
    private Label activeAuctionsLabel;
    @FXML
    private Label totalAuctionsLabel;
    @FXML
    private Label totalUsersLabel;
    @FXML
    private Label openAuctionsLabel;
    @FXML
    private Label pendingAuctionsLabel;
    @FXML
    private Label newsLabel;

    @FXML
    private HBox hotItemsBox;

    private final AppFacade app = AppFacade.getInstance();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String[] newsHeadlines = {
            "Tuần lễ vàng đấu giá siêu xe và nghệ thuật đương đại đang diễn ra. Đừng bỏ lỡ!",
            "Sự kiện đặc biệt: Đấu giá từ thiện ủng hộ quỹ bảo trợ trẻ em vào tối thứ 7 tuần này.",
            "Phiên đấu giá tác phẩm điêu khắc cổ điển vừa thiết lập kỷ lục giá trị mới!",
            "Hãy liên hệ bộ phận hỗ trợ trực tuyến nếu bạn gặp bất kỳ sự cố giao dịch nào."
    };
    private int currentNewsIndex = 0;
    private javafx.animation.Timeline newsTimeline;

    private final HotItemCache hotCache = HotItemCache.getInstance();
    private Timeline hotRefreshTimeline;

    @FXML
    public void initialize() {
        loadData();
        startNewsTicker();
        startHotItemRefresh();
    }

    private void startNewsTicker() {
        if (newsLabel == null)
            return;
        newsTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.seconds(5), event -> {
                    javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(Duration.millis(300),
                            newsLabel);
                    fadeOut.setFromValue(1.0);
                    fadeOut.setToValue(0.0);
                    fadeOut.setOnFinished(e -> {
                        currentNewsIndex = (currentNewsIndex + 1) % newsHeadlines.length;
                        newsLabel.setText(newsHeadlines[currentNewsIndex]);
                        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
                                Duration.millis(300), newsLabel);
                        fadeIn.setFromValue(0.0);
                        fadeIn.setToValue(1.0);
                        fadeIn.play();
                    });
                    fadeOut.play();
                }));
        newsTimeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        newsTimeline.play();
    }

    private void loadData() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null) {
            welcomeLabel.setText("Chào mừng, " + user.getUsername() + " (" + user.getRole() + ")");
        }

        activeAuctionsLabel.setText("...");
        totalAuctionsLabel.setText("...");
        totalUsersLabel.setText("...");
        openAuctionsLabel.setText("...");
        if (pendingAuctionsLabel != null) pendingAuctionsLabel.setText("...");

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            private List<Auction> all;
            private int userCount;

            @Override
            protected Void call() {
                all = app.getAllAuctions();
                userCount = app.getAllUsers().size();
                // Seed hot-item cache from fresh data (background thread – safe)
                hotCache.seedFromList(all);
                return null;
            }

            @Override
            protected void succeeded() {
                long running = all.stream().filter(a -> a.getStatus() == AuctionStatus.RUNNING).count();
                long open    = all.stream().filter(a -> a.getStatus() == AuctionStatus.OPEN).count();
                long pending = all.stream().filter(a -> a.getStatus() == AuctionStatus.PENDING).count();

                activeAuctionsLabel.setText(String.valueOf(running));
                totalAuctionsLabel.setText(String.valueOf(all.size()));
                totalUsersLabel.setText(String.valueOf(userCount));
                openAuctionsLabel.setText(String.valueOf(open));
                if (pendingAuctionsLabel != null) pendingAuctionsLabel.setText(String.valueOf(pending));

                // Render hot items using cached bid counts for ordering
                refreshHotItems(all);
            }

            @Override
            protected void failed() {
                System.err.println("[Dashboard] loadData failed: " + getException().getMessage());
                totalAuctionsLabel.setText("Lỗi tải dữ liệu");
            }
        };

        Thread t = new Thread(task, "dashboard-load");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Re-renders the hot-items strip using current HotItemCache bid counts.
     * Must be called on the FX thread.
     */
    private void refreshHotItems(List<Auction> all) {
        // Pull top-5 IDs from O(1) cache
        List<String> topIds = hotCache.getTopN(5);

        // Map IDs → Auction objects (prefer cache order, fall back to bid-history size)
        List<Auction> hotList;
        if (!topIds.isEmpty()) {
            Map<String, Auction> byId = new java.util.HashMap<>();
            all.forEach(a -> byId.put(a.getId(), a));
            hotList = topIds.stream()
                    .map(byId::get)
                    .filter(a -> a != null
                            && (a.getStatus() == AuctionStatus.RUNNING
                                || a.getStatus() == AuctionStatus.OPEN))
                    .toList();
        } else {
            // Fallback: sort by bid-history size
            hotList = all.stream()
                    .filter(a -> a.getStatus() == AuctionStatus.OPEN
                            || a.getStatus() == AuctionStatus.RUNNING)
                    .sorted((a1, a2) -> Integer.compare(
                            a2.getBidHistory().size(), a1.getBidHistory().size()))
                    .limit(5).toList();
        }

        hotItemsBox.getChildren().clear();
        for (Auction a : hotList) {
            hotItemsBox.getChildren().add(createHotItemCard(a));
        }
    }

    /**
     * Schedules a lightweight hot-item re-sort every 5 seconds on a background thread.
     * No DB query – purely reads the in-memory HotItemCache and existing auction list.
     */
    private void startHotItemRefresh() {
        hotRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            javafx.concurrent.Task<List<Auction>> refreshTask = new javafx.concurrent.Task<>() {
                @Override protected List<Auction> call() { return app.getAllAuctions(); }
            };
            refreshTask.setOnSucceeded(ev -> {
                hotCache.seedFromList(refreshTask.getValue());
                Platform.runLater(() -> refreshHotItems(refreshTask.getValue()));
            });
            Thread t = new Thread(refreshTask, "hot-refresh");
            t.setDaemon(true);
            t.start();
        }));
        hotRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        hotRefreshTimeline.play();
    }

    @FXML
    private void handleViewAllAuctions(ActionEvent event) {
        try {
            NavigationManager.getInstance().navigateTo(
                    NavigationManager.AUCTION_LIST, "Danh sách đấu giá", null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private VBox createHotItemCard(Auction auction) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setStyle(
                "-fx-background-radius: 16; -fx-border-radius: 16; -fx-min-width: 240; -fx-pref-width: 240; -fx-alignment: center;");
        card.setCursor(Cursor.HAND);

        ImageView iv = new ImageView();
        iv.setFitWidth(200);
        iv.setFitHeight(120);
        iv.setPreserveRatio(true);
        javafx.concurrent.Task<javafx.scene.image.Image> imgTask = new javafx.concurrent.Task<>() {
            @Override
            protected javafx.scene.image.Image call() {
                return ImageLoaderUtil.loadItemImage(auction.getItem().getImageUrl(), 200, 120);
            }

            @Override
            protected void succeeded() {
                iv.setImage(getValue());
            }
        };
        Thread t = new Thread(imgTask);
        t.setDaemon(true);
        t.start();

        Label title = new Label(auction.getItem().getName());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: -theme-text;");

        Label price = new Label(String.format("Giá: %,.0f ₫", auction.getHighestBid()));
        price.setStyle("-fx-text-fill: #16A34A; -fx-font-weight: bold; -fx-font-size: 14px;");

        Label status = new Label(auction.getStatusDisplay());
        status.getStyleClass().addAll("badge",
                auction.getStatus() == AuctionStatus.RUNNING ? "badge-running" : "badge-open");

        card.getChildren().addAll(iv, title, price, status);

        // Hover scale animation
        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(200), card);
        scaleUp.setToX(1.05);
        scaleUp.setToY(1.05);
        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(200), card);
        scaleDown.setToX(1.0);
        scaleDown.setToY(1.0);

        card.setOnMouseEntered(e -> scaleUp.playFromStart());
        card.setOnMouseExited(e -> scaleDown.playFromStart());

        card.setOnMouseClicked(e -> {
            try {
                NavigationManager.getInstance().navigateTo(NavigationManager.AUCTION_DETAIL, "Chi tiết", auction);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        return card;
    }

    public void cleanup() {
        if (newsTimeline != null) newsTimeline.stop();
        if (hotRefreshTimeline != null) hotRefreshTimeline.stop();
    }
}
