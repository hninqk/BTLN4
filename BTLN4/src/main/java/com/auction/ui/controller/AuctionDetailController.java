package com.auction.ui.controller;

import com.auction.ui.util.AlertHelper;
import com.auction.ui.util.AuctionChartHelper;
import com.auction.ui.util.ImageLoaderUtil;
import com.auction.ui.util.NavigationManager;
import com.auction.core.util.BidLadderUtil;
import com.auction.core.util.CacheManager;
import com.auction.core.util.CurrencyUtil;
import com.auction.core.util.DataReceiver;
import com.auction.core.util.SessionManager;

import com.auction.ui.support.ui.AuctionDetailUIUpdater;
import com.auction.ui.support.realtime.AuctionDetailWebSocketManager;
import com.auction.ui.support.dto.FeedEntry;
import com.auction.ui.support.logic.LiveFeedEntryParser;
import com.auction.core.model.*;
import com.auction.service.AuctionWebSocketService;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AuctionDetailController extends BaseController
        implements DataReceiver, com.auction.service.AuctionWebSocketService.AuctionWebSocketListener {

    @FXML
    private Label statusBadge;
    @FXML
    private Label nameLabel;
    @FXML
    private Label categoryLabel;
    @FXML
    private Label sellerLabel;
    @FXML
    private Label startPriceLabel;
    @FXML
    private Label startTimeLabel;
    @FXML
    private Label endTimeLabel;
    @FXML
    private Label descriptionLabel;
    @FXML
    private ImageView itemImageView;

    @FXML
    private Label currentPriceLabel;
    @FXML
    private Label bidCountLabel;
    @FXML
    private Label timeRemainingLabel;
    @FXML
    private Label timeLabelPrefix;
    @FXML
    private Label minBidHint;
    @FXML
    private Label stepHintLabel;
    @FXML
    private Label sellerWarningLabel;
    @FXML
    private Label balanceLabel;
    @FXML
    private Label frozenLabel;
    @FXML
    private TextField bidAmountField;
    @FXML
    private Label bidErrorLabel;
    @FXML
    private Button placeBidButton;
    @FXML
    private Button autoBidToggleButton;

    @FXML
    private VBox autoBidPopup;
    @FXML
    private Label autoBidStatusBadge;
    @FXML
    private Label currentAutoBidLabel;
    @FXML
    private TextField autoMaxBidField;
    @FXML
    private Label autoBidErrorLabel;
    @FXML
    private Button registerAutoBidButton;

    @FXML
    private StackPane rootStackPane;
    @FXML
    private ListView<String> liveFeedList;
    @FXML
    private LineChart<Number, Number> priceChart;
    @FXML
    private NumberAxis timeAxis;

    @FXML
    private VBox winnerBox;
    @FXML
    private Label winnerLabel;
    @FXML
    private Label winnerPriceLabel;

    private String auctionId;
    private Auction currentAuction;
    private ScheduledExecutorService scheduler;
    private com.auction.ui.util.AuctionChartHelper chartHelper;
    private volatile boolean wsConnected = false;
    private int knownBidCount = 0;

    private AuctionDetailUIUpdater uiUpdater;
    private AuctionDetailWebSocketManager wsManager;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final LiveFeedEntryParser liveFeedParser = new LiveFeedEntryParser.Default();

    @Override
    public void receiveData(Object data) {
        if (data instanceof Auction a) {
            this.auctionId = a.getId();
            this.currentAuction = a;

            initManagers();
            populateStaticView();
            preloadBidsIntoChartAndFeed();
            refreshAll();

            taskRunner.run("fetch-full-auction", () -> app.findAuctionById(auctionId), fullOpt -> {
                fullOpt.ifPresent(full -> {
                    this.currentAuction = full;
                    initManagers();
                    populateStaticView();
                    // Clear chart/feed before re-populating to avoid duplicates
                    if (chartHelper != null)
                        chartHelper.clear();
                    if (liveFeedList != null)
                        liveFeedList.getItems().clear();
                    preloadBidsIntoChartAndFeed();

                    SessionManager.getInstance().addWsListener(this);
                    AuctionWebSocketService globalWs = SessionManager.getInstance().getGlobalWs();
                    if (globalWs != null && globalWs.isConnected()) {
                        wsConnected = true;
                        requestAutoBidCheck(globalWs);
                    }

                    refreshAll();
                    loadAutoBidState();
                });
            }, error -> {
                System.err.println("[AuctionDetail] Failed to fetch full details: " + error.getMessage());
                SessionManager.getInstance().addWsListener(this);
                wsConnected = SessionManager.getInstance().getGlobalWs() != null
                        && SessionManager.getInstance().getGlobalWs().isConnected();
                refreshControls();
            });

            startTimerScheduler();
        }
    }

    private void requestAutoBidCheck(AuctionWebSocketService ws) {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user instanceof Bidder bidder) {
            JsonObject abReq = new JsonObject();
            abReq.addProperty("type", "CHECK_AUTO_BID");
            abReq.addProperty("auctionId", currentAuction.getId());
            abReq.addProperty("bidderId", bidder.getId());
            ws.send(abReq.toString());
        }
    }

    private void initManagers() {
        uiUpdater = new AuctionDetailUIUpdater(
                timeRemainingLabel, timeLabelPrefix, currentPriceLabel, bidCountLabel, minBidHint, stepHintLabel,
                balanceLabel, frozenLabel, statusBadge, startTimeLabel,
                winnerBox, winnerLabel, winnerPriceLabel,
                bidErrorLabel, placeBidButton, bidAmountField, sellerWarningLabel,
                autoBidToggleButton, autoBidPopup, registerAutoBidButton,
                autoMaxBidField, autoBidErrorLabel);

        wsManager = new AuctionDetailWebSocketManager(
                currentAuction,
                this::appendToFeed,
                chartHelper::addBid,
                this::refreshAll,
                msg -> {
                    if (uiUpdater != null) {
                        uiUpdater.refreshControls(currentAuction, wsConnected, isCurrentUserHighestBidder());
                    }
                    if (bidErrorLabel != null) {
                        bidErrorLabel.setText(msg);
                    }
                },
                this::handleAutoBidWsMessage,
                this::handleWinnerWsMessage);
    }

    private void handleAutoBidWsMessage(JsonObject json) {
        String type = json.get("type").getAsString();
        switch (type) {
            case "AUTO_BID_ACK" -> {
                if (autoBidErrorLabel != null) {
                    autoBidErrorLabel.setText("Đăng ký thành công.");
                    autoBidErrorLabel.setStyle("-fx-text-fill: #81c784;");
                }
                if (autoMaxBidField != null)
                    autoMaxBidField.clear();

                if (autoBidStatusBadge != null) {
                    autoBidStatusBadge.setText("Đang Hoạt Động");
                    autoBidStatusBadge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;");
                    if (registerAutoBidButton != null)
                        registerAutoBidButton.setText("Cập nhật Auto-Bid");
                }
                loadAutoBidState();
            }
            case "AUTO_BID_STATUS" -> {
                double maxBid = json.get("maxBid").getAsDouble();
                if (autoMaxBidField != null)
                    autoMaxBidField.setText(String.format("%.0f", maxBid));
                if (autoBidStatusBadge != null) {
                    autoBidStatusBadge.setText("Đang Hoạt Động");
                    autoBidStatusBadge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;");
                    if (registerAutoBidButton != null)
                        registerAutoBidButton.setText("Cập nhật Auto-Bid");
                }
                if (currentAutoBidLabel != null) {
                    currentAutoBidLabel.setText(String.format("Auto-Bid của bạn: %,.0f ₫", maxBid));
                    currentAutoBidLabel.setVisible(true);
                    currentAutoBidLabel.setManaged(true);
                }
            }
            case "AUTO_BID_DEACTIVATED" -> {
                String bidderId = json.has("bidderId") ? json.get("bidderId").getAsString() : null;
                User user = SessionManager.getInstance().getCurrentUser();
                if (user != null && user.getId().equals(bidderId)) {
                    if (autoBidStatusBadge != null) {
                        autoBidStatusBadge.setText("Đã Dừng");
                        autoBidStatusBadge.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626;");
                    }
                    if (autoBidErrorLabel != null) {
                        autoBidErrorLabel
                                .setText("Auto-Bid đã dừng: Có người đặt giá cao hơn giới hạn tối đa của bạn.");
                        autoBidErrorLabel.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                    }
                    if (currentAutoBidLabel != null) {
                        currentAutoBidLabel.setVisible(false);
                        currentAutoBidLabel.setManaged(false);
                    }
                }
            }
        }
    }

    private void handleWinnerWsMessage(JsonObject json) {
        String winnerUsername = json.has("winnerUsername") ? json.get("winnerUsername").getAsString() : null;
        double winnerBid = json.has("winnerBid") ? json.get("winnerBid").getAsDouble() : -1;
        showWinnerAnnouncement(winnerUsername, winnerBid);
    }

    public void initialize() {
        if (bidErrorLabel != null) {
            bidErrorLabel.setText("");
            bidErrorLabel.managedProperty().bind(bidErrorLabel.visibleProperty());
            bidErrorLabel.visibleProperty().bind(bidErrorLabel.textProperty().isEmpty().not());
        }
        chartHelper = new com.auction.ui.util.AuctionChartHelper(priceChart, timeAxis);
        applyChartWindow();
        setupLiveFeedList();

        com.auction.core.util.CurrencyUtil.setupCurrencyTextField(bidAmountField);
        if (autoMaxBidField != null)
            com.auction.core.util.CurrencyUtil.setupCurrencyTextField(autoMaxBidField);

        if (rootStackPane != null) {
            rootStackPane.addEventFilter(MouseEvent.MOUSE_PRESSED, evt -> {
                if (autoBidPopup != null && autoBidPopup.isVisible()) {
                    if (!autoBidPopup.getBoundsInParent().contains(evt.getX(), evt.getY())) {
                        autoBidPopup.setVisible(false);
                        autoBidPopup.setManaged(false);
                    }
                }
            });
        }
    }

    private void setupLiveFeedList() {
        if (liveFeedList == null)
            return;
        liveFeedList.setCellFactory(list -> new ListCell<>() {
            private HBox row;
            private FontIcon icon;
            private Label time, actor, amount, message;
            {
                icon = new FontIcon();
                icon.setIconSize(14);
                time = new Label();
                time.getStyleClass().add("feed-time");
                actor = new Label();
                actor.getStyleClass().add("feed-actor");
                amount = new Label();
                amount.getStyleClass().add("feed-amount");
                message = new Label();
                message.setWrapText(true);
                VBox textBox = new VBox(3, new HBox(8, time, actor), message);
                HBox.setHgrow(textBox, javafx.scene.layout.Priority.ALWAYS);
                row = new HBox(10, icon, textBox, amount);
                row.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null || entry.isBlank()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                FeedEntry parsed = liveFeedParser.parse(entry);
                boolean autoBid = parsed.amount().isBlank();
                icon.setIconCode(autoBid ? FontAwesomeSolid.BOLT : FontAwesomeSolid.GAVEL);
                icon.getStyleClass().removeAll("feed-icon-auto", "feed-icon-bid");
                icon.getStyleClass().add(autoBid ? "feed-icon-auto" : "feed-icon-bid");
                message.getStyleClass().removeAll("feed-message-auto", "feed-message");
                message.getStyleClass().add(autoBid ? "feed-message-auto" : "feed-message");
                row.getStyleClass().removeAll("feed-entry-auto", "feed-entry");
                row.getStyleClass().add(autoBid ? "feed-entry-auto" : "feed-entry");
                time.setText(parsed.time());
                actor.setText(parsed.actor());
                amount.setText(parsed.amount());
                message.setText(parsed.message());
                amount.setVisible(!autoBid);
                amount.setManaged(!autoBid);
                setGraphic(row);
            }
        });
    }

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown())
            scheduler.shutdown();
        SessionManager.getInstance().removeWsListener(this);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        shutdown();
    }

    @Override
    public void onWsConnected() {
        wsConnected = true;
        wsManager.onWsConnected();
        AuctionWebSocketService globalWs = SessionManager.getInstance().getGlobalWs();
        if (globalWs != null)
            requestAutoBidCheck(globalWs);
    }

    @Override
    public void onWsDisconnected(String error) {
        wsConnected = false;
        wsManager.onWsDisconnected(error);
    }

    @Override
    public void onWsError(String errorMsg) {
        wsManager.onWsError(errorMsg);
    }

    @Override
    public void onBidUpdate(JsonObject json) {
        wsManager.onBidUpdate(json);
    }

    @Override
    public void onAutoBidLog(JsonObject json) {
        wsManager.onAutoBidLog(json);
    }

    @Override
    public void onAutoBidAck(JsonObject json) {
        wsManager.onAutoBidAck(json);
    }

    @Override
    public void onAutoBidStatus(JsonObject json) {
        wsManager.onAutoBidStatus(json);
    }

    @Override
    public void onAutoBidDeactivated(JsonObject json) {
        wsManager.onAutoBidDeactivated(json);
    }

    @Override
    public void onStatusChanged(JsonObject json) {
        wsManager.onStatusChanged(json);
    }

    @Override
    public void onBalanceUpdate(JsonObject json) {
        wsManager.onBalanceUpdate(json);
    }

    @Override
    public void onFullSync(JsonObject json) {
        wsManager.onFullSync(json);
    }

    @Override
    public void onOutbid(JsonObject json) {
        wsManager.onOutbid(json);
    }

    @Override
    public void onLegacyBidUpdate(JsonObject json) {
        wsManager.onLegacyBidUpdate(json);
    }

    private void startTimerScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AuctionDetail-Timer");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> Platform.runLater(this::refreshCountdown), 0, 1, TimeUnit.SECONDS);
    }

    private void populateStaticView() {
        if (currentAuction == null)
            return;
        Item item = currentAuction.getItem();
        nameLabel.setText(item.getName());
        DesktopHeaderController.setTitleAndSubtitle(item.getName(), null);
        categoryLabel.setText(item.getCategory() + " (" + item.getCategoryInfo() + ")");
        String shopName = currentAuction.getSeller().getShopName();
        sellerLabel.setText(
                (shopName != null && !shopName.trim().isEmpty()) ? shopName : currentAuction.getSeller().getUsername());
        startPriceLabel.setText(String.format("%,.0f ₫", item.getStartingPrice()));
        if (currentAuction.getEndTime() != null) {
            endTimeLabel.setText(currentAuction.getEndTime().format(FMT));
        } else {
            endTimeLabel.setText("N/A");
        }
        descriptionLabel.setText(item.getDescription());
        String imgUrl = item.getImageUrl();
        if (imgUrl != null && !imgUrl.isEmpty()) {
            String cacheKey = (imgUrl.startsWith("data:image/") ? Integer.toHexString(imgUrl.hashCode()) : imgUrl)
                    + "_420_250";
            javafx.scene.image.Image cachedImg = com.auction.core.util.CacheManager.getInstance().getImage(cacheKey);
            if (cachedImg != null)
                itemImageView.setImage(cachedImg);
            else
                itemImageView.setImage(ImageLoaderUtil.loadItemImage(imgUrl, 420, 250));
        }
    }

    private void preloadBidsIntoChartAndFeed() {
        if (currentAuction == null)
            return;
        applyChartWindow();
        List<BidTransaction> history = currentAuction.getBidHistory();
        for (BidTransaction bid : history) {
            chartHelper.addBid(bid);
            addBidToFeed(bid);
        }
    }

    private void applyChartWindow() {
        if (chartHelper != null)
            chartHelper.setTimeWindow(null, null, "dd/MM HH:mm", Duration.ofHours(6).toMillis());
    }

    private void loadAutoBidState() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user instanceof Bidder bidder && currentAuction != null) {
            currentAuction.getAutoBids().stream().filter(ab -> ab.getBidderId().equals(bidder.getId())).findFirst()
                    .ifPresent(myAutoBid -> {
                        Platform.runLater(() -> {
                            if (autoMaxBidField != null)
                                autoMaxBidField.setText(String.format("%.0f", myAutoBid.getMaxBid()));
                            if (autoBidStatusBadge != null) {
                                autoBidStatusBadge.setText("Đang Hoạt Động");
                                autoBidStatusBadge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;");
                                if (registerAutoBidButton != null)
                                    registerAutoBidButton.setText("Cập nhật Auto-Bid");
                            }
                            if (currentAutoBidLabel != null) {
                                currentAutoBidLabel
                                        .setText(String.format("Auto-Bid của bạn: %,.0f ₫", myAutoBid.getMaxBid()));
                                currentAutoBidLabel.setVisible(true);
                                currentAutoBidLabel.setManaged(true);
                            }
                        });
                    });
        }
    }

    private void refreshCountdown() {
        if (uiUpdater != null)
            uiUpdater.refreshCountdown(currentAuction);
        if (currentAuction != null && (currentAuction.getStatus() == AuctionStatus.CLOSED
                || currentAuction.getStatus() == AuctionStatus.CANCELED)) {
            if (scheduler != null && !scheduler.isShutdown())
                scheduler.shutdown();
        }
    }

    private void refreshBidSection() {
        if (uiUpdater != null)
            uiUpdater.refreshBidSection(currentAuction);
        if (currentAuction != null) {
            List<BidTransaction> history = currentAuction.getBidHistory();
            if (history != null && history.size() > knownBidCount)
                knownBidCount = history.size();
        }
    }

    private void refreshBalanceSection() {
        if (uiUpdater != null)
            uiUpdater.refreshBalanceSection();
    }

    private void refreshStatusSection() {
        if (uiUpdater != null)
            uiUpdater.refreshStatusSection(currentAuction);
    }

    private void refreshWinnerSection() {
        if (uiUpdater != null)
            uiUpdater.refreshWinnerSection(currentAuction);
    }

    private void refreshControls() {
        if (uiUpdater != null)
            uiUpdater.refreshControls(currentAuction, wsConnected, isCurrentUserHighestBidder());
    }

    private boolean isCurrentUserHighestBidder() {
        if (currentAuction == null)
            return false;
        User user = SessionManager.getInstance().getCurrentUser();
        BidTransaction winner = currentAuction.getWinner();
        return user != null && winner != null && winner.getBidder() != null
                && user.getId().equals(winner.getBidder().getId());
    }

    private void refreshAll() {
        if (currentAuction == null)
            return;
        refreshCountdown();
        refreshBidSection();
        refreshBalanceSection();
        refreshStatusSection();
        refreshWinnerSection();
        refreshControls();
    }

    private void addBidToFeed(BidTransaction bid) {
        appendToFeed(String.format("[%s]  %s  →  %,.0f ₫", bid.getTimestamp().format(TIME_FMT),
                bid.getBidder().getUsername(), bid.getAmount()));
    }

    private void appendToFeed(String entry) {
        ObservableList<String> items = liveFeedList.getItems();
        if (!items.isEmpty() && items.get(0).equals(entry))
            return;
        items.add(0, entry);
        if (items.size() > 50)
            items.remove(50, items.size());
    }

    @FXML
    private void handlePlaceBid(ActionEvent event) {
        if (bidErrorLabel != null)
            bidErrorLabel.setText("");
        String input = bidAmountField.getText().trim();
        if (input.isEmpty()) {
            if (bidErrorLabel != null)
                bidErrorLabel.setText("Vui lòng nhập số tiền đặt giá.");
            return;
        }
        double amount;
        try {
            amount = com.auction.core.util.CurrencyUtil.parseCurrency(input);
        } catch (NumberFormatException e) {
            if (bidErrorLabel != null)
                bidErrorLabel.setText("Số tiền không hợp lệ.");
            return;
        }
        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Bidder bidder)) {
            if (bidErrorLabel != null)
                bidErrorLabel.setText("Chỉ Bidder mới có thể đặt giá.");
            return;
        }
        if (isCurrentUserHighestBidder()) {
            if (bidErrorLabel != null) {
                bidErrorLabel.setText("Bạn đang là người ra giá cao nhất.");
                bidErrorLabel.setStyle("-fx-text-fill: #64b5f6;");
            }
            refreshControls();
            return;
        }
        AuctionWebSocketService globalWs = SessionManager.getInstance().getGlobalWs();
        if (!wsConnected || globalWs == null) {
            if (bidErrorLabel != null)
                bidErrorLabel.setText("Không thể kết nối server. Vui lòng chờ server hoạt động.");
            return;
        }
        JsonObject req = new JsonObject();
        req.addProperty("type", "PLACE_BID");
        req.addProperty("auctionId", currentAuction.getId());
        req.addProperty("bidderId", bidder.getId());
        req.addProperty("bidderUsername", bidder.getUsername());
        req.addProperty("bidderBalance", bidder.getAccountBalance());
        req.addProperty("amount", amount);
        globalWs.send(req.toString());
        bidAmountField.clear();
        placeBidButton.setDisable(true);
        bidAmountField.setDisable(true);
        if (bidErrorLabel != null) {
            bidErrorLabel.setText("Đang gửi giá đặt đến server...");
            bidErrorLabel.setStyle("-fx-text-fill: #64b5f6;");
        }
    }

    @FXML
    private void handleToggleAutoBid(ActionEvent event) {
        if (autoBidPopup == null)
            return;
        boolean nowVisible = !autoBidPopup.isVisible();
        autoBidPopup.setVisible(nowVisible);
        autoBidPopup.setManaged(nowVisible);
        if (nowVisible) {
            autoBidPopup.setTranslateX(0);
            autoBidPopup.setTranslateY(0);
            StackPane.setAlignment(autoBidPopup, Pos.CENTER);
            User user = SessionManager.getInstance().getCurrentUser();
            AuctionWebSocketService globalWs = SessionManager.getInstance().getGlobalWs();
            if (user instanceof Bidder bidder && wsConnected && globalWs != null) {
                JsonObject req = new JsonObject();
                req.addProperty("type", "CHECK_AUTO_BID");
                req.addProperty("auctionId", currentAuction.getId());
                req.addProperty("bidderId", bidder.getId());
                globalWs.send(req.toString());
            }
        }
    }

    @FXML
    private void handleCloseAutoBidPopup(ActionEvent event) {
        if (autoBidPopup != null) {
            autoBidPopup.setVisible(false);
            autoBidPopup.setManaged(false);
        }
    }

    @FXML
    private void handleRegisterAutoBid(ActionEvent event) {
        if (autoBidErrorLabel == null)
            return;
        autoBidErrorLabel.setText("");
        String maxBidInput = autoMaxBidField.getText().trim();
        if (maxBidInput.isEmpty()) {
            autoBidErrorLabel.setText("Vui lòng nhập giá tối đa.");
            return;
        }
        double maxBid;
        try {
            maxBid = com.auction.core.util.CurrencyUtil.parseCurrency(maxBidInput);
        } catch (NumberFormatException e) {
            autoBidErrorLabel.setText("Số tiền không hợp lệ.");
            return;
        }
        if (currentAuction != null && currentAuction.getItem() != null) {
            double minRequired = currentAuction.getItem().getStartingPrice();
            if (maxBid < minRequired) {
                autoBidErrorLabel.setText(
                        String.format("Giá Auto-Bid phải lớn hơn hoặc bằng giá tối thiểu (%,.0f ₫).", minRequired));
                autoBidErrorLabel.setStyle("-fx-text-fill: #EF4444;");
                return;
            }
        }
        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Bidder bidder)) {
            autoBidErrorLabel.setText("Chỉ Bidder mới có thể đăng ký Auto-Bid.");
            return;
        }
        AuctionWebSocketService globalWs = SessionManager.getInstance().getGlobalWs();
        if (!wsConnected || globalWs == null) {
            autoBidErrorLabel.setText("Không thể kết nối server. Vui lòng chờ.");
            autoBidErrorLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        JsonObject req = new JsonObject();
        req.addProperty("type", "REGISTER_AUTO_BID");
        req.addProperty("auctionId", currentAuction.getId());
        req.addProperty("bidderId", bidder.getId());
        req.addProperty("maxBid", maxBid);
        globalWs.send(req.toString());
        registerAutoBidButton.setDisable(true);
        autoMaxBidField.setDisable(true);
        autoBidErrorLabel.setText("Đang gửi yêu cầu...");
        autoBidErrorLabel.setStyle("-fx-text-fill: #64b5f6;");
    }

    @FXML
    private void handleBack(ActionEvent event) {
        shutdown();
        try {
            nav.navigateTo(NavigationManager.DASHBOARD, "Tổng quan", null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showWinnerAnnouncement(String winnerUsername, double winnerBid) {

    }
}
