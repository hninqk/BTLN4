package com.auction.ui.controller;

import com.auction.ui.support.logic.DefaultProfileStatsService;
import com.auction.ui.support.ui.GuardedNodeUpdater;
import com.auction.ui.support.dto.ProfileStats;
import com.auction.ui.support.logic.ProfileStatsService;
import com.auction.core.model.Auction;
import com.auction.core.model.Bidder;
import com.auction.core.model.Seller;
import com.auction.core.model.User;
import com.auction.core.util.SessionManager;
import com.auction.core.util.TimeSyncManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Modality;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import com.auction.ui.util.ImageLoaderUtil;
import com.auction.ui.util.NavigationManager;

/**
 * UserProfileController – displays and manages user profile data and balance.
 */
public class UserProfileController extends BaseController {

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
    private Label closedAuctionCountLabel;
    @FXML
    private Label totalRevenueLabel;

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

    private User currentUser;
    private final ProfileStatsService statsService = new DefaultProfileStatsService();
    private static final GuardedNodeUpdater NODE_UPDATER = new GuardedNodeUpdater.Default();

    @FXML
    public void initialize() {
        currentUser = SessionManager.getInstance().getCurrentUser();
        DesktopHeaderController.setTitleAndSubtitle("Hồ sơ cá nhân", null);
        profileErrorLabel.setText("");
        profileSuccessLabel.setText("");

        // Apply thousands-separator formatting to the deposit input field
        com.auction.core.util.CurrencyUtil.setupCurrencyTextField(depositField);

        if (currentUser != null) {
            populateProfile();
            // Fetch fresh data from server to ensure balance is up-to-date
            taskRunner.run("profile-refresh", () -> app.findUserById(currentUser.getId()), uOpt -> {
                uOpt.ifPresent(u -> {
                    SessionManager.getInstance().setCurrentUser(u);
                    currentUser = u;
                    populateProfile();
                });
            }, null);
        }
    }

    private void populateProfile() {
        setTextIfChanged(displayNameLabel, currentUser.getUsername());
        setTextIfChanged(roleLabel, currentUser.getRole());
        setTextIfChanged(memberSinceLabel, "Thành viên từ: " + currentUser.getCreatedAt().toLocalDate());

        setVisibleIfChanged(bidderStatsBox, false);
        setManagedIfChanged(bidderStatsBox, false);
        setVisibleIfChanged(sellerStatsBox, false);
        setManagedIfChanged(sellerStatsBox, false);
        setVisibleIfChanged(balanceBox, false);
        setManagedIfChanged(balanceBox, false);
        setVisibleIfChanged(shopNameBox, false);
        setManagedIfChanged(shopNameBox, false);

        if (currentUser instanceof Bidder bidder) {
            setVisibleIfChanged(bidderStatsBox, true);
            setManagedIfChanged(bidderStatsBox, true);
            setVisibleIfChanged(balanceBox, true);
            setManagedIfChanged(balanceBox, true);
            setTextIfChanged(balanceLabel, String.format("%,.0f ₫", bidder.getAccountBalance()));
            loadBidCount(bidder);

        } else if (currentUser instanceof Seller seller) {
            setVisibleIfChanged(sellerStatsBox, true);
            setManagedIfChanged(sellerStatsBox, true);
            setVisibleIfChanged(shopNameBox, true);
            setManagedIfChanged(shopNameBox, true);
            setTextIfChanged(shopNameLabel, seller.getShopName() != null ? seller.getShopName() : "");
            if (shopNameField != null)
                shopNameField.setText(seller.getShopName());
            loadSellerAuctionCount(seller);
            loadSellerStats(seller);
        }
    }

    private static void setTextIfChanged(Labeled node, String text) {
        NODE_UPDATER.setTextIfChanged(node, text);
    }

    private static void setVisibleIfChanged(Node node, boolean visible) {
        NODE_UPDATER.setVisibleIfChanged(node, visible);
    }

    private static void setManagedIfChanged(Node node, boolean managed) {
        NODE_UPDATER.setManagedIfChanged(node, managed);
    }

    /**
     * Warm the bid-count cache for a single bidder only.
     * Called by LoginController after the user authenticates — avoids building
     * a cache for every user in the system on a single-machine deployment.
     */
    public static void preloadCacheForUser(java.util.List<Auction> fullAuctions, String bidderId) {
        new DefaultProfileStatsService().preloadBidCount(fullAuctions, bidderId);
    }

