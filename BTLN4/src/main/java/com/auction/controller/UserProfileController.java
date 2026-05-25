package com.auction.controller;

import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.service.AppFacade;
import com.auction.util.SessionManager;
import com.auction.util.TimeSyncManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.UnaryOperator;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Modality;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import com.auction.util.ImageLoaderUtil;
import com.auction.util.NavigationManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserProfileController {

    @FXML
    private Label avatarLabel;
    @FXML
    private Label displayNameLabel;
    @FXML
    private Label roleLabel;
    @FXML
    private Label memberSinceLabel;

    @FXML
    private VBox bidderStatsBox;
    @FXML
    private Label balanceLabel;
    @FXML
    private Label totalBidsLabel;

    @FXML
    private VBox sellerStatsBox;
    @FXML
    private Label shopNameLabel;
    @FXML
    private Label auctionCountLabel;

    @FXML
    private VBox shopNameBox;
    @FXML
    private TextField shopNameField;
    @FXML
    private VBox balanceBox;
    @FXML
    private TextField depositField;
    @FXML
    private Label profileErrorLabel;
    @FXML
    private Label profileSuccessLabel;

    // ── Seller activity panel ──────────────────────────────────────────────────
    @FXML
    private VBox sellerActivityBox;
    @FXML
    private Label sellerEarningLabel;
    @FXML
    private Label sellerRevenueLabel;
    @FXML
    private Label sellerClosedLabel;
    @FXML
    private GridPane sellerRevenueHeatmap;

    private User currentUser;
    private final AppFacade app = AppFacade.getInstance();
    private Tooltip activeHeatmapTooltip;

    @FXML
    public void initialize() {
        currentUser = SessionManager.getInstance().getCurrentUser();
        profileErrorLabel.setText("");
        profileSuccessLabel.setText("");

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

        com.auction.util.CurrencyUtil.setupCurrencyTextField(depositField);

        if (currentUser != null) {
            populateProfile();
            // Fetch fresh data from server to ensure balance is up-to-date
            Task<java.util.Optional<User>> task = new Task<>() {
                @Override
                protected java.util.Optional<User> call() {
                    return app.findUserById(currentUser.getId());
                }
            };
            task.setOnSucceeded(e -> {
                task.getValue().ifPresent(u -> {
                    SessionManager.getInstance().setCurrentUser(u);
                    currentUser = u;
                    populateProfile();
                });
            });
            Thread t = new Thread(task, "profile-refresh");
            t.setDaemon(true);
            t.start();
        }
    }

    private void populateProfile() {
        displayNameLabel.setText(currentUser.getUsername());
        roleLabel.setText(currentUser.getRole());
        memberSinceLabel.setText("Thành viên từ: " + currentUser.getCreatedAt().toLocalDate());

        if (bidderStatsBox != null) {
            bidderStatsBox.setVisible(false);
            bidderStatsBox.setManaged(false);
        }
        if (sellerStatsBox != null) {
            sellerStatsBox.setVisible(false);
            sellerStatsBox.setManaged(false);
        }
        if (balanceBox != null) {
            balanceBox.setVisible(false);
            balanceBox.setManaged(false);
        }
        if (shopNameBox != null) {
            shopNameBox.setVisible(false);
            shopNameBox.setManaged(false);
        }

        if (currentUser instanceof Bidder bidder) {
            if (bidderStatsBox != null) {
                bidderStatsBox.setVisible(true);
                bidderStatsBox.setManaged(true);
            }
            if (balanceBox != null) {
                balanceBox.setVisible(true);
                balanceBox.setManaged(true);
            }
            balanceLabel.setText(String.format("%,.0f ₫", bidder.getAccountBalance()));
            loadBidCount(bidder);

        } else if (currentUser instanceof Seller seller) {
            if (sellerStatsBox != null) {
                sellerStatsBox.setVisible(true);
                sellerStatsBox.setManaged(true);
            }
            if (shopNameBox != null) {
                shopNameBox.setVisible(true);
                shopNameBox.setManaged(true);
            }
            if (sellerActivityBox != null) {
                sellerActivityBox.setVisible(true);
                sellerActivityBox.setManaged(true);
            }
            shopNameLabel.setText(seller.getShopName());
            if (shopNameField != null)
                shopNameField.setText(seller.getShopName());
            loadSellerAuctionCount(seller);
            loadSellerStats(seller);
        }
    }

    private static final java.util.Map<String, Long> bidCountCache = new java.util.concurrent.ConcurrentHashMap<>();

    public static void preloadCache(java.util.List<Auction> fullAuctions) {
        bidCountCache.clear();
        for (Auction full : fullAuctions) {
            for (com.auction.model.BidTransaction b : full.getBidHistory()) {
                bidCountCache.merge(b.getBidder().getId(), 1L, Long::sum);
            }
        }
    }

    private void loadBidCount(Bidder bidder) {
        if (totalBidsLabel == null)
            return;

        Long cached = bidCountCache.get(bidder.getId());
        if (cached != null) {
            totalBidsLabel.setText(String.valueOf(cached));
        } else {
            totalBidsLabel.setText("...");
        }

        Task<Long> task = new Task<>() {
            @Override
            protected Long call() {
                long count = 0;
                for (Auction shallow : app.getAllAuctions()) {
                    Auction full = app.findAuctionById(shallow.getId()).orElse(null);
                    if (full != null) {
                        count += full.getBidHistory().stream()
                                .filter(b -> b.getBidder().getId().equals(bidder.getId()))
                                .count();
                    }
                }
                return count;
            }
        };
        task.setOnSucceeded(e -> {
            bidCountCache.put(bidder.getId(), task.getValue());
            totalBidsLabel.setText(String.valueOf(task.getValue()));
        });
        task.setOnFailed(e -> {
            if (bidCountCache.get(bidder.getId()) == null)
                totalBidsLabel.setText("?");
        });
        new Thread(task, "profile-bid-count").start();
    }

    private void loadSellerAuctionCount(Seller seller) {
        if (auctionCountLabel == null)
            return;
        auctionCountLabel.setText("...");
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                return app.getAuctionsBySeller(seller).size();
            }
        };
        task.setOnSucceeded(e -> auctionCountLabel.setText(String.valueOf(task.getValue())));
        task.setOnFailed(e -> auctionCountLabel.setText("?"));
        new Thread(task, "profile-auction-count").start();
    }

    /** Load seller revenue stats & heatmap async. */
    private void loadSellerStats(Seller seller) {
        if (sellerEarningLabel == null)
            return;
        sellerEarningLabel.setText("...");
        sellerRevenueLabel.setText("...");
        sellerClosedLabel.setText("...");

        Task<List<Auction>> task = new Task<>() {
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
            double totalRevenue = 0;
            double monthRevenue = 0;
            int closedCount = 0;

            for (Auction a : auctions) {
                if (a.getStatus() != AuctionStatus.CLOSED)
                    continue;
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

            double maxDay = byDay.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            if (sellerRevenueHeatmap != null) {
                // Giữ nguyên Hàng 0 chứa Label. Chỉ xóa các ô vuông cũ
                sellerRevenueHeatmap.getChildren().removeIf(node -> node.getStyleClass().contains("heatmap-cell"));
                // 7 columns (days of week) × 5 rows (weeks) — GitHub-style horizontal grid
                for (int i = 0; i < 35; i++) {
                    int col = i % 7; // 0=Sun … 6=Sat
                    int row = i / 7; // 0=oldest week … 4=current week
                    LocalDate day = firstDay.plusDays(i);
                    double amt = byDay.getOrDefault(day, 0.0);
                    int closedDay = closedMap.getOrDefault(day, 0);
                    Region cell = new Region();

                    // Vô hiệu hóa Stretch để giữ chuẩn kích thước 24x24 đã khai báo trong CSS

                    cell.getStyleClass().addAll("heatmap-cell", heatmapLevel(amt, maxDay));
                    Tooltip tooltip = new Tooltip(
                            "Tổng doanh thu: " + String.format("%,.0f đ", amt) + "\n" +
                            "Số phiên đã chốt: " + closedDay + "\n" +
                            "Ngày: " + day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                    tooltip.setShowDelay(javafx.util.Duration.millis(50));
                    tooltip.setShowDuration(javafx.util.Duration.INDEFINITE);
                    Tooltip.install(cell, tooltip);

                    // Khi click chuột vào ô vuông heatmap, hiển thị Tooltip ngay tại vị trí con trỏ chuột
                    cell.setOnMousePressed(event -> {
                        if (activeHeatmapTooltip != null && activeHeatmapTooltip.isShowing()) {
                            activeHeatmapTooltip.hide();
                        }
                        tooltip.show(cell, event.getScreenX() + 10, event.getScreenY() + 10);
                        activeHeatmapTooltip = tooltip;
                        event.consume(); // Ngăn không cho sự kiện lan lên Scene để tránh bị tự động ẩn
                    });

                    // Khi rê chuột sang ô khác, tự động ẩn Tooltip cũ đang mở
                    cell.setOnMouseEntered(event -> {
                        if (activeHeatmapTooltip != null && activeHeatmapTooltip.isShowing() && activeHeatmapTooltip != tooltip) {
                            activeHeatmapTooltip.hide();
                            activeHeatmapTooltip = null;
                        }
                    });

                    // Bắt đầu nhồi dữ liệu từ Hàng 1 (Row = row + 1)
                    sellerRevenueHeatmap.add(cell, col, row + 1);
                }
            }
        });
        task.setOnFailed(e -> {
            sellerEarningLabel.setText("?");
            sellerRevenueLabel.setText("?");
            sellerClosedLabel.setText("?");
        });
        new Thread(task, "profile-seller-stats").start();
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

    @FXML
    private void handleSaveProfile(ActionEvent event) {
        profileErrorLabel.setText("");
        profileSuccessLabel.setText("Đang lưu...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                if (currentUser instanceof Seller seller
                        && shopNameField != null
                        && !shopNameField.getText().trim().isEmpty()) {
                    seller.setShopName(shopNameField.getText().trim());
                }
                app.saveUser(currentUser);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            profileSuccessLabel.setText("Thông tin đã được cập nhật.");
            populateProfile();
        });
        task.setOnFailed(e -> Platform.runLater(() -> {
            profileErrorLabel.setText("Lỗi lưu dữ liệu: " + task.getException().getMessage());
            profileSuccessLabel.setText("");
        }));
        new Thread(task, "save-profile").start();
    }

    @FXML
    private void handleDeposit(ActionEvent event) {
        profileErrorLabel.setText("");
        profileSuccessLabel.setText("");
        if (!(currentUser instanceof Bidder bidder))
            return;
        String input = depositField.getText().trim();
        if (input.isEmpty())
            return;
        try {
            double amount = com.auction.util.CurrencyUtil.parseCurrency(input);
            if (amount <= 0)
                throw new NumberFormatException();

            showQRDepositModal(bidder, amount);

        } catch (NumberFormatException e) {
            profileErrorLabel.setText("Số tiền không hợp lệ.");
        }
    }

    private void showQRDepositModal(Bidder bidder, double amount) {
        Stage qrStage = new Stage();
        qrStage.initModality(Modality.APPLICATION_MODAL);
        qrStage.initStyle(StageStyle.UTILITY);
        qrStage.setTitle("Thanh toán quét mã VietQR");
        qrStage.setMinWidth(460);
        qrStage.setMinHeight(600);

        // ── Root layout ──────────────────────────────────────────────────────────
        VBox dialogRoot = new VBox(16);
        dialogRoot.setPadding(new Insets(28, 28, 24, 28));
        dialogRoot.setAlignment(Pos.TOP_CENTER);
        dialogRoot.setFillWidth(true);
        dialogRoot.getStyleClass().add("main-container");
        if (NavigationManager.getInstance().isDarkMode()) {
            dialogRoot.getStyleClass().add("dark-mode");
        }

        // ── Header ───────────────────────────────────────────────────────────────
        Label titleLabel = new Label("🏦  QUÉT MÃ ĐỂ NẠP TIỀN");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -theme-primary;");

        Label subtitleLabel = new Label("Sử dụng ứng dụng ngân hàng hoặc ví điện tử để quét mã QR bên dưới");
        subtitleLabel.setMaxWidth(Double.MAX_VALUE);
        subtitleLabel.setWrapText(true);
        subtitleLabel.setAlignment(Pos.CENTER);
        subtitleLabel.getStyleClass().add("label-subtle");

        // ── QR Frame ─────────────────────────────────────────────────────────────
        VBox qrFrame = new VBox();
        qrFrame.setAlignment(Pos.CENTER);
        qrFrame.setStyle("-fx-background-color: white; -fx-padding: 12;"
                + " -fx-border-radius: 12; -fx-background-radius: 12;"
                + " -fx-border-color: #E2E8F0; -fx-border-width: 1;");
        qrFrame.setPrefSize(220, 220);
        qrFrame.setMinSize(220, 220);
        qrFrame.setMaxSize(220, 220);

        ProgressIndicator loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(40, 40);
        qrFrame.getChildren().add(loadingIndicator);

        // ── Build VietQR URL ─────────────────────────────────────────────────────
        String rawInfo = "BTLN4 NAPTIEN " + bidder.getUsername();
        String encodedInfo;
        String encodedName;
        try {
            encodedInfo = java.net.URLEncoder.encode(rawInfo, "UTF-8");
            encodedName = java.net.URLEncoder.encode("HE THONG DAU GIA BTLN4", "UTF-8");
        } catch (Exception e) {
            encodedInfo = rawInfo.replace(" ", "%20");
            encodedName = "HE%20THONG%20DAU%20GIA%20BTLN4";
        }
        String qrUrl = String.format(
                "https://img.vietqr.io/image/MB-0974894480-qr_only.png?amount=%.0f&addInfo=%s&accountName=%s",
                amount, encodedInfo, encodedName);

        ImageView qrImageView = new ImageView();
        qrImageView.setFitWidth(196);
        qrImageView.setFitHeight(196);
        qrImageView.setPreserveRatio(true);

        Task<Image> loadQRTask = new Task<>() {
            @Override
            protected Image call() {
                return ImageLoaderUtil.loadItemImageSync(qrUrl, 196, 196);
            }
        };
        loadQRTask.setOnSucceeded(e -> {
            qrFrame.getChildren().clear();
            qrImageView.setImage(loadQRTask.getValue());
            qrFrame.getChildren().add(qrImageView);
        });
        loadQRTask.setOnFailed(e -> {
            qrFrame.getChildren().clear();
            Label errLabel = new Label("Không thể tải mã QR.\nVui lòng kiểm tra kết nối mạng!");
            errLabel.getStyleClass().add("label-error");
            errLabel.setWrapText(true);
            errLabel.setAlignment(Pos.CENTER);
            qrFrame.getChildren().add(errLabel);
        });
        new Thread(loadQRTask, "vietqr-load").start();

        // ── Details grid ─────────────────────────────────────────────────────────
        // Col 0 (label): fixed ~130px | Col 1 (value): grows to fill remaining
        GridPane detailsGrid = new GridPane();
        detailsGrid.setHgap(12);
        detailsGrid.setVgap(10);
        detailsGrid.setMaxWidth(Double.MAX_VALUE);
        detailsGrid.setStyle("-fx-background-color: -theme-surface;"
                + " -fx-border-color: -theme-border;"
                + " -fx-border-radius: 8; -fx-background-radius: 8;"
                + " -fx-padding: 14;");

        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(120);
        col0.setPrefWidth(130);
        col0.setHgrow(Priority.NEVER);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(0);
        col1.setPrefWidth(200);
        col1.setHgrow(Priority.ALWAYS); // value column stretches to fill
        col1.setFillWidth(true);

        detailsGrid.getColumnConstraints().addAll(col0, col1);

        addDetailRow(detailsGrid, 0, "Ngân hàng:", "MB Bank (Ngân hàng Quân Đội)");
        addDetailRow(detailsGrid, 1, "Số tài khoản:", "0974894480");
        addDetailRow(detailsGrid, 2, "Chủ tài khoản:", "HE THONG DAU GIA BTLN4");
        addDetailRow(detailsGrid, 3, "Số tiền:", String.format("%,.0f ₫", amount));
        addDetailRow(detailsGrid, 4, "Nội dung:", "BTLN4 NAPTIEN " + bidder.getUsername());

        // ── Status + Buttons ─────────────────────────────────────────────────────
        VBox statusBox = new VBox(10);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setMaxWidth(Double.MAX_VALUE);

        Label verificationLabel = new Label("");
        verificationLabel.setMaxWidth(Double.MAX_VALUE);
        verificationLabel.setAlignment(Pos.CENTER);
        verificationLabel.setWrapText(true);
        verificationLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setMaxWidth(Double.MAX_VALUE);

        Button btnCancel = new Button("Hủy giao dịch");
        btnCancel.getStyleClass().add("btn-secondary");
        btnCancel.setMinWidth(130);

        Button btnConfirm = new Button("Xác nhận đã chuyển khoản");
        btnConfirm.getStyleClass().add("btn-success");
        btnConfirm.setMinWidth(180);

        buttonBox.getChildren().addAll(btnCancel, btnConfirm);
        statusBox.getChildren().addAll(verificationLabel, buttonBox);

        btnCancel.setOnAction(ev -> qrStage.close());

        btnConfirm.setOnAction(ev -> {
            btnCancel.setDisable(true);
            btnConfirm.setDisable(true);

            ProgressIndicator verIndicator = new ProgressIndicator();
            verIndicator.setMaxSize(22, 22);

            HBox progressContainer = new HBox(8, verIndicator, verificationLabel);
            progressContainer.setAlignment(Pos.CENTER);
            statusBox.getChildren().clear();
            statusBox.getChildren().add(progressContainer);

            verificationLabel.setText("Đang đối soát giao dịch với ngân hàng...");
            verificationLabel.setStyle("-fx-text-fill: -theme-primary; -fx-font-weight: bold;");

            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1.5), event1 -> {
                Task<Bidder> topupTask = new Task<>() {
                    @Override
                    protected Bidder call() {
                        return app.topupBalance(bidder, amount);
                    }
                };
                topupTask.setOnSucceeded(e -> {
                    Bidder updated = topupTask.getValue();
                    SessionManager.getInstance().setCurrentUser(updated);
                    currentUser = updated;
                    populateProfile();
                    depositField.clear();
                    profileSuccessLabel.setText(
                            String.format("Đã nạp thành công %,.0f ₫ qua QR Code.", amount));
                    qrStage.close();
                });
                topupTask.setOnFailed(e -> {
                    btnCancel.setDisable(false);
                    btnConfirm.setDisable(false);
                    statusBox.getChildren().clear();
                    statusBox.getChildren().addAll(verificationLabel, buttonBox);
                    verificationLabel.setText("Không thể xác nhận giao dịch. Vui lòng thử lại!");
                    verificationLabel.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                });
                new Thread(topupTask, "topup-finalize").start();
            }));
            timeline.play();
        });

        // ── Assemble & wrap in ScrollPane ────────────────────────────────────────
        dialogRoot.getChildren().addAll(titleLabel, subtitleLabel, qrFrame, detailsGrid, statusBox);

        ScrollPane scrollPane = new ScrollPane(dialogRoot);
        scrollPane.setFitToWidth(true); // dialogRoot stretches to scroll width
        scrollPane.setFitToHeight(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("main-container");
        if (NavigationManager.getInstance().isDarkMode()) {
            scrollPane.getStyleClass().add("dark-mode");
        }

        Scene qrScene = new Scene(scrollPane, 460, 610);
        qrScene.getStylesheets().add(
                getClass().getResource("/com/auction/styles/main.css").toExternalForm());
        qrStage.setScene(qrScene);
        qrStage.setResizable(true);
        qrStage.show();
    }

    /**
     * Adds a single key-value row to the details GridPane.
     * The value label has word-wrap enabled and fills the available column width.
     */
    private void addDetailRow(GridPane grid, int row, String labelText, String valueText) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("label-subtle");
        lbl.setStyle("-fx-font-weight: bold;");
        lbl.setMinWidth(120);
        lbl.setWrapText(false);

        Label val = new Label(valueText);
        val.getStyleClass().add("label");
        val.setWrapText(true); // long values wrap instead of being clipped
        val.setMaxWidth(Double.MAX_VALUE); // stretch to fill column 1
        GridPane.setHgrow(val, Priority.ALWAYS);
        GridPane.setFillWidth(val, true);

        if (labelText.contains("Số tiền")) {
            val.setStyle("-fx-text-fill: #16A34A; -fx-font-weight: bold;");
        } else if (labelText.contains("Nội dung")) {
            val.setStyle("-fx-text-fill: -theme-primary; -fx-font-weight: bold;");
        }

        grid.add(lbl, 0, row);
        grid.add(val, 1, row);
    }
}
