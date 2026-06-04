package com.auction.ui.controller;

import com.auction.core.model.*;
import javafx.scene.control.*;

import com.auction.core.util.CacheManager;
import com.auction.ui.support.logic.DashboardAuctionService;
import com.auction.ui.support.logic.DefaultDashboardAuctionService;
import com.auction.ui.support.ui.GuardedNodeUpdater;
import com.auction.core.util.HotItemCache;
import com.auction.ui.util.NavigationManager;
import com.auction.core.util.SessionManager;
import com.auction.core.util.TimeSyncManager;
import javafx.fxml.FXML;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.chart.PieChart;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.Cursor;
import javafx.application.Platform;
import javafx.util.Duration;
import com.auction.ui.util.ImageLoaderUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.MouseEvent;
import com.google.gson.JsonObject;

public class DashboardController extends RealtimeController {

    public DashboardController() {
        super("Dashboard-WS", "[Dashboard]");
    }

    @FXML

    private Button exploreButton;

    @FXML

    private VBox bidderView;

    @FXML

    private Label newsLabel;

    @FXML

    private VBox hotItemsBox;

    @FXML

    private VBox cardRunning;

    @FXML

    private VBox cardOpen;

    @FXML

    private VBox cardPending;

    @FXML

    private VBox cardTotalAuctions;

    @FXML

    private VBox cardTotalUsers;

    @FXML private Label dashStatRunning;

    @FXML private Label dashStatOpen;

    @FXML private Label dashStatPending;

    @FXML private Label dashStatTotalAuctions;

    @FXML private Label dashStatTotalUsers;

    @FXML

    private VBox sellerView;

    @FXML

    private Label sellerNewsLabel;

    @FXML

    private VBox sellerHotItemsBox;

    @FXML

    private Label sellerEarningLabel;

    @FXML

    private Label sellerRevenueLabel;

    @FXML

    private Label sellerClosedLabel;

    @FXML

    private GridPane sellerRevenueHeatmap;

    @FXML

    private PieChart sellerCategoryPieChart;

    @FXML

    private StackPane sellerPieContainer;

    @FXML

    private VBox sellerCardRunning;

    @FXML

    private VBox sellerCardOpen;

    @FXML

    private VBox sellerCardPending;

    @FXML

    private VBox sellerCardTotalAuctions;

    @FXML

    private VBox sellerCardTotalUsers;

    @FXML private Label sellerDashStatRunning;

    @FXML private Label sellerDashStatOpen;

    @FXML

    private FlowPane sellerPieLegend;

    private final DashboardAuctionService dashboardAuctionService = new DefaultDashboardAuctionService();

    private static final GuardedNodeUpdater NODE_UPDATER = new GuardedNodeUpdater.Default();

    private Tooltip activeHeatmapTooltip;

    private final String[] pieColors = new String[] {
            "#3B82F6",
            "#22C55E",
            "#F97316"
    };

    private final String[] newsHeadlines = {
            "Sự kiện đặc biệt: Đấu giá thượng lưu có sự góp mặt của tỷ phú Trương Xuân Hiếu vào chiều thứ 6 tuần này.",
            "Tuần lễ vàng đấu giá siêu xe và nghệ thuật đương đại đang diễn ra. Đừng bỏ lỡ!",
            "Sự kiện đặc biệt: Đấu giá thượng lưu có sự góp mặt của tỷ phú Trương Xuân Hiếu vào chiều thứ 6 tuần này.",
            "Phiên đấu giá tác phẩm điêu khắc cổ điển vừa thiết lập kỷ lục giá trị mới!",
            "Hãy liên hệ bộ phận hỗ trợ trực tuyến nếu bạn gặp bất kỳ sự cố giao dịch nào."
    };

    private int currentNewsIndex = 0;

    private javafx.animation.Timeline newsTimeline;

    private javafx.animation.Timeline sellerNewsTimeline;

    private static final int DASHBOARD_PAGE_SIZE = 5;

    private final HotItemCache hotCache = HotItemCache.getInstance();

    private Timeline hotRefreshTimeline;

