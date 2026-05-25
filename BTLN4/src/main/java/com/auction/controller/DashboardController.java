package com.auction.controller;

import com.auction.model.*;
import com.auction.service.AppFacade;
import com.auction.util.HotItemCache;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import com.auction.util.TimeSyncManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.Cursor;
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
    private FlowPane hotItemsBox;

    private final AppFacade app = AppFacade.getInstance();

    private final String[] newsHeadlines = {
            "Sự kiện đặc biệt: Đấu giá thượng lưu có sự góp mặt của tỷ phú Trương Xuân Hiếu vào chiều thứ 6 tuần này.",
            "Tuần lễ vàng đấu giá siêu xe và nghệ thuật đương đại đang diễn ra. Đừng bỏ lỡ!",
            "Sự kiện đặc biệt: Đấu giá thượng lưu có sự góp mặt của tỷ phú Trương Xuân Hiếu vào chiều thứ 6 tuần này.",
            "Phiên đấu giá tác phẩm điêu khắc cổ điển vừa thiết lập kỷ lục giá trị mới!",
            "Hãy liên hệ bộ phận hỗ trợ trực tuyến nếu bạn gặp bất kỳ sự cố giao dịch nào."
    };
    private int currentNewsIndex = 0;
    private javafx.animation.Timeline newsTimeline;

    private final HotItemCache hotCache = HotItemCache.getInstance();
    private Timeline hotRefreshTimeline;
    private Timeline hotCountdownTimeline;
    private final Map<String, Auction> visibleHotAuctions = new HashMap<>();
    /** Guard to prevent overlapping hot-refresh DB queries. */
    private volatile boolean isRefreshing = false;

    @FXML
    public void initialize() {
        loadData();
        startNewsTicker();
        startHotItemRefresh();
        startHotCountdownRefresh();
    }

    private void startNewsTicker() {
        if (newsLabel == null)
            return;
        newsTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.seconds(8), event -> {
                    javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(Duration.millis(400),
                            newsLabel);
                    fadeOut.setFromValue(1.0);
                    fadeOut.setToValue(0.0);
                    fadeOut.setOnFinished(e -> {
                        currentNewsIndex = (currentNewsIndex + 1) % newsHeadlines.length;
                        newsLabel.setText(newsHeadlines[currentNewsIndex]);
                        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
                                Duration.millis(400), newsLabel);
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
        if (pendingAuctionsLabel != null)
            pendingAuctionsLabel.setText("...");

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
                long open = all.stream().filter(a -> a.getStatus() == AuctionStatus.OPEN).count();
                long pending = all.stream().filter(a -> a.getStatus() == AuctionStatus.PENDING).count();

                activeAuctionsLabel.setText(String.valueOf(running));
                totalAuctionsLabel.setText(String.valueOf(all.size()));
                totalUsersLabel.setText(String.valueOf(userCount));
                openAuctionsLabel.setText(String.valueOf(open));
                if (pendingAuctionsLabel != null)
                    pendingAuctionsLabel.setText(String.valueOf(pending));

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
     * Re-renders the hot-items strip with auctions ending soonest first.
     * Must be called on the FX thread.
     */
    private void refreshHotItems(List<Auction> all) {
        List<Auction> hotList = all.stream()
                .filter(a -> a.getStatus() == AuctionStatus.OPEN
                        || a.getStatus() == AuctionStatus.RUNNING)
                .sorted(Comparator
                        .comparing((Auction a) -> endTimeOrMax(a))
                        .thenComparing(a -> a.getItem().getName()))
                .limit(5)
                .toList();

        visibleHotAuctions.clear();
        hotList.forEach(a -> visibleHotAuctions.put(a.getId(), a));

        // Smart update: only clear and rebuild if the list of items changed
        boolean changed = false;
        if (hotItemsBox.getChildren().size() != hotList.size()) {
            changed = true;
        } else {
            for (int i = 0; i < hotList.size(); i++) {
                VBox card = (VBox) hotItemsBox.getChildren().get(i);
                if (!hotList.get(i).getId().equals(card.getUserData())) {
                    changed = true;
                    break;
                }
            }
        }

        if (changed) {
            hotItemsBox.getChildren().clear();
            for (Auction a : hotList) {
                VBox card = createHotItemCard(a);
                card.setUserData(a.getId()); // Store ID for future checks
                hotItemsBox.getChildren().add(card);
            }
        } else {
            // Hot items are the same, just gracefully update the prices and statuses!
            for (int i = 0; i < hotList.size(); i++) {
                Auction a = hotList.get(i);
                VBox card = (VBox) hotItemsBox.getChildren().get(i);

                // createHotItemCard structure: [0] image, [1] title, [2] price, [3] status, [4]
                // countdown
                if (card.getChildren().size() >= 5) {
                    Label price = (Label) card.getChildren().get(2);
                    price.setText(String.format("Giá: %,.0f ₫", a.getHighestBid()));

                    Label status = (Label) card.getChildren().get(3);
                    status.setText(a.getStatusDisplay());

                    // Update badge color
                    status.getStyleClass().removeAll("badge-running", "badge-open");
                    status.getStyleClass().add(
                            a.getStatus() == com.auction.model.AuctionStatus.RUNNING ? "badge-running" : "badge-open");

                    Label countdown = (Label) card.getChildren().get(4);
                    countdown.setText(formatCountdown(a));
                }
            }
        }
    }

    private LocalDateTime endTimeOrMax(Auction auction) {
        return auction.getEndTime() == null ? LocalDateTime.MAX : auction.getEndTime();
    }

    /**
     * Schedules a lightweight hot-item re-sort every 30 seconds on a background
     * thread.
     * No DB query – purely reads the in-memory HotItemCache and existing auction
     * list.
     */
    private void startHotItemRefresh() {
        // 30 s interval – reduced from 15 s to cut DB load and CPU usage.
        // isRefreshing guard ensures only one background query runs at a time.
        hotRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            if (isRefreshing)
                return; // skip if previous query still in progress
            isRefreshing = true;
            javafx.concurrent.Task<List<Auction>> refreshTask = new javafx.concurrent.Task<>() {
                @Override
                protected List<Auction> call() {
                    return app.getAllAuctions();
                }
            };
            refreshTask.setOnSucceeded(ev -> {
                isRefreshing = false;
                hotCache.seedFromList(refreshTask.getValue());
                Platform.runLater(() -> refreshHotItems(refreshTask.getValue()));
            });
            refreshTask.setOnFailed(ev -> isRefreshing = false);
            Thread t = new Thread(refreshTask, "hot-refresh");
            t.setDaemon(true);
            t.start();
        }));
        hotRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        hotRefreshTimeline.play();
    }

    private void startHotCountdownRefresh() {
        hotCountdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateHotCountdownLabels()));
        hotCountdownTimeline.setCycleCount(Timeline.INDEFINITE);
        hotCountdownTimeline.play();
    }

    private void updateHotCountdownLabels() {
        for (javafx.scene.Node node : hotItemsBox.getChildren()) {
            if (!(node instanceof VBox card) || !(card.getUserData() instanceof String auctionId)) {
                continue;
            }
            Auction auction = visibleHotAuctions.get(auctionId);
            if (auction == null || card.getChildren().size() < 5) {
                continue;
            }
            Label countdown = (Label) card.getChildren().get(4);
            countdown.setText(formatCountdown(auction));
        }
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
        // Check cache first – if the splash screen already preloaded this image
        // we can set it synchronously on the FX thread with zero overhead.
        String imgUrl = auction.getItem() != null ? auction.getItem().getImageUrl() : null;
        if (imgUrl != null && !imgUrl.isEmpty()) {
            javafx.scene.image.Image cached = com.auction.util.CacheManager.getInstance()
                    .getImage(imgUrl + "_200_120");
            if (cached != null) {
                iv.setImage(cached);
            } else {
                // Cache miss – load in background
                javafx.concurrent.Task<javafx.scene.image.Image> imgTask = new javafx.concurrent.Task<>() {
                    @Override
                    protected javafx.scene.image.Image call() {
                        return ImageLoaderUtil.loadItemImage(imgUrl, 200, 120);
                    }

                    @Override
                    protected void succeeded() {
                        iv.setImage(getValue());
                    }
                };
                Thread imgThread = new Thread(imgTask, "img-load-dashboard");
                imgThread.setDaemon(true);
                imgThread.start();
            }
        }

        Label title = new Label(auction.getItem().getName());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: -theme-text;");

        Label price = new Label(String.format("Giá: %,.0f ₫", auction.getHighestBid()));
        price.setStyle("-fx-text-fill: #16A34A; -fx-font-weight: bold; -fx-font-size: 14px;");

        Label status = new Label(auction.getStatusDisplay());
        status.getStyleClass().addAll("badge",
                auction.getStatus() == AuctionStatus.RUNNING ? "badge-running" : "badge-open");

        Label countdown = new Label(formatCountdown(auction));
        countdown.getStyleClass().add("hot-countdown");

        card.getChildren().addAll(iv, title, price, status, countdown);

        card.setOnMouseClicked(e -> {
            // Fetch full details asynchronously
            javafx.concurrent.Task<Auction> fetchTask = new javafx.concurrent.Task<>() {
                @Override
                protected Auction call() throws Exception {
                    return AppFacade.getInstance().findAuctionById(auction.getId()).orElse(auction);
                }
            };
            fetchTask.setOnSucceeded(ev -> {
                try {
                    NavigationManager.getInstance().navigateTo(NavigationManager.AUCTION_DETAIL, "Chi tiết",
                            fetchTask.getValue());
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
            Thread t = new Thread(fetchTask, "fetch-detail-" + auction.getId());
            t.setDaemon(true);
            t.start();
        });

        return card;
    }

    private String formatCountdown(Auction auction) {
        if (auction.getEndTime() == null)
            return "Chưa có hạn kết thúc";

        java.time.Duration remaining = java.time.Duration.between(TimeSyncManager.getNow(), auction.getEndTime());
        if (remaining.isNegative() || remaining.isZero()) {
            return "Sắp kết thúc";
        }

        long days = remaining.toDays();
        long hours = remaining.toHoursPart();
        long minutes = remaining.toMinutesPart();
        long seconds = remaining.toSecondsPart();

        if (days > 0) {
            return String.format("Còn %dd %02dh %02dm", days, hours, minutes);
        }
        return String.format("Còn %02d:%02d:%02d", remaining.toHours(), minutes, seconds);
    }

    public void cleanup() {
        if (newsTimeline != null)
            newsTimeline.stop();
        if (hotRefreshTimeline != null)
            hotRefreshTimeline.stop();
        if (hotCountdownTimeline != null)
            hotCountdownTimeline.stop();
    }
}
