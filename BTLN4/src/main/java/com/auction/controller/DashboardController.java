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
import javafx.scene.layout.GridPane;
import javafx.scene.chart.PieChart;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
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
import com.auction.util.ImageLoaderUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

public class DashboardController {

    @FXML
    private Label welcomeLabel;
    
    // Bidder View Elements
    @FXML
    private VBox bidderView;
    @FXML
    private Label newsLabel;
    @FXML
    private FlowPane hotItemsBox;
    
    // Seller View Elements
    @FXML
    private VBox sellerView;
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
    private FlowPane sellerPieLegend;

    private final AppFacade app = AppFacade.getInstance();
    private Tooltip activeHeatmapTooltip;

    // Pie chart color palettes (blue-focused for light and dark themes)
    private final String[] lightPieColors = new String[] {"#93C5FD", "#60A5FA", "#3B82F6", "#2563EB", "#1E3A8A"};
    private final String[] darkPieColors = new String[] {"#60A5FA", "#3B82F6", "#2563EB", "#1E40AF", "#1E3A8A"};

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
    private volatile boolean isRefreshing = false;

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
            
            if (user instanceof Seller) {
                // Setup Seller View
                if (bidderView != null) {
                    bidderView.setVisible(false);
                    bidderView.setManaged(false);
                }
                if (sellerView != null) {
                    sellerView.setVisible(true);
                    sellerView.setManaged(true);
                }
                loadSellerStats((Seller) user);
            } else {
                // Setup Bidder/Admin View
                if (bidderView != null) {
                    bidderView.setVisible(true);
                    bidderView.setManaged(true);
                }
                if (sellerView != null) {
                    sellerView.setVisible(false);
                    sellerView.setManaged(false);
                }
                startNewsTicker();
                startHotCountdownRefresh();
                
                javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
                    private List<Auction> all;
                    @Override
                    protected Void call() {
                        all = app.getAllAuctions();
                        hotCache.seedFromList(all);
                        return null;
                    }
                    @Override
                    protected void succeeded() {
                        refreshHotItems(all);
                        startHotItemRefresh();
                    }
                    @Override
                    protected void failed() {
                        System.err.println("[Dashboard] loadData failed: " + getException().getMessage());
                    }
                };
                Thread t = new Thread(task, "dashboard-load");
                t.setDaemon(true);
                t.start();
            }
        }
    }
    
    private void loadSellerStats(Seller seller) {
        if (sellerEarningLabel == null) return;
        sellerEarningLabel.setText("...");
        sellerRevenueLabel.setText("...");
        sellerClosedLabel.setText("...");

        javafx.concurrent.Task<List<Auction>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<Auction> call() {
                return app.getAuctionsBySeller(seller);
            }
        };
        task.setOnSucceeded(e -> {
            List<Auction> auctions = task.getValue();
            LocalDate today = TimeSyncManager.getNow().toLocalDate();
            LocalDate firstDay = today.minusDays(34);
            Map<LocalDate, Double> revenueByDay = new HashMap<>();
            Map<LocalDate, Integer> closedByDay = new HashMap<>();
            Map<String, Integer> categoryCount = new HashMap<>();
            
            double totalRevenue = 0;
            double monthRevenue = 0;
            int closedCount = 0;

            for (Auction a : auctions) {
                // Count category for all seller's items
                    if (a.getItem() != null && a.getItem().getCategory() != null) {
                    String catName = a.getItem().getCategory();
                    categoryCount.merge(catName, 1, Integer::sum);
                }
                
                if (a.getStatus() != AuctionStatus.CLOSED) continue;
                double amount = Math.max(0, a.getHighestBid());
                LocalDate day = a.getEndTime().toLocalDate();
                totalRevenue += amount;
                closedCount++;
                if (!day.isBefore(today.minusDays(29)) && !day.isAfter(today))
                    monthRevenue += amount;
                if (!day.isBefore(firstDay) && !day.isAfter(today)) {
                    revenueByDay.merge(day, amount, Double::sum);
                    closedByDay.merge(day, 1, Integer::sum);
                }
            }

            final double total = totalRevenue;
            final double month = monthRevenue;
            final int closed = closedCount;
            final Map<LocalDate, Double> byDay = revenueByDay;
            final Map<LocalDate, Integer> closedMap = closedByDay;

            sellerEarningLabel.setText(String.format("%,.0f ₫", total));
            sellerRevenueLabel.setText(String.format("%,.0f ₫", month));
            sellerClosedLabel.setText(String.valueOf(closed));

            // Populate PieChart and apply theme-aware colors
            if (sellerCategoryPieChart != null) {
                ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
                for (Map.Entry<String, Integer> entry : categoryCount.entrySet()) {
                    pieChartData.add(new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue()));
                }
                sellerCategoryPieChart.setData(pieChartData);
                // hide built-in legend — we use a custom color legend below
                sellerCategoryPieChart.setLegendVisible(false);
                // ensure chart is visible and sized (fix for cases where chart didn't render)
                sellerCategoryPieChart.setVisible(true);
                sellerCategoryPieChart.setOpacity(1.0);
                sellerCategoryPieChart.setPrefSize(240, 240);
                sellerCategoryPieChart.setMinSize(160, 160);
                // Tidy up appearance: hide slice labels, make donut-style start angle
                sellerCategoryPieChart.setLabelsVisible(false);
                sellerCategoryPieChart.setStartAngle(90);
                sellerCategoryPieChart.setClockwise(true);

                // Ensure colors are applied after nodes are created
                Platform.runLater(() -> applyPieColors());

                // Attach listener to theme changes so pie colors update when dark-mode toggles
                if (sellerCategoryPieChart.getScene() != null) {
                    Parent root = sellerCategoryPieChart.getScene().getRoot();
                    root.getStyleClass().addListener((ListChangeListener<String>) change -> applyPieColors());
                } else {
                    sellerCategoryPieChart.sceneProperty().addListener((obs, oldScene, newScene) -> {
                        if (newScene != null) {
                            Parent root = newScene.getRoot();
                            root.getStyleClass().addListener((ListChangeListener<String>) change -> applyPieColors());
                        }
                    });
                }
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
                        if (activeHeatmapTooltip != null && activeHeatmapTooltip.isShowing() && activeHeatmapTooltip != tooltip) {
                            activeHeatmapTooltip.hide();
                            activeHeatmapTooltip = null;
                        }
                    });

                    sellerRevenueHeatmap.add(cell, col, row + 1);
                }
            }
        });
        task.setOnFailed(e -> {
            sellerEarningLabel.setText("?");
            sellerRevenueLabel.setText("?");
            sellerClosedLabel.setText("?");
        });
        new Thread(task, "dashboard-seller-stats").start();
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

    // Determine current theme by checking root style class
    private boolean isDarkMode() {
        if (sellerCategoryPieChart == null || sellerCategoryPieChart.getScene() == null)
            return false;
        Parent root = sellerCategoryPieChart.getScene().getRoot();
        return root.getStyleClass().contains("dark-mode");
    }

    // Apply colors to pie chart slices according to current theme
    private void applyPieColors() {
        if (sellerCategoryPieChart == null) return;
        ObservableList<PieChart.Data> data = sellerCategoryPieChart.getData();
        if (data == null) return;
        String[] palette = isDarkMode() ? darkPieColors : lightPieColors;
        Platform.runLater(() -> {
            double total = data.stream().mapToDouble(PieChart.Data::getPieValue).sum();
            if (sellerPieLegend != null) sellerPieLegend.getChildren().clear();
            int i = 0;
            for (PieChart.Data d : data) {
                Node node = d.getNode();
                final int idx = i;
                String color = palette[i % palette.length];
                if (node != null) {
                    node.setStyle("-fx-pie-color: " + color + ";");

                    // Tooltip: show name, count and percent
                    String perc = total > 0 ? String.format("%.1f%%", d.getPieValue() * 100.0 / total) : "0%";
                    Tooltip t = new Tooltip(d.getName() + "\nSố: " + (int) d.getPieValue() + "\n" + perc);
                    Tooltip.install(node, t);

                    // Hover effect: slight pop-out
                    node.addEventHandler(MouseEvent.MOUSE_ENTERED, ev -> {
                        node.setScaleX(1.06);
                        node.setScaleY(1.06);
                    });
                    node.addEventHandler(MouseEvent.MOUSE_EXITED, ev -> {
                        node.setScaleX(1.0);
                        node.setScaleY(1.0);
                    });
                } else {
                    // If node not yet created, attach listener to set style and tooltip later
                    d.nodeProperty().addListener((obs, oldNode, newNode) -> {
                        if (newNode != null) {
                            newNode.setStyle("-fx-pie-color: " + color + ";");
                            String perc = total > 0 ? String.format("%.1f%%", d.getPieValue() * 100.0 / total) : "0%";
                            Tooltip t = new Tooltip(d.getName() + "\nSố: " + (int) d.getPieValue() + "\n" + perc);
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

                // Add legend item (color swatch + label)
                if (sellerPieLegend != null) {
                    HBox legendItem = new HBox(8);
                    legendItem.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    Region swatch = new Region();
                    swatch.setPrefSize(12, 12);
                    swatch.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 4; -fx-border-color: rgba(0,0,0,0.06); -fx-border-width: 1;");
                    Label lbl = new Label(d.getName() + " (" + (int) d.getPieValue() + ")");
                    lbl.getStyleClass().add("chart-legend-item");
                    legendItem.getChildren().addAll(swatch, lbl);
                    sellerPieLegend.getChildren().add(legendItem);
                }
                i++;
            }
        });
    }

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
                card.setUserData(a.getId());
                hotItemsBox.getChildren().add(card);
            }
        } else {
            for (int i = 0; i < hotList.size(); i++) {
                Auction a = hotList.get(i);
                VBox card = (VBox) hotItemsBox.getChildren().get(i);
                if (card.getChildren().size() >= 5) {
                    Label price = (Label) card.getChildren().get(2);
                    price.setText(String.format("Giá: %,.0f ₫", a.getHighestBid()));

                    Label status = (Label) card.getChildren().get(3);
                    status.setText(a.getStatusDisplay());

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

    private void startHotItemRefresh() {
        hotRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            if (isRefreshing)
                return; 
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
        card.setStyle("-fx-background-radius: 16; -fx-border-radius: 16; -fx-min-width: 240; -fx-pref-width: 240; -fx-alignment: center;");
        card.setCursor(Cursor.HAND);

        ImageView iv = new ImageView();
        iv.setFitWidth(200);
        iv.setFitHeight(120);
        iv.setPreserveRatio(true);
        String imgUrl = auction.getItem() != null ? auction.getItem().getImageUrl() : null;
        if (imgUrl != null && !imgUrl.isEmpty()) {
            javafx.scene.image.Image cached = com.auction.util.CacheManager.getInstance()
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