    /** Clear all cached bid counts — call on logout so the next user gets fresh data. */
    public static void clearCache() {
        new DefaultProfileStatsService().clearBidCountCache();
    }

    private void loadBidCount(Bidder bidder) {
        if (totalBidsLabel == null)
            return;

        Long cached = statsService.cachedBidCount(bidder.getId());
        if (cached != null) {
            setTextIfChanged(totalBidsLabel, String.valueOf(cached));
        } else {
            setTextIfChanged(totalBidsLabel, "...");
        }

        taskRunner.run("profile-bid-count", () -> statsService.countBids(app, bidder), count -> {
            setTextIfChanged(totalBidsLabel, String.valueOf(count));
        }, error -> {
            if (statsService.cachedBidCount(bidder.getId()) == null)
                setTextIfChanged(totalBidsLabel, "?");
        });
    }

    private void loadSellerAuctionCount(Seller seller) {
        if (auctionCountLabel == null)
            return;
        setTextIfChanged(auctionCountLabel, "...");
        taskRunner.run("profile-auction-count", () -> statsService.countSellerAuctions(app, seller), count -> {
            setTextIfChanged(auctionCountLabel, String.valueOf(count));
        }, error -> setTextIfChanged(auctionCountLabel, "?"));
    }

    private void loadSellerStats(Seller seller) {
        setTextIfChanged(closedAuctionCountLabel, "...");
        setTextIfChanged(totalRevenueLabel, "...");

        taskRunner.run("profile-seller-stats", () -> statsService.calculateSellerStats(app, seller), stats -> {
            setTextIfChanged(closedAuctionCountLabel, String.valueOf(stats.closedAuctions()));
            setTextIfChanged(totalRevenueLabel, String.format("%,.0f ₫", stats.totalRevenue()));
        }, error -> {
            setTextIfChanged(closedAuctionCountLabel, "?");
            setTextIfChanged(totalRevenueLabel, "?");
        });
    }

    @FXML
    private void handleSaveProfile(ActionEvent event) {
        profileErrorLabel.setText("");
        profileSuccessLabel.setText("Đang lưu...");

        taskRunner.run("save-profile", () -> {
            if (currentUser instanceof Seller seller
                    && shopNameField != null
                    && !shopNameField.getText().trim().isEmpty()) {
                seller.setShopName(shopNameField.getText().trim());
            }
            app.saveUser(currentUser);
            return null;
        }, result -> {
            profileSuccessLabel.setText("Thông tin đã được cập nhật.");
            populateProfile();
        }, error -> {
            profileErrorLabel.setText("Lỗi lưu dữ liệu: " + error.getMessage());
            profileSuccessLabel.setText("");
        });
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
            double amount = com.auction.core.util.CurrencyUtil.parseCurrency(input);
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

        taskRunner.run("vietqr-load", () -> ImageLoaderUtil.loadItemImageSync(qrUrl, 196, 196), image -> {
            qrFrame.getChildren().clear();
            qrImageView.setImage(image);
            qrFrame.getChildren().add(qrImageView);
        }, error -> {
            qrFrame.getChildren().clear();
            Label errLabel = new Label("Không thể tải mã QR.\nVui lòng kiểm tra kết nối mạng!");
            errLabel.getStyleClass().add("label-error");
            errLabel.setWrapText(true);
            errLabel.setAlignment(Pos.CENTER);
            qrFrame.getChildren().add(errLabel);
        });

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
                taskRunner.run("topup-finalize", () -> app.topupBalance(bidder, amount), updated -> {
                    if (updated != null) {
                        SessionManager.getInstance().setCurrentUser(updated);
                        currentUser = updated;
                        populateProfile();
                        depositField.clear();
                        profileSuccessLabel.setText(
                                String.format("Đã nạp thành công %,.0f ₫ qua QR Code.", amount));
                        qrStage.close();
                    } else {
                        btnCancel.setDisable(false);
                        btnConfirm.setDisable(false);
                        statusBox.getChildren().clear();
                        statusBox.getChildren().addAll(verificationLabel, buttonBox);
                        verificationLabel.setText("Không tìm thấy giao dịch. Vui lòng thử lại!");
                        verificationLabel.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                    }
                }, error -> {
                    btnCancel.setDisable(false);
                    btnConfirm.setDisable(false);
                    statusBox.getChildren().clear();
                    statusBox.getChildren().addAll(verificationLabel, buttonBox);
                    verificationLabel.setText("Lỗi hệ thống: " + error.getMessage());
                    verificationLabel.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                });
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
