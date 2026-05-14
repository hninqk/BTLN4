package com.auction.controller;

import com.auction.client.AuctionClient;
import com.auction.model.*;
import com.auction.service.AppFacade;
import com.auction.util.DataReceiver;
import com.auction.util.ImageLoaderUtil;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

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
    @FXML private Label     categoryInfoLabel;

    // ── Live bid panel ────────────────────────────────────────────────────────
    @FXML private Label     currentPriceLabel;
    @FXML private Label     bidCountLabel;
    @FXML private Label     timeRemainingLabel;
    @FXML private Label     lastUpdateLabel;
    @FXML private Label     minBidHint;
    @FXML private Label     balanceLabel;
    @FXML private TextField bidAmountField;
    @FXML private Label     bidErrorLabel;
    @FXML private Button    placeBidButton;

    // ── Live feed ─────────────────────────────────────────────────────────────
    @FXML private ListView<String> liveFeedList;

    // ── Price chart ───────────────────────────────────────────────────────────
    @FXML private LineChart<String, Number> priceChart;

    // ── Winner box ────────────────────────────────────────────────────────────
    @FXML private VBox  winnerBox;
    @FXML private Label winnerLabel;
    @FXML private Label winnerPriceLabel;

    // ── Bid history table ─────────────────────────────────────────────────────
    @FXML private TableView<BidTransaction>           bidHistoryTable;
    @FXML private TableColumn<BidTransaction, String> colBidder;
    @FXML private TableColumn<BidTransaction, String> colAmount;
    @FXML private TableColumn<BidTransaction, String> colBidTime;

    // ── Internal state ────────────────────────────────────────────────────────
    private final AppFacade app = AppFacade.getInstance();
    private String  auctionId;
    private Auction currentAuction;
    private ScheduledExecutorService scheduler;
    private XYChart.Series<String, Number> priceSeries;
    private int chartTick     = 0;
    private int knownBidCount = 0;

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
            populateStaticView();
            preloadBidsIntoChartAndFeed();
            refreshLivePanel();
            connectWebSocket();   // WS handles everything; REQUEST_SYNC on open
            startTimerScheduler(); // 1-second countdown only (no DB polling)
        }
    }

    @FXML
    public void initialize() {
        bidErrorLabel.setText("");
        setupBidHistoryColumns();
        setupChart();

        UnaryOperator<TextFormatter.Change> numericFilter = change ->
                change.getControlNewText().matches("[0-9,.]*") ? change : null;
        bidAmountField.setTextFormatter(new TextFormatter<>(numericFilter));
    }

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) scheduler.shutdown();
        if (wsClient  != null) wsClient.disconnect();
    }

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
            JsonObject json = gson.fromJson(msg, JsonObject.class);

            if (json.has("error")) {
                bidErrorLabel.setText("⚠ " + json.get("error").getAsString());
                return;
            }

            String type = json.has("type") ? json.get("type").getAsString() : "";

            switch (type) {
                case "BID_UPDATE"             -> onBidUpdate(json);
                case "AUCTION_STATUS_CHANGED" -> onStatusChanged(json);
                case "BALANCE_UPDATE"         -> onBalanceUpdate(json);
                case "FULL_SYNC"              -> onFullSync(json);
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

        if (currentAuction != null) {
            currentAuction.setHighestBid(amount);
            // Build a real bid entry for the UI
            Bidder dummy = new Bidder(bidderId, LocalDateTime.now(), bidderName, "", 0);
            BidTransaction dummyBid = new BidTransaction(
                    java.util.UUID.randomUUID().toString(),
                    LocalDateTime.parse(timeStr),
                    dummy, currentAuction, amount);
            currentAuction.injectBid(dummyBid);
        }

        // Add to feed & chart immediately (don't wait for scheduler)
        String timeDisplay = LocalDateTime.parse(timeStr).format(TIME_FMT);
        appendToFeed(String.format("[%s]  %s  →  %,.0f ₫", timeDisplay, bidderName, amount));
        addRawToChart(amount);

        bidErrorLabel.setText("");
        refreshLivePanel();
    }

    /** Auction status changed by admin (approve/start/finish/cancel). */
    private void onStatusChanged(JsonObject json) {
        String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;

        String newStatusStr = json.get("newStatus").getAsString();
        double highestBid   = json.has("highestBid") ? json.get("highestBid").getAsDouble() : -1;
        String startTimeStr = json.has("startTime")  ? json.get("startTime").getAsString()  : "";

        if (currentAuction != null) {
            // Reload from local DB to get the full updated object
            app.findAuctionById(currentAuction.getId()).ifPresent(fresh -> {
                // Merge any WS-injected bids that are not yet in DB
                for (BidTransaction bt : currentAuction.getBidHistory()) {
                    if (fresh.getBidHistory().stream().noneMatch(b -> b.getId().equals(bt.getId()))) {
                        fresh.injectBid(bt);
                    }
                }
                currentAuction = fresh;
            });

            // Patch with server values if reload was incomplete
            if (highestBid >= 0) currentAuction.setHighestBid(highestBid);
        }

        refreshLivePanel();
        System.out.println("[AuctionDetail] Status → " + newStatusStr);
    }

    /** Server tells this client its bidder's balance was updated (after auction finish). */
    private void onBalanceUpdate(JsonObject json) {
        String bidderId  = json.get("bidderId").getAsString();
        double newBalance= json.get("newBalance").getAsDouble();

        User me = SessionManager.getInstance().getCurrentUser();
        if (me instanceof Bidder myBidder && myBidder.getId().equals(bidderId)) {
            myBidder.setAccountBalance(newBalance);
            SessionManager.getInstance().setCurrentUser(myBidder);
            System.out.printf("[AuctionDetail] Balance updated: %.0f ₫%n", newBalance);
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

    private void setupBidHistoryColumns() {
        colBidder.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getBidder().getUsername()));
        colAmount.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("%,.0f ₫", c.getValue().getAmount())));
        colBidTime.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTimestamp().format(FMT_SEC)));
    }

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
        sellerLabel.setText(currentAuction.getSeller().getUsername());
        startPriceLabel.setText(String.format("%,.0f ₫", item.getStartingPrice()));
        endTimeLabel.setText(currentAuction.getEndTime().format(FMT));
        descriptionLabel.setText(item.getDescription());
        itemImageView.setImage(ImageLoaderUtil.loadItemImage(item.getImageUrl(), 420, 250));
        categoryInfoLabel.setText(item.getCategoryInfo());
    }

    private void preloadBidsIntoChartAndFeed() {
        if (currentAuction == null) return;
        List<BidTransaction> history = currentAuction.getBidHistory();
        for (BidTransaction bid : history) {
            addBidToChart(bid);
            addBidToFeed(bid);
        }
        knownBidCount = history.size();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Live panel (updated every second and on each WS event)
    // ──────────────────────────────────────────────────────────────────────────

    private void refreshLivePanel() {
        if (currentAuction == null) return;

        // Price + bid count
        currentPriceLabel.setText(String.format("%,.0f ₫", currentAuction.getHighestBid()));
        bidCountLabel.setText(currentAuction.getBidHistory().size() + " lượt đấu giá");
        minBidHint.setText("Tối thiểu: " + String.format("%,.0f ₫", currentAuction.getHighestBid() + 1));
        lastUpdateLabel.setText("Cập nhật: " + LocalDateTime.now().format(TIME_FMT)
                + (wsConnected ? " 🟢 Server" : " 🔴 Offline"));

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

        // Bid history table
        bidHistoryTable.setItems(FXCollections.observableArrayList(
                currentAuction.getBidHistory()));

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
        boolean canBid = status == AuctionStatus.RUNNING && user instanceof Bidder && wsConnected;
        placeBidButton.setDisable(!canBid);
        bidAmountField.setDisable(!canBid);
        if (balanceLabel != null) {
            if (user instanceof Bidder bidder) {
                balanceLabel.setText(String.format("Số dư: %,.0f ₫", bidder.getAccountBalance()));
                balanceLabel.setVisible(true);
            } else {
                balanceLabel.setVisible(false);
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
    }

    @FXML
    private void handleBack(ActionEvent event) {
        shutdown();
        try {
            NavigationManager.getInstance().navigateTo(
                    NavigationManager.AUCTION_LIST, "Danh sách đấu giá", null);
        } catch (IOException e) { e.printStackTrace(); }
    }
}