    private Timeline hotCountdownTimeline;

    private Timeline sellerHotRefreshTimeline;

    private Timeline sellerHotCountdownTimeline;

    private final Map<String, Auction> visibleHotAuctions = new HashMap<>();

    private final Map<String, Auction> visibleSellerHotAuctions = new HashMap<>();

    private List<Auction> runningDashboardAuctions = List.of();

    private List<Auction> upcomingDashboardAuctions = List.of();

    private List<Auction> sellerRunningDashboardAuctions = List.of();

    private List<Auction> sellerUpcomingDashboardAuctions = List.of();

    private int runningPageIndex = 0;

    private int upcomingPageIndex = 0;

    private int sellerRunningPageIndex = 0;

    private int sellerUpcomingPageIndex = 0;

    private volatile boolean isRefreshing = false;

    private volatile boolean isSellerRefreshing = false;

    @FXML

    public void initialize() {
        Platform.runLater(() -> {
            if (sellerRevenueHeatmap != null && sellerRevenueHeatmap.getScene() != null) {
                sellerRevenueHeatmap.getScene().addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
                    if (activeHeatmapTooltip != null && activeHeatmapTooltip.isShowing()) {
                        activeHeatmapTooltip.hide();
                        activeHeatmapTooltip = null;
                    }
                });
            }
        });

        loadData();
        setupRealtime();
    }

    @Override

    protected void handleWsMessage(JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "";
        if ("AUCTION_STATUS_CHANGED".equals(type) || "AUCTION_CREATED".equals(type)) {
            Platform.runLater(this::loadData);
        }
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

    private void startSellerNewsTicker() {
        if (sellerNewsLabel == null)
            return;
        sellerNewsTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.seconds(8), event -> {
                    javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(Duration.millis(400),
                            sellerNewsLabel);
                    fadeOut.setFromValue(1.0);
                    fadeOut.setToValue(0.0);
                    fadeOut.setOnFinished(e -> {
                        currentNewsIndex = (currentNewsIndex + 1) % newsHeadlines.length;
                        sellerNewsLabel.setText(newsHeadlines[currentNewsIndex]);
                        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
                                Duration.millis(400), sellerNewsLabel);
                        fadeIn.setFromValue(0.0);
                        fadeIn.setToValue(1.0);
                        fadeIn.play();
                    });
                    fadeOut.play();
                }));
        sellerNewsTimeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        sellerNewsTimeline.play();
    }

    private void loadData() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null) {
            com.auction.ui.controller.DesktopHeaderController.setTitleAndSubtitle("Tổng quan", null);

            if (exploreButton != null) {
                exploreButton.setVisible(true);
                exploreButton.setManaged(true);
            }

            if (user instanceof Seller seller) {

                if (bidderView != null) {
                    bidderView.setVisible(false);
                    bidderView.setManaged(false);
                }
                if (sellerView != null) {
                    sellerView.setVisible(true);
                    sellerView.setManaged(true);
                }
                setupStatCardsByRole(user.getRole(), false);
                startSellerNewsTicker();
                startSellerHotCountdownRefresh();
                loadSellerStats((Seller) user);

                taskRunner.run("dashboard-seller-load", () -> {
                    List<Auction> all = app.getAuctionsBySeller(seller);
                    hotCache.seedFromList(all);
                    return all;
                }, all -> {
                    LocalDateTime now = TimeSyncManager.getNow();
                    long running = all.stream().filter(a -> isRunningByTime(a, now)).count();
                    long open    = all.stream().filter(DashboardController.this::isUpcoming).count();
                    if (sellerDashStatRunning != null) sellerDashStatRunning.setText(String.valueOf(running));
                    if (sellerDashStatOpen    != null) sellerDashStatOpen.setText(String.valueOf(open));
                    refreshSellerHotItems(all);
                    startSellerHotItemRefresh();
                }, error -> System.err.println("[Dashboard] loadData failed: " + error.getMessage()));
            } else {

                if (bidderView != null) {
                    bidderView.setVisible(true);
                    bidderView.setManaged(true);
                }
                if (sellerView != null) {
                    sellerView.setVisible(false);
                    sellerView.setManaged(false);
                }
                setupStatCardsByRole(user.getRole(), true);
                startNewsTicker();
                startHotCountdownRefresh();

                taskRunner.run("dashboard-load", () -> {
                    List<Auction> all = user.getRole().equalsIgnoreCase("Admin")
                            ? app.getAllAuctions()
                            : app.getPublicAuctions();
                    hotCache.seedFromList(all);
                    int totalUsers = 0;
                    if (user.getRole().equalsIgnoreCase("Admin")) {
                        totalUsers = app.getAllUsers().size();
                    }
                    return new Object[] { all, totalUsers };
                }, result -> {
                    List<Auction> all = (List<Auction>) ((Object[]) result)[0];
                    int totalUsers = (int) ((Object[]) result)[1];
                    LocalDateTime now = TimeSyncManager.getNow();
                    long running = all.stream().filter(a -> isRunningByTime(a, now)).count();
                    long open    = all.stream().filter(DashboardController.this::isUpcoming).count();
                    long pending = all.stream().filter(a -> a.getStatus() == AuctionStatus.PENDING).count();
                    if (dashStatRunning      != null) dashStatRunning.setText(String.valueOf(running));
                    if (dashStatOpen         != null) dashStatOpen.setText(String.valueOf(open));
                    if (dashStatPending       != null) dashStatPending.setText(String.valueOf(pending));
                    if (dashStatTotalAuctions != null) dashStatTotalAuctions.setText(String.valueOf(all.size()));
                    if (dashStatTotalUsers    != null) dashStatTotalUsers.setText(String.valueOf(totalUsers));
                    refreshHotItems(all);
                    startHotItemRefresh();
                }, error -> System.err.println("[Dashboard] loadData failed: " + error.getMessage()));
            }
        }
    }

    private void setupStatCardsByRole(String role, boolean isBidderView) {
        boolean isAdmin = role != null && role.equalsIgnoreCase("Admin");

        if (isBidderView) {

            setCardVisibility(cardPending, isAdmin);
            setCardVisibility(cardTotalAuctions, isAdmin);
            setCardVisibility(cardTotalUsers, isAdmin);
        } else {

            setCardVisibility(sellerCardPending, isAdmin);
            setCardVisibility(sellerCardTotalAuctions, isAdmin);
            setCardVisibility(sellerCardTotalUsers, isAdmin);
        }
    }

    private void setCardVisibility(VBox card, boolean visible) {
        if (card == null)
            return;
        card.setVisible(visible);

    }

    private void loadSellerStats(Seller seller) {
        if (sellerEarningLabel == null)
            return;
        sellerEarningLabel.setText("...");
        sellerRevenueLabel.setText("...");

        taskRunner.run("dashboard-seller-stats", () -> app.getAuctionsBySeller(seller), auctions -> {
            LocalDate today = TimeSyncManager.getNow().toLocalDate();
            LocalDate firstDay = today.minusDays(34);
            Map<LocalDate, Double> revenueByDay = new HashMap<>();
            Map<LocalDate, Integer> closedByDay = new HashMap<>();
            Map<String, Integer> categoryCount = new HashMap<>();

            double totalRevenue = 0;
            double monthRevenue = 0;

            for (Auction a : auctions) {
                if (a.getStatus() != AuctionStatus.CLOSED)
                    continue;

                if (a.getItem() != null && a.getItem().getCategory() != null) {
                    String catName = a.getItem().getCategory();
                    categoryCount.merge(catName, 1, Integer::sum);
                }

                double amount = Math.max(0, a.getHighestBid());
                LocalDate day = a.getEndTime().toLocalDate();
                totalRevenue += amount;
                if (!day.isBefore(today.minusDays(29)) && !day.isAfter(today))
                    monthRevenue += amount;
                if (!day.isBefore(firstDay) && !day.isAfter(today)) {
                    revenueByDay.merge(day, amount, Double::sum);
                    closedByDay.merge(day, 1, Integer::sum);
                }
            }

            final double total = totalRevenue;
            final double month = monthRevenue;
            final Map<LocalDate, Double> byDay = revenueByDay;
            final Map<LocalDate, Integer> closedMap = closedByDay;

            sellerEarningLabel.setText(String.format("%,.0f ₫", total));
            sellerRevenueLabel.setText(String.format("%,.0f ₫", month));

            if (sellerCategoryPieChart != null) {
                ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
                for (Map.Entry<String, Integer> entry : categoryCount.entrySet()) {

                    pieChartData.add(new PieChart.Data(entry.getKey(), entry.getValue()));
                }
                sellerCategoryPieChart.setData(pieChartData);

                sellerCategoryPieChart.setLegendVisible(false);

                sellerCategoryPieChart.setVisible(true);
                sellerCategoryPieChart.setOpacity(1.0);
                sellerCategoryPieChart.setPrefSize(240, 240);
                sellerCategoryPieChart.setMinSize(160, 160);

                sellerCategoryPieChart.setLabelsVisible(false);
                sellerCategoryPieChart.setStartAngle(90);
                sellerCategoryPieChart.setClockwise(true);

                Platform.runLater(() -> applyPieColors());
            }

            double maxDay = byDay.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            if (sellerRevenueHeatmap != null) {
                sellerRevenueHeatmap.getChildren().removeIf(node -> node.getStyleClass().contains("heatmap-cell"));
                for (int i = 0; i < 35; i++) {
                    int col = i % 7;
                    int row = i / 7;
                    LocalDate day = firstDay.plusDays(i);
                    double amt = byDay.getOrDefault(day, 0.0);
                    int closedDay = closedMap.getOrDefault(day, 0);
                    Region cell = new Region();

                    cell.getStyleClass().addAll("heatmap-cell", heatmapLevel(amt, maxDay));
                    Tooltip tooltip = new Tooltip(
                            "Tổng doanh thu: " + String.format("%,.0f đ", amt) + "\n" +
                                    "Số phiên đã chốt: " + closedDay + "\n" +
                                    "Ngày: " + day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                    tooltip.setShowDelay(javafx.util.Duration.millis(50));
                    tooltip.setShowDuration(javafx.util.Duration.INDEFINITE);
                    Tooltip.install(cell, tooltip);

                    cell.setOnMousePressed(event -> {
                        if (activeHeatmapTooltip != null && activeHeatmapTooltip.isShowing()) {
                            activeHeatmapTooltip.hide();
                        }
                        tooltip.show(cell, event.getScreenX() + 10, event.getScreenY() + 10);
                        activeHeatmapTooltip = tooltip;
                        event.consume();
                    });

                    cell.setOnMouseEntered(event -> {
                        if (activeHeatmapTooltip != null && activeHeatmapTooltip.isShowing()
                                && activeHeatmapTooltip != tooltip) {
                            activeHeatmapTooltip.hide();
                            activeHeatmapTooltip = null;
                        }
                    });

                    sellerRevenueHeatmap.add(cell, col, row + 1);
                }
            }
        }, error -> {
            sellerEarningLabel.setText("?");
            sellerRevenueLabel.setText("?");
        });
    }

    private String heatmapLevel(double amount, double maxDayRevenue) {
        if (amount <= 0 || maxDayRevenue <= 0)
            return "heatmap-level-0";
        double ratio = amount / maxDayRevenue;
        if (ratio < 0.25)
            return "heatmap-level-1";
        if (ratio < 0.50)
            return "heatmap-level-2";
        if (ratio < 0.75)
            return "heatmap-level-3";
        return "heatmap-level-4";
    }

    private void applyPieColors() {
        if (sellerCategoryPieChart == null)
            return;
        ObservableList<PieChart.Data> data = sellerCategoryPieChart.getData();
        if (data == null)
            return;
        String[] palette = pieColors;
        Platform.runLater(() -> {
            double total = data.stream().mapToDouble(PieChart.Data::getPieValue).sum();
            if (sellerPieLegend != null)
                sellerPieLegend.getChildren().clear();
            int i = 0;
            for (PieChart.Data d : data) {
                Node node = d.getNode();
                final int idx = i;
                String color = palette[i % palette.length];
                if (node != null) {
                    node.setStyle("-fx-pie-color: " + color + ";");

                    String perc = total > 0 ? String.format("%.1f%%", d.getPieValue() * 100.0 / total) : "0%";
                    Tooltip t = new Tooltip(d.getName() + "\nSố lượng: " + (int) d.getPieValue() + "\nTỷ lệ: " + perc);
                    Tooltip.install(node, t);

                    node.addEventHandler(MouseEvent.MOUSE_ENTERED, ev -> {
                        node.setScaleX(1.06);
                        node.setScaleY(1.06);
                    });
                    node.addEventHandler(MouseEvent.MOUSE_EXITED, ev -> {
                        node.setScaleX(1.0);
                        node.setScaleY(1.0);
                    });
                } else {

                    d.nodeProperty().addListener((obs, oldNode, newNode) -> {
                        if (newNode != null) {
                            newNode.setStyle("-fx-pie-color: " + color + ";");
                            String perc = total > 0 ? String.format("%.1f%%", d.getPieValue() * 100.0 / total) : "0%";
                            Tooltip t = new Tooltip(
                                    d.getName() + "\nSố lượng: " + (int) d.getPieValue() + "\nTỷ lệ: " + perc);
                            Tooltip.install(newNode, t);
                            newNode.addEventHandler(MouseEvent.MOUSE_ENTERED, ev -> {
                                newNode.setScaleX(1.06);
                                newNode.setScaleY(1.06);
                            });
                            newNode.addEventHandler(MouseEvent.MOUSE_EXITED, ev -> {
                                newNode.setScaleX(1.0);
                                newNode.setScaleY(1.0);
                            });
                        }
                    });
                }

                if (sellerPieLegend != null) {
                    HBox legendItem = new HBox(8);
                    legendItem.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    Region swatch = new Region();
                    swatch.setPrefSize(12, 12);
                    swatch.setStyle("-fx-background-color: " + color
                            + "; -fx-background-radius: 4; -fx-border-color: rgba(0,0,0,0.06); -fx-border-width: 1;");
                    Label lbl = new Label(d.getName() + " (" + (int) d.getPieValue() + ")");
                    lbl.setStyle("-fx-text-fill: -theme-text; -fx-font-size: 13px;");
                    legendItem.getChildren().addAll(swatch, lbl);
                    sellerPieLegend.getChildren().add(legendItem);
                }
                i++;
            }
        });
    }

    private void refreshHotItems(List<Auction> all) {
        LocalDateTime now = TimeSyncManager.getNow();
        runningDashboardAuctions = runningAuctions(all, now);
        upcomingDashboardAuctions = upcomingAuctions(all, now);
        runningPageIndex = clampPage(runningPageIndex, runningDashboardAuctions.size());
        upcomingPageIndex = clampPage(upcomingPageIndex, upcomingDashboardAuctions.size());

        visibleHotAuctions.clear();
        hotItemsBox.getChildren().setAll(
                createCarouselSection("🔥 Đang Diễn Ra", runningDashboardAuctions, runningPageIndex,
                        false, true, visibleHotAuctions),
                createCarouselSection("⏳ Sắp Diễn Ra", upcomingDashboardAuctions, upcomingPageIndex,
                        false, false, visibleHotAuctions));
    }

    private void refreshSellerHotItems(List<Auction> all) {
        if (sellerHotItemsBox == null)
            return;

        LocalDateTime now = TimeSyncManager.getNow();
        sellerRunningDashboardAuctions = runningAuctions(all, now);
        sellerUpcomingDashboardAuctions = upcomingAuctions(all, now);
        sellerRunningPageIndex = clampPage(sellerRunningPageIndex, sellerRunningDashboardAuctions.size());
        sellerUpcomingPageIndex = clampPage(sellerUpcomingPageIndex, sellerUpcomingDashboardAuctions.size());

        visibleSellerHotAuctions.clear();
        sellerHotItemsBox.getChildren().setAll(
                createCarouselSection("🔥 Đang Diễn Ra", sellerRunningDashboardAuctions, sellerRunningPageIndex,
                        true, true, visibleSellerHotAuctions),
                createCarouselSection("⏳ Sắp Diễn Ra", sellerUpcomingDashboardAuctions, sellerUpcomingPageIndex,
                        true, false, visibleSellerHotAuctions));
    }

    private List<Auction> runningAuctions(List<Auction> all, LocalDateTime now) {
        return dashboardAuctionService.runningAuctions(all, now);
    }

    private List<Auction> upcomingAuctions(List<Auction> all, LocalDateTime now) {
        return dashboardAuctionService.upcomingAuctions(all, now);
    }

    private boolean isRunningByTime(Auction auction, LocalDateTime now) {
        return dashboardAuctionService.isRunningByTime(auction, now);
    }

    private boolean isUpcomingByTimeOrStatus(Auction auction, LocalDateTime now) {
        return dashboardAuctionService.isUpcomingByTimeOrStatus(auction, now);
    }

    private VBox createCarouselSection(String title, List<Auction> auctions, int pageIndex,
                                       boolean sellerView, boolean runningRow,
                                       Map<String, Auction> visibleMap) {
        VBox section = new VBox(10);
        section.setMaxWidth(Double.MAX_VALUE);
        section.setStyle("-fx-background-color: -theme-surface; -fx-background-radius: 16; "
                + "-fx-border-color: -theme-border; -fx-border-radius: 16; -fx-padding: 14;");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");
        Label countLabel = new Label(auctions.size() + " sản phẩm");
        countLabel.getStyleClass().add("label-subtle");
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox header = new HBox(10, titleLabel, spacer, countLabel);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        HBox cards = new HBox(14);
        cards.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(cards, javafx.scene.layout.Priority.ALWAYS);

        int from = Math.min(pageIndex * DASHBOARD_PAGE_SIZE, auctions.size());
        int to = Math.min(from + DASHBOARD_PAGE_SIZE, auctions.size());
        if (auctions.isEmpty()) {
            Label empty = new Label(runningRow ? "Hiện chưa có phiên đang diễn ra." : "Hiện chưa có phiên sắp diễn ra.");
            empty.getStyleClass().add("label-subtle");
            cards.getChildren().add(empty);
        } else {
            for (Auction auction : auctions.subList(from, to)) {
                VBox card = createHotItemCard(auction);
                card.setUserData(auction.getId());
                visibleMap.put(auction.getId(), auction);
                cards.getChildren().add(card);
            }
        }

        Button previous = new Button("‹");
        Button next = new Button("›");
        previous.getStyleClass().addAll("btn-secondary", "carousel-arrow");
        next.getStyleClass().addAll("btn-secondary", "carousel-arrow");
        previous.setDisable(pageIndex <= 0);
        next.setDisable(to >= auctions.size());
        previous.setVisible(auctions.size() > DASHBOARD_PAGE_SIZE);
        next.setVisible(auctions.size() > DASHBOARD_PAGE_SIZE);
        previous.setManaged(auctions.size() > DASHBOARD_PAGE_SIZE);
        next.setManaged(auctions.size() > DASHBOARD_PAGE_SIZE);
        previous.setOnAction(e -> turnCarouselPage(sellerView, runningRow, -1));
        next.setOnAction(e -> turnCarouselPage(sellerView, runningRow, 1));

        HBox body = new HBox(10, previous, cards, next);
        body.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        animateCards(cards);
        section.getChildren().addAll(header, body);
        return section;
    }

    private void turnCarouselPage(boolean sellerView, boolean runningRow, int delta) {
        if (sellerView) {
            if (runningRow) {
                sellerRunningPageIndex = clampPage(sellerRunningPageIndex + delta, sellerRunningDashboardAuctions.size());
            } else {
                sellerUpcomingPageIndex = clampPage(sellerUpcomingPageIndex + delta, sellerUpcomingDashboardAuctions.size());
            }
            refreshSellerHotItems(sellerRunningDashboardAuctions.isEmpty() && sellerUpcomingDashboardAuctions.isEmpty()
                    ? List.of() : mergeDashboardLists(sellerRunningDashboardAuctions, sellerUpcomingDashboardAuctions));
        } else {
            if (runningRow) {
                runningPageIndex = clampPage(runningPageIndex + delta, runningDashboardAuctions.size());
            } else {
                upcomingPageIndex = clampPage(upcomingPageIndex + delta, upcomingDashboardAuctions.size());
            }
            refreshHotItems(runningDashboardAuctions.isEmpty() && upcomingDashboardAuctions.isEmpty()
                    ? List.of() : mergeDashboardLists(runningDashboardAuctions, upcomingDashboardAuctions));
        }
    }

    private List<Auction> mergeDashboardLists(List<Auction> first, List<Auction> second) {
        return dashboardAuctionService.merge(first, second);
    }

    private int clampPage(int pageIndex, int itemCount) {
        return dashboardAuctionService.clampPage(pageIndex, itemCount, DASHBOARD_PAGE_SIZE);
    }

    private void animateCards(HBox cards) {
        cards.setOpacity(0.0);
        cards.setTranslateX(24);
        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(Duration.millis(180), cards);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        javafx.animation.TranslateTransition slide = new javafx.animation.TranslateTransition(Duration.millis(180), cards);
        slide.setFromX(24);
        slide.setToX(0);
        new javafx.animation.ParallelTransition(fade, slide).play();
    }

    private LocalDateTime endTimeOrMax(Auction auction) {
        return auction.getEndTime() == null ? LocalDateTime.MAX : auction.getEndTime();
    }

    private LocalDateTime startTimeOrMax(Auction auction) {
        return auction.getStartTime() == null ? LocalDateTime.MAX : auction.getStartTime();
    }

    private boolean isUpcoming(Auction auction) {
        return isUpcomingByTimeOrStatus(auction, TimeSyncManager.getNow());
    }

    private void startHotItemRefresh() {
        hotRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            if (isRefreshing)
                return;
            isRefreshing = true;
            javafx.concurrent.Task<List<Auction>> refreshTask = new javafx.concurrent.Task<>() {
                @Override
                protected List<Auction> call() {
                    User user = SessionManager.getInstance().getCurrentUser();
                    if (user instanceof Seller seller) {
                        return app.getAuctionsBySeller(seller);
                    }
                    return user != null && user.getRole().equalsIgnoreCase("Admin")
                            ? app.getAllAuctions()
                            : app.getPublicAuctions();
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

    private void startSellerHotItemRefresh() {
        sellerHotRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            if (isSellerRefreshing)
                return;
            isSellerRefreshing = true;
            javafx.concurrent.Task<List<Auction>> refreshTask = new javafx.concurrent.Task<>() {
                @Override
                protected List<Auction> call() {
                    User user = SessionManager.getInstance().getCurrentUser();
                    if (user instanceof Seller seller) {
                        return app.getAuctionsBySeller(seller);
                    }
                    return user != null && user.getRole().equalsIgnoreCase("Admin")
                            ? app.getAllAuctions()
                            : app.getPublicAuctions();
                }
            };
            refreshTask.setOnSucceeded(ev -> {
                isSellerRefreshing = false;
                hotCache.seedFromList(refreshTask.getValue());
                Platform.runLater(() -> refreshSellerHotItems(refreshTask.getValue()));
            });
            refreshTask.setOnFailed(ev -> isSellerRefreshing = false);
            Thread t = new Thread(refreshTask, "seller-hot-refresh");
            t.setDaemon(true);
            t.start();
        }));
        sellerHotRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        sellerHotRefreshTimeline.play();
    }

    private void startHotCountdownRefresh() {
        hotCountdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateHotCountdownLabels()));
        hotCountdownTimeline.setCycleCount(Timeline.INDEFINITE);
        hotCountdownTimeline.play();
    }

    private void startSellerHotCountdownRefresh() {
        sellerHotCountdownTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> updateSellerHotCountdownLabels()));
        sellerHotCountdownTimeline.setCycleCount(Timeline.INDEFINITE);
        sellerHotCountdownTimeline.play();
    }

    private void updateHotCountdownLabels() {
        updateCountdownLabelsIn(hotItemsBox, visibleHotAuctions);
    }

    private void updateSellerHotCountdownLabels() {
        if (sellerHotItemsBox == null)
            return;
        updateCountdownLabelsIn(sellerHotItemsBox, visibleSellerHotAuctions);
    }

    private void updateCountdownLabelsIn(Parent root, Map<String, Auction> visibleAuctions) {
        if (root == null)
            return;
        boolean statusChanged = false;
        LocalDateTime now = TimeSyncManager.getNow();

        for (Node node : root.getChildrenUnmodifiable()) {
            if (node instanceof VBox card && card.getUserData() instanceof String auctionId) {
                Auction auction = visibleAuctions.get(auctionId);
                if (auction != null && card.getChildren().size() >= 5) {
                    Label statusBadge = (Label) card.getChildren().get(3);
                    Label countdown = (Label) card.getChildren().get(4);
                    countdown.setText(formatCountdown(auction));

                    if (auction.getStatus() == AuctionStatus.UPCOMING && auction.getStartTime() != null
                            && !now.isBefore(auction.getStartTime())) {
                        try {
                            auction.goLive();
                            statusBadge.setText(auction.getStatusDisplay());
                            statusBadge.getStyleClass().setAll("badge", "badge-running");
                            statusChanged = true;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            if (node instanceof Parent parent) {
                updateCountdownLabelsIn(parent, visibleAuctions);
            }
        }

        if (statusChanged) {
            Platform.runLater(() -> {
                User user = SessionManager.getInstance().getCurrentUser();
                if (user instanceof Seller) {
                    List<Auction> all = mergeDashboardLists(sellerRunningDashboardAuctions, sellerUpcomingDashboardAuctions);
                    if (!all.isEmpty()) refreshSellerHotItems(all);
                } else {
                    List<Auction> all = mergeDashboardLists(runningDashboardAuctions, upcomingDashboardAuctions);
                    if (!all.isEmpty()) refreshHotItems(all);
                }
            });
        }
    }

    private VBox createHotItemCard(Auction auction) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setStyle(
                "-fx-background-radius: 16; -fx-border-radius: 16; -fx-min-width: 188; -fx-pref-width: 188; -fx-max-width: 188; -fx-alignment: center;");
        card.setCursor(Cursor.HAND);

        ImageView iv = new ImageView();
        iv.setFitWidth(166);
        iv.setFitHeight(104);
        iv.setPreserveRatio(true);
        String imgUrl = auction.getItem() != null ? auction.getItem().getImageUrl() : null;
        if (imgUrl != null && !imgUrl.isEmpty()) {
            javafx.scene.image.Image cached = com.auction.core.util.CacheManager.getInstance()
                    .getImage(imgUrl + "_200_120");
            if (cached != null) {
                iv.setImage(cached);
            } else {
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
            javafx.concurrent.Task<Auction> fetchTask = new javafx.concurrent.Task<>() {
                @Override
                protected Auction call() throws Exception {
                    return app.findAuctionById(auction.getId()).orElse(auction);
                }
            };
            fetchTask.setOnSucceeded(ev -> {
                try {
                    nav.navigateTo(NavigationManager.AUCTION_DETAIL, "Chi tiết",
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
        return dashboardAuctionService.formatCountdown(auction, TimeSyncManager.getNow());
    }

    @Override

    public void cleanup() {
        super.cleanup();
        if (newsTimeline != null)
            newsTimeline.stop();
        if (sellerNewsTimeline != null)
            sellerNewsTimeline.stop();
        if (hotRefreshTimeline != null)
            hotRefreshTimeline.stop();
        if (hotCountdownTimeline != null)
            hotCountdownTimeline.stop();
        if (sellerHotRefreshTimeline != null)
            sellerHotRefreshTimeline.stop();
        if (sellerHotCountdownTimeline != null)
            sellerHotCountdownTimeline.stop();
    }
}
