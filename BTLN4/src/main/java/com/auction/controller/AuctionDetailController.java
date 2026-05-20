package com.auction.controller;

import com.auction.client.AuctionClient;
import com.auction.model.*;
import com.auction.util.DataReceiver;
import com.auction.util.HotItemCache;
import com.auction.util.ImageLoaderUtil;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * AuctionDetailController – merged live-bidding view.
 *
 * Real-time updates via WebSocket:
 *  • BID_UPDATE            → update price, feed, chart, balance (if winner notified)
 *  • AUCTION_STATUS_CHANGED→ update status badge, enable/disable bid button
 *  • BALANCE_UPDATE        → update current user's balance in session & UI
 *  • FULL_SYNC             → reload entire auction state from server snapshot
 *
 * Seller creates auctions → SellerManagementController sends CREATE_AUCTION via WS.
 * Admin actions          → AdminManagementController sends ADMIN_ACTION via WS.
 */
public class AuctionDetailController implements DataReceiver {

    // ── Info panel ────────────────────────────────────────────────────────────
    @FXML private Label     itemNameLabel;
    @FXML private Label     auctionIdLabel;
    @FXML private Label     statusBadge;
    @FXML private Label     nameLabel;
    @FXML private Label     categoryLabel;
    @FXML private Label     sellerLabel;
    @FXML private Label     startPriceLabel;
    @FXML private Label     startTimeLabel;
    @FXML private Label     endTimeLabel;
    @FXML private Label     descriptionLabel;
    @FXML private ImageView itemImageView;

    // ── Live bid panel ────────────────────────────────────────────────────────
    @FXML private Label     currentPriceLabel;
    @FXML private Label     bidCountLabel;
    @FXML private Label     timeRemainingLabel;
    @FXML private Label     lastUpdateLabel;
    @FXML private Label     minBidHint;
    @FXML private Label     sellerWarningLabel;
    @FXML private Label     balanceLabel;
    @FXML private Label     frozenLabel;
    @FXML private TextField bidAmountField;
    @FXML private Label     bidErrorLabel;
    @FXML private Button    placeBidButton;
    @FXML private ScrollPane mainScrollPane;

    // ── Auto-Bid panel ────────────────────────────────────────────────────────
    @FXML private VBox      autoBidDashboard;
    @FXML private Label     autoBidStatusBadge;
    @FXML private TextField autoMaxBidField;
    @FXML private TextField autoIncrementField;
    @FXML private Label     autoBidErrorLabel;
    @FXML private Button    registerAutoBidButton;

    // ── Live feed ─────────────────────────────────────────────────────────────
    @FXML private ListView<String> liveFeedList;

    // ── Price chart ───────────────────────────────────────────────────────────
    @FXML private LineChart<String, Number> priceChart;

    // ── Winner box ────────────────────────────────────────────────────────────
    @FXML private VBox  winnerBox;
    @FXML private Label winnerLabel;
    @FXML private Label winnerPriceLabel;


    // ── Internal state ────────────────────────────────────────────────────────
    private String  auctionId;
    private Auction currentAuction;
    private ScheduledExecutorService scheduler;
    private XYChart.Series<String, Number> priceSeries;
    private int chartTick     = 0;
    private int knownBidCount = 0;
    /** Debounce: timestamp of last full refreshLivePanel render (ms). */
    private volatile long lastRefreshMs = 0;

    // WebSocket
    private AuctionClient wsClient;
    private volatile boolean wsConnected = false;
    private final Gson gson = new Gson();

    // In-memory bid list (synced from server via WS)
    private final List<BidTransaction> wsKnownBids = new ArrayList<>();

    private static final DateTimeFormatter FMT      = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_SEC  = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ──────────────────────────────────────────────────────────────────────────
    // DataReceiver
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void receiveData(Object data) {
        if (data instanceof Auction a) {
            this.auctionId      = a.getId();
            this.currentAuction = a;

            // 1. Show basic info immediately
            populateStaticView();
            refreshLivePanel();

            // 2. Fetch full details (including bids) in background
            javafx.concurrent.Task<java.util.Optional<Auction>> task = new javafx.concurrent.Task<>() {
                @Override protected java.util.Optional<Auction> call() {
                    return com.auction.service.AppFacade.getInstance().findAuctionById(auctionId);
                }
            };

            task.setOnSucceeded(e -> {
                task.getValue().ifPresent(full -> {
                    this.currentAuction = full;
                    populateStaticView();
                    preloadBidsIntoChartAndFeed();
                    refreshLivePanel();
                    loadAutoBidState();
                    connectWebSocket(); // Only connect WS after we have baseline bids
                });
            });

            task.setOnFailed(e -> {
                System.err.println("[AuctionDetail] Failed to fetch full details: " + task.getException().getMessage());
                connectWebSocket(); // Fallback: connect anyway
            });

            new Thread(task, "fetch-full-auction").start();
            startTimerScheduler(); 
        }
    }

    @FXML
    public void initialize() {
        bidErrorLabel.setText("");
        setupChart();

        UnaryOperator<TextFormatter.Change> numericFilter = change ->
                change.getControlNewText().matches("[0-9,.]*") ? change : null;
        bidAmountField.setTextFormatter(new TextFormatter<>(numericFilter));
        
        if (autoMaxBidField != null) autoMaxBidField.setTextFormatter(new TextFormatter<>(numericFilter));
        if (autoIncrementField != null) autoIncrementField.setTextFormatter(new TextFormatter<>(numericFilter));
    }

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) scheduler.shutdown();
        if (wsClient  != null) wsClient.disconnect();
    }

    /** Called by NavigationManager when leaving this screen. */
    public void cleanup() { shutdown(); }

    // ──────────────────────────────────────────────────────────────────────────
    // WebSocket
    // ──────────────────────────────────────────────────────────────────────────

    private void connectWebSocket() {
        wsClient = new AuctionClient();
        Thread t = new Thread(() -> {
            wsClient.connect(
                    msg -> Platform.runLater(() -> handleWsMessage(msg)),
                    err -> Platform.runLater(() -> {
                        wsConnected = false;
                        placeBidButton.setDisable(true);
                        bidAmountField.setDisable(true);
                        bidErrorLabel.setText("🔴 Mất kết nối server – không thể đặt giá.");
                        refreshLivePanel();
                    }),
                    // onOpen: request full sync from server
                    () -> {
                        wsConnected = true;
                        sendRequestSync();
                        Platform.runLater(this::refreshLivePanel);
                    }
            );
        }, "AuctionDetail-WS");
        t.setDaemon(true);
        t.start();
    }

    /** Send REQUEST_SYNC to get up-to-date auction list from server. */
    private void sendRequestSync() {
        if (wsClient == null || !wsClient.isConnected()) return;
        JsonObject req = new JsonObject();
        req.addProperty("type", "REQUEST_SYNC");
        wsClient.send(req.toString());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WS Message Handling
    // ──────────────────────────────────────────────────────────────────────────

    private void handleWsMessage(String msg) {
        try {
            com.google.gson.JsonElement element = gson.fromJson(msg, com.google.gson.JsonElement.class);
            if (!element.isJsonObject()) return;
            JsonObject json = element.getAsJsonObject();

            if (json.has("error")) {
                String errMsg = "⚠ " + json.get("error").getAsString();
                if (autoBidErrorLabel != null && !autoBidErrorLabel.getText().isEmpty()) {
                    autoBidErrorLabel.setText(errMsg);
                    autoBidErrorLabel.setStyle("-fx-text-fill: red;");
                    if (registerAutoBidButton != null) registerAutoBidButton.setDisable(false);
                    if (autoMaxBidField != null) autoMaxBidField.setDisable(false);
                    if (autoIncrementField != null) autoIncrementField.setDisable(false);
                }
                bidErrorLabel.setText(errMsg);
                bidErrorLabel.setStyle("-fx-text-fill: red;");
                placeBidButton.setDisable(false);
                bidAmountField.setDisable(false);
                if (mainScrollPane != null) mainScrollPane.setVvalue(0.0);
                return;
            }

            String type = json.has("type") ? json.get("type").getAsString() : "";

            switch (type) {
                case "BID_UPDATE"             -> onBidUpdate(json);
                case "AUCTION_STATUS_CHANGED" -> onStatusChanged(json);
                case "BALANCE_UPDATE"         -> onBalanceUpdate(json);
                case "FULL_SYNC"              -> onFullSync(json);
                case "AUTO_BID_LOG"           -> onAutoBidLog(json);
                case "AUTO_BID_ACK"           -> onAutoBidAck(json);
                // Legacy: bare bid response without "type" field
                default -> {
                    if (json.has("amount") && json.has("bidder")) {
                        onLegacyBidUpdate(json);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[AuctionDetail] WS parse error: " + e.getMessage());
        }
    }

    /** New bid placed on server – update price, feed, chart. */
    private void onBidUpdate(JsonObject json) {
        String aid           = json.has("auctionId")      ? json.get("auctionId").getAsString()      : null;
        double amount        = json.get("amount").getAsDouble();
        String bidderName    = json.get("bidderUsername").getAsString();
        String bidderId      = json.has("bidderId")       ? json.get("bidderId").getAsString()       : "remote";
        String timeStr       = json.has("time")           ? json.get("time").getAsString()           : LocalDateTime.now().toString();

        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;

        // O(1) cache update – runs on FX thread via Platform.runLater, which is fine
        // because ConcurrentHashMap + AtomicInteger are thread-safe for the increment
        if (aid != null) HotItemCache.getInstance().recordBid(aid);
        else if (currentAuction != null) HotItemCache.getInstance().recordBid(currentAuction.getId());

        LocalDateTime ts = LocalDateTime.parse(timeStr);
        if (com.auction.util.ServerConfig.isRemote()) {
            ts = ts.plusHours(7); // Convert UTC (Render) to Vietnam time (GMT+7)
        }

        if (currentAuction != null) {
            currentAuction.setHighestBid(amount);
            // Build a real bid entry for the UI
            Bidder dummy = new Bidder(bidderId, LocalDateTime.now(), bidderName, "", 0);
            BidTransaction dummyBid = new BidTransaction(
                    java.util.UUID.randomUUID().toString(),
                    ts,
                    dummy, currentAuction, amount);
            currentAuction.injectBid(dummyBid);
        }

        // Add to feed & chart immediately (don't wait for scheduler)
        String timeDisplay = ts.format(TIME_FMT);
        appendToFeed(String.format("[%s]  %s  →  %,.0f ₫", timeDisplay, bidderName, amount));
        addRawToChart(amount);

        bidErrorLabel.setText("");
        refreshLivePanel();
    }

    private void onAutoBidLog(JsonObject json) {
        String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;
        
        String msg = json.get("message").getAsString();
        String timeDisplay = LocalDateTime.now().format(TIME_FMT);
        appendToFeed(String.format("[%s] ⚡ %s", timeDisplay, msg));
    }
    
    private void onAutoBidAck(JsonObject json) {
        String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;
        
        if (autoBidErrorLabel != null) {
            autoBidErrorLabel.setText("✅ Đăng ký thành công.");
            autoBidErrorLabel.setStyle("-fx-text-fill: #81c784;");
        }
        if (autoMaxBidField != null) autoMaxBidField.clear();
        if (autoIncrementField != null) autoIncrementField.clear();
        
        if (autoBidStatusBadge != null) {
            autoBidStatusBadge.setText("Đang Hoạt Động");
            autoBidStatusBadge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;");
            registerAutoBidButton.setText("Cập nhật Auto-Bid");
        }
        
        refreshLivePanel();
    }

    /** Auction status changed by admin (approve/start/finish/cancel). */
    private void onStatusChanged(JsonObject json) {
        String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;

        String newStatusStr = json.get("newStatus").getAsString();
        double highestBid   = json.has("highestBid") ? json.get("highestBid").getAsDouble() : -1;
        String startTimeStr = json.has("startTime")  ? json.get("startTime").getAsString()  : "";

        AuctionStatus previousStatus = currentAuction != null ? currentAuction.getStatus() : null;

        if (currentAuction != null) {
            // Patch in-memory object directly from WS data.
            // DO NOT reload from local DB — local DB is stale on remote machines.
            try {
                AuctionStatus newStatus = AuctionStatus.valueOf(newStatusStr);
                currentAuction.setStatus(newStatus);
            } catch (IllegalArgumentException ignored) {}

            if (highestBid >= 0) currentAuction.setHighestBid(highestBid);

            if (!startTimeStr.isEmpty()) {
                try { currentAuction.setStartTime(LocalDateTime.parse(startTimeStr)); }
                catch (Exception ignored) {}
            }
        }

        refreshLivePanel();
        System.out.println("[AuctionDetail] Status → " + newStatusStr);

        // Hiển thị thông báo người chiến thắng khi phiên CLOSED
        if ("CLOSED".equals(newStatusStr) && !AuctionStatus.CLOSED.equals(previousStatus)) {
            String winnerUsername = json.has("winnerUsername") ? json.get("winnerUsername").getAsString() : null;
            double winnerBid      = json.has("winnerBid")      ? json.get("winnerBid").getAsDouble()      : -1;
            showWinnerAnnouncement(winnerUsername, winnerBid);
        }
    }

    /** Server thông báo số dư của bidder đã thay đổi (sau khi bid hoặc auction finish). */
    private void onBalanceUpdate(JsonObject json) {
        String bidderId  = json.get("bidderId").getAsString();
        double newBalance = json.get("newBalance").getAsDouble();
        double frozen    = json.has("frozenBalance")    ? json.get("frozenBalance").getAsDouble()    : -1;

        User me = SessionManager.getInstance().getCurrentUser();
        if (me instanceof Bidder myBidder && myBidder.getId().equals(bidderId)) {
            myBidder.setAccountBalance(newBalance);
            if (frozen >= 0) myBidder.setFrozenBalance(frozen);
            SessionManager.getInstance().setCurrentUser(myBidder);
            System.out.printf("[AuctionDetail] Balance updated: total=%.0f ₫ frozen=%.0f ₫ available=%.0f ₫%n",
                    newBalance, myBidder.getFrozenBalance(), myBidder.getAvailableBalance());
            refreshLivePanel(); // re-render balanceLabel
        }
    }

    /** Full state snapshot sent by server on REQUEST_SYNC. */
    private void onFullSync(JsonObject json) {
        if (!json.has("auctions") || currentAuction == null) return;
        JsonArray auctions = json.get("auctions").getAsJsonArray();
        for (int i = 0; i < auctions.size(); i++) {
            JsonObject a = auctions.get(i).getAsJsonObject();
            String aid = a.get("auctionId").getAsString();
            if (aid.equals(currentAuction.getId())) {
                applyAuctionSnapshot(a);
                return;
            }
        }
    }

    /** Apply a server auction snapshot to our in-memory model. */
    private void applyAuctionSnapshot(JsonObject snap) {
        if (currentAuction == null) return;
        double highestBid = snap.get("highestBid").getAsDouble();
        currentAuction.setHighestBid(highestBid);

        // Apply any bid history from server that we don't have locally
        if (snap.has("bidHistory")) {
            JsonArray bids = snap.get("bidHistory").getAsJsonArray();
            List<String> existingIds = currentAuction.getBidHistory()
                    .stream().map(BidTransaction::getId).toList();
            for (int i = 0; i < bids.size(); i++) {
                JsonObject b = bids.get(i).getAsJsonObject();
                String bidId = b.get("bidId").getAsString();
                if (!existingIds.contains(bidId)) {
                    double amt   = b.get("amount").getAsDouble();
                    String bName = b.get("bidderUsername").getAsString();
                    String bId   = b.get("bidderId").getAsString();
                    LocalDateTime ts = LocalDateTime.parse(b.get("time").getAsString());
                    Bidder dummy = new Bidder(bId, ts, bName, "", 0);
                    currentAuction.injectBid(new BidTransaction(bidId, ts, dummy, currentAuction, amt));
                }
            }
        }

        refreshLivePanel();
        // Rebuild chart/feed fully after sync
        rebuildChartAndFeed();
    }

    /** Rebuild chart and feed from current bid history (called after FULL_SYNC). */
    private void rebuildChartAndFeed() {
        priceSeries.getData().clear();
        liveFeedList.getItems().clear();
        chartTick     = 0;
        knownBidCount = 0;
        List<BidTransaction> history = currentAuction.getBidHistory();
        for (BidTransaction bt : history) {
            addBidToChart(bt);
            addBidToFeed(bt);
        }
        knownBidCount = history.size();
    }

    /** Legacy: server sent bare {amount, bidder, time} without type field. */
    private void onLegacyBidUpdate(JsonObject json) {
        String aid        = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        double amount     = json.get("amount").getAsDouble();
        String bidderName = json.get("bidder").getAsString();
        String timeStr    = json.has("time") ? json.get("time").getAsString() : LocalDateTime.now().toString();

        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;
        if (currentAuction != null) {
            currentAuction.setHighestBid(amount);
            Bidder dummy = new Bidder("remote", LocalDateTime.now(), bidderName, "", 0);
            BidTransaction dummyBid = new BidTransaction(
                    java.util.UUID.randomUUID().toString(),
                    LocalDateTime.now(), dummy, currentAuction, amount);
            currentAuction.injectBid(dummyBid);
        }
        refreshLivePanel();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Timer-only scheduler (no DB polling – WS is the data source)
    // ──────────────────────────────────────────────────────────────────────────

    private void startTimerScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AuctionDetail-Timer");
            t.setDaemon(true);
            return t;
        });
        // Every second: update countdown timer and status badge only
        scheduler.scheduleAtFixedRate(
                () -> Platform.runLater(this::refreshLivePanel), 0, 1, TimeUnit.SECONDS);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Setup
    // ──────────────────────────────────────────────────────────────────────────


    private void setupChart() {
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Giá đấu");
        priceChart.getData().add(priceSeries);
        priceChart.setCreateSymbols(true);
        priceChart.setAnimated(false);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Static info (once on load)
    // ──────────────────────────────────────────────────────────────────────────

    private void populateStaticView() {
        if (currentAuction == null) return;
        Item item = currentAuction.getItem();
        itemNameLabel.setText(item.getName());
        auctionIdLabel.setText("ID: " + currentAuction.getId());
        nameLabel.setText(item.getName());
        categoryLabel.setText(item.getCategory());
        String shopName = currentAuction.getSeller().getShopName();
        sellerLabel.setText((shopName != null && !shopName.trim().isEmpty()) ? shopName : currentAuction.getSeller().getUsername());
        startPriceLabel.setText(String.format("%,.0f ₫", item.getStartingPrice()));
        endTimeLabel.setText(currentAuction.getEndTime().format(FMT));
        descriptionLabel.setText(item.getDescription());
        // Check image cache first (splash preloads 420×250) – set immediately, no thread needed
        String imgUrl = item.getImageUrl();
        if (imgUrl != null && !imgUrl.isEmpty()) {
            String cacheKey = (imgUrl.startsWith("data:image/") && imgUrl.contains(";base64,")
                    ? Integer.toHexString(imgUrl.hashCode()) : imgUrl) + "_420_250";
            javafx.scene.image.Image cachedImg = com.auction.util.CacheManager.getInstance().getImage(cacheKey);
            if (cachedImg != null) {
                itemImageView.setImage(cachedImg);
            } else {
                itemImageView.setImage(ImageLoaderUtil.loadItemImage(imgUrl, 420, 250));
            }
        }
    }

    private void preloadBidsIntoChartAndFeed() {
        if (currentAuction == null) return;
        List<BidTransaction> history = currentAuction.getBidHistory();
        System.out.println("[AuctionDetail] REST data: " + history.size() + " bids for auction " + currentAuction.getId());
        for (BidTransaction bid : history) {
            addBidToChart(bid);
            addBidToFeed(bid);
        }
        knownBidCount = history.size();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Live panel (updated every second and on each WS event)
    // ──────────────────────────────────────────────────────────────────────────

    private void loadAutoBidState() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user instanceof Bidder bidder && currentAuction != null) {
            AutoBid activeBid = new com.auction.repository.JdbcAutoBidRepository()
                    .findByAuctionIdAndBidderId(currentAuction.getId(), bidder.getId());
            if (activeBid != null) {
                Platform.runLater(() -> {
                    if (autoMaxBidField != null) autoMaxBidField.setText(String.format("%.0f", activeBid.getMaxBid()));
                    if (autoIncrementField != null) autoIncrementField.setText(String.format("%.0f", activeBid.getIncrement()));
                    if (autoBidStatusBadge != null) {
                        autoBidStatusBadge.setText("Đang Hoạt Động");
                        autoBidStatusBadge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;");
                        if (registerAutoBidButton != null) registerAutoBidButton.setText("Cập nhật Auto-Bid");
                    }
                });
            }
        }
    }

    private void refreshLivePanel() {
        if (currentAuction == null) return;
        // Debounce: skip if called again within 150 ms (WS + scheduler fire at the same time)
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs < 150) return;
        lastRefreshMs = now;

        // Price + bid count
        currentPriceLabel.setText(String.format("%,.0f ₫", currentAuction.getHighestBid()));
        bidCountLabel.setText(currentAuction.getBidHistory().size() + " lượt đấu giá");
        minBidHint.setText("Tối thiểu: " + String.format("%,.0f ₫", currentAuction.getHighestBid() + 1));
        lastUpdateLabel.setText("Đồng bộ : " + LocalDateTime.now().format(TIME_FMT));

        // Status badge
        updateStatusBadge();

        // Start time
        LocalDateTime st = currentAuction.getStartTime();
        startTimeLabel.setText(st != null ? st.format(FMT) : "Chưa bắt đầu");

        // Countdown timer
        AuctionStatus status = currentAuction.getStatus();
        switch (status) {
            case PENDING  -> timeRemainingLabel.setText("⏳ Chờ Admin duyệt");
            case OPEN     -> timeRemainingLabel.setText("🟢 Chờ Admin bắt đầu");
            case CLOSED, CANCELED -> {
                timeRemainingLabel.setText("Đã kết thúc");
                if (scheduler != null && !scheduler.isShutdown()) scheduler.shutdown();
            }
            case RUNNING -> {
                Duration remaining = Duration.between(LocalDateTime.now(), currentAuction.getEndTime());
                timeRemainingLabel.setText(remaining.isNegative() ? "Hết giờ" :
                        String.format("%02d:%02d:%02d",
                                remaining.toHours(), remaining.toMinutesPart(), remaining.toSecondsPart()));
            }
        }

        // New bids → chart + feed (from injected WS bids not yet rendered)
        List<BidTransaction> history = currentAuction.getBidHistory();
        if (history.size() > knownBidCount) {
            for (int i = knownBidCount; i < history.size(); i++) {
                // Only add to chart/feed if not already added via onBidUpdate()
                // onBidUpdate adds to chart immediately; scheduler skips those
            }
            knownBidCount = history.size();
        }

        // Winner box
        if (status == AuctionStatus.CLOSED) {
            BidTransaction winner = currentAuction.getWinner();
            if (winner != null) {
                winnerBox.setVisible(true); winnerBox.setManaged(true);
                winnerLabel.setText("Người thắng: " + winner.getBidder().getUsername());
                winnerPriceLabel.setText(String.format("Giá chốt: %,.0f ₫", winner.getAmount()));
            }
        } else {
            winnerBox.setVisible(false); winnerBox.setManaged(false);
        }

        // Bid button & balance
        User user = SessionManager.getInstance().getCurrentUser();
        boolean isExpired = currentAuction.getEndTime() != null && LocalDateTime.now().isAfter(currentAuction.getEndTime());
        boolean canBid = status == AuctionStatus.RUNNING && !isExpired && user instanceof Bidder && wsConnected;
        
        if (sellerWarningLabel != null) {
            boolean isSeller = user instanceof Seller;
            sellerWarningLabel.setVisible(isSeller);
            sellerWarningLabel.setManaged(isSeller);
        }

        // Chặn đặt giá liên tiếp nếu đang giữ giá cao nhất
        boolean isHighestBidder = false;
        if (canBid && user != null) {
            BidTransaction highestBid = currentAuction.getWinner();
            if (highestBid != null && highestBid.getBidder().getUsername().equals(user.getUsername())) {
                isHighestBidder = true;
                canBid = false; // Vô hiệu hóa đặt giá
            }
        }
        
        boolean canAutoBid = status == AuctionStatus.RUNNING && !isExpired && user instanceof Bidder && wsConnected;

        placeBidButton.setDisable(!canBid);
        bidAmountField.setDisable(!canBid);
        
        if (autoBidDashboard != null) {
            autoBidDashboard.setVisible(canAutoBid);
            autoBidDashboard.setManaged(canAutoBid);
        }
        
        if (registerAutoBidButton != null) {
            registerAutoBidButton.setDisable(!canAutoBid);
            autoMaxBidField.setDisable(!canAutoBid);
            autoIncrementField.setDisable(!canAutoBid);
            
            if (autoBidErrorLabel != null) {
                String abCur = autoBidErrorLabel.getText();
                if (abCur.contains("Đang gửi")) {
                    autoBidErrorLabel.setText("");
                    autoBidErrorLabel.setStyle("");
                }
            }
        }

        if (isHighestBidder) {
            bidErrorLabel.setText("🏆 Bạn đang giữ giá cao nhất. Chờ người khác đặt giá cao hơn.");
            bidErrorLabel.setStyle("-fx-text-fill: #e5a93c; -fx-font-weight: bold;");
        } else {
            // Reset về default nếu đang hiển thị thông báo highest-bidder hoặc pending
            String cur = bidErrorLabel.getText();
            if (cur.contains("giữ giá cao nhất") || cur.contains("Đang gửi")) {
                bidErrorLabel.setText("");
                bidErrorLabel.setStyle(""); // Reset màu về mặc định
            }
        }
        if (balanceLabel != null) {
            if (user instanceof Bidder bidder) {
                double available = bidder.getAvailableBalance();
                double frozen    = bidder.getFrozenBalance();
                balanceLabel.setText(String.format("Khả dụng: %,.0f ₫", available));
                balanceLabel.setVisible(true);
                
                if (frozenLabel != null) {
                    if (frozen > 0) {
                        frozenLabel.setText(String.format("Đóng băng: %,.0f ₫", frozen));
                        frozenLabel.setVisible(true);
                        frozenLabel.setManaged(true);
                    } else {
                        frozenLabel.setVisible(false);
                        frozenLabel.setManaged(false);
                    }
                }
            } else {
                balanceLabel.setVisible(false);
                if (frozenLabel != null) {
                    frozenLabel.setVisible(false);
                    frozenLabel.setManaged(false);
                }
            }
        }
    }

    private void updateStatusBadge() {
        AuctionStatus status = currentAuction.getStatus();
        statusBadge.setText(currentAuction.getStatusDisplay());
        statusBadge.getStyleClass().removeAll(
                "badge-pending", "badge-open", "badge-running", "badge-closed", "badge-canceled");
        String cls = switch (status) {
            case PENDING  -> "badge-pending";
            case OPEN     -> "badge-open";
            case RUNNING  -> "badge-running";
            case CLOSED   -> "badge-closed";
            case CANCELED -> "badge-canceled";
        };
        statusBadge.getStyleClass().add(cls);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Chart / Feed helpers
    // ──────────────────────────────────────────────────────────────────────────

    private void addBidToChart(BidTransaction bid) {
        chartTick++;
        priceSeries.getData().add(new XYChart.Data<>("#" + chartTick, bid.getAmount()));
    }

    private void addRawToChart(double amount) {
        chartTick++;
        priceSeries.getData().add(new XYChart.Data<>("#" + chartTick, amount));
    }

    private void addBidToFeed(BidTransaction bid) {
        String entry = String.format("[%s]  %s  →  %,.0f ₫",
                bid.getTimestamp().format(TIME_FMT),
                bid.getBidder().getUsername(),
                bid.getAmount());
        appendToFeed(entry);
    }

    private void appendToFeed(String entry) {
        ObservableList<String> items = liveFeedList.getItems();
        if (!items.isEmpty() && items.get(0).equals(entry)) return; // dedupe
        items.add(0, entry);
        if (items.size() > 50) items.remove(50, items.size());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FXML actions
    // ──────────────────────────────────────────────────────────────────────────

    @FXML
    private void handlePlaceBid(ActionEvent event) {
        bidErrorLabel.setText("");
        String input = bidAmountField.getText().trim();
        if (input.isEmpty()) { bidErrorLabel.setText("Vui lòng nhập số tiền đặt giá."); return; }

        double amount;
        try { amount = Double.parseDouble(input.replace(",", "")); }
        catch (NumberFormatException e) { bidErrorLabel.setText("Số tiền không hợp lệ."); return; }

        double minBid = currentAuction.getHighestBid();
        if (amount <= minBid) {
            bidErrorLabel.setText("Số tiền đặt giá phải lớn hơn hoặc bằng số tiền tối thiểu quy định.");
            bidErrorLabel.setStyle("-fx-text-fill: red;");
            if (mainScrollPane != null) mainScrollPane.setVvalue(0.0);
            return;
        }

        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Bidder bidder)) {
            bidErrorLabel.setText("Chỉ Bidder mới có thể đặt giá."); return;
        }

        if (!wsConnected || wsClient == null) {
            bidErrorLabel.setText("❌ Không thể kết nối server. Vui lòng chờ server hoạt động.");
            return;
        }

        // ── Send PLACE_BID to server; server validates + broadcasts BID_UPDATE ──
        JsonObject req = new JsonObject();
        req.addProperty("type",           "PLACE_BID");
        req.addProperty("auctionId",      currentAuction.getId());
        req.addProperty("bidderId",       bidder.getId());
        req.addProperty("bidderUsername", bidder.getUsername());
        req.addProperty("bidderBalance",  bidder.getAccountBalance());
        req.addProperty("amount",         amount);
        wsClient.send(req.toString());
        bidAmountField.clear();

        // Vô hiệu hóa ngay lập tức để tránh double-click trong khi chờ server phản hồi.
        // refreshLivePanel() sẽ tự quản lý trạng thái sau khi nhận BID_UPDATE từ server.
        placeBidButton.setDisable(true);
        bidAmountField.setDisable(true);
        bidErrorLabel.setText("⏳ Đang gửi giá đặt đến server...");
        bidErrorLabel.setStyle("-fx-text-fill: #64b5f6;");
    }



    @FXML
    private void handleRegisterAutoBid(ActionEvent event) {
        if (autoBidErrorLabel == null) return;
        autoBidErrorLabel.setText("");
        
        String maxBidInput = autoMaxBidField.getText().trim();
        String incInput = autoIncrementField.getText().trim();

        if (maxBidInput.isEmpty() || incInput.isEmpty()) { 
            autoBidErrorLabel.setText("Vui lòng nhập đầy đủ giá tối đa và bước giá."); 
            return; 
        }

        double maxBid, increment;
        try { 
            maxBid = Double.parseDouble(maxBidInput.replace(",", ""));
            increment = Double.parseDouble(incInput.replace(",", ""));
        } catch (NumberFormatException e) { 
            autoBidErrorLabel.setText("Số tiền không hợp lệ."); 
            return; 
        }

        double minBid = currentAuction.getHighestBid();
        if (maxBid <= minBid) {
            autoBidErrorLabel.setText("Giá tối đa phải lớn hơn giá hiện tại.");
            autoBidErrorLabel.setStyle("-fx-text-fill: red;");
            if (mainScrollPane != null) mainScrollPane.setVvalue(1.0);
            return;
        }
        if (increment <= 0) {
            autoBidErrorLabel.setText("Bước giá phải lớn hơn 0.");
            autoBidErrorLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Bidder bidder)) {
            autoBidErrorLabel.setText("Chỉ Bidder mới có thể đăng ký Auto-Bid."); 
            return;
        }

        if (!wsConnected || wsClient == null) {
            autoBidErrorLabel.setText("❌ Không thể kết nối server. Vui lòng chờ.");
            autoBidErrorLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        JsonObject req = new JsonObject();
        req.addProperty("type", "REGISTER_AUTO_BID");
        req.addProperty("auctionId", currentAuction.getId());
        req.addProperty("bidderId", bidder.getId());
        req.addProperty("maxBid", maxBid);
        req.addProperty("increment", increment);
        wsClient.send(req.toString());

        registerAutoBidButton.setDisable(true);
        autoMaxBidField.setDisable(true);
        autoIncrementField.setDisable(true);
        autoBidErrorLabel.setText("⏳ Đang gửi yêu cầu...");
        autoBidErrorLabel.setStyle("-fx-text-fill: #64b5f6;");
    }

    @FXML
    private void handleBack(ActionEvent event) {
        shutdown();
        try {
            NavigationManager.getInstance().navigateTo(
                    NavigationManager.AUCTION_LIST, "Danh sách đấu giá", null);
        } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * Hiển thị thông báo người chiến thắng khi phiên đấu giá kết thúc.
     * Gọi trên FX thread (từ Platform.runLater trong handleWsMessage).
     */
    private void showWinnerAnnouncement(String winnerUsername, double winnerBid) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("🏆 Phiên đấu giá kết thúc");
        alert.setHeaderText("Phiên đấu giá: " + (currentAuction != null
                ? currentAuction.getItem().getName() : ""));
        if (winnerUsername != null && winnerBid > 0) {
            alert.setContentText(String.format(
                    "🎉 Người chiến thắng: %s%n💰 Giá chốt: %,.0f ₫%n%nSố dư của người thắng đã được trừ tự động.",
                    winnerUsername, winnerBid));
        } else {
            alert.setContentText("Phiên đấu giá đã kết thúc.\nKhông có ai đặt giá trong phiên này.");
        }
        alert.showAndWait();
    }
}
