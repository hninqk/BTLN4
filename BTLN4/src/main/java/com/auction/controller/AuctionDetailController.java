package com.auction.controller;

import com.auction.client.AuctionClient;
import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;
import com.auction.model.*;
import com.auction.service.AppFacade;
import com.auction.util.DataReceiver;
import com.auction.util.ImageLoaderUtil;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
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
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * AuctionDetailController – merged live-bidding view.
 *
 * Uses AppFacade (service layer) — no direct repository access.
 *
 * Fixes applied:
 *  – Status badge now refreshed from DB every second (was reading stale in-memory state).
 *  – switch fall-through bug: explicit removeAll() + single add per tick.
 *  – Merged price chart, live feed, countdown timer from old LiveBidding screen.
 *  – "Xem trực tiếp" button removed (everything is here).
 *  – startTime shown as "Chưa bắt đầu" when null.
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
    private int chartTick   = 0;
    private int knownBidCount = 0;

    // WebSocket client for real-time bid submission
    private AuctionClient wsClient;
    private boolean wsConnected = false;
    private final Gson gson = new Gson();

    private static final DateTimeFormatter FMT     = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_SEC = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void receiveData(Object data) {
        if (data instanceof Auction a) {
            this.auctionId = a.getId();
            // Always fetch fresh copy from DB
            this.currentAuction = app.findAuctionById(auctionId).orElse(a);
            populateStaticView();
            preloadBidsIntoChartAndFeed();
            refreshLivePanel();
            connectWebSocket();   // Real-time WS for bid submission
            startScheduler();     // 1s DB polling for UI refresh
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
        if (wsClient != null) wsClient.disconnect();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WebSocket
    // ──────────────────────────────────────────────────────────────────────────

    private void connectWebSocket() {
        wsClient = new AuctionClient();
        Thread t = new Thread(() -> {
            wsClient.connect(
                    msg -> Platform.runLater(() -> {
                        // Parse broadcast and update model manually
                        try {
                            JsonObject json = gson.fromJson(msg, JsonObject.class);
                            if (json.has("amount") && json.has("bidder") && json.has("time")) {
                                double amount = json.get("amount").getAsDouble();
                                String bidderName = json.get("bidder").getAsString();
                                String timeStr = json.get("time").getAsString();
                                String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;

                                // Only update if it's the current auction
                                if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;

                                if (currentAuction != null) {
                                    currentAuction.setHighestBid(amount);
                                    
                                    // Inject dummy bid for UI consistency
                                    Bidder dummy = new Bidder("remote", LocalDateTime.now(), bidderName, "", 0);
                                    BidTransaction dummyBid = new BidTransaction(
                                            java.util.UUID.randomUUID().toString(), 
                                            LocalDateTime.now(), dummy, currentAuction, amount);
                                    currentAuction.injectBid(dummyBid);
                                }
                                refreshLivePanel();
                            }
                        } catch (Exception e) {
                            System.err.println("[AuctionDetail] WS parse error: " + e.getMessage());
                        }
                    }),
                    err -> Platform.runLater(() -> {
                        wsConnected = false;
                        placeBidButton.setDisable(true);
                        bidAmountField.setDisable(true);
                        bidErrorLabel.setText("🔴 Mất kết nối server – không thể đặt giá.");
                    })
            );
            wsConnected = wsClient.isConnected();
            Platform.runLater(this::refreshLivePanel);
        }, "AuctionDetail-WS");
        t.setDaemon(true);
        t.start();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Setup
    // ──────────────────────────────────────────────────────────────────────────

    private void setupBidHistoryColumns() {
        colBidder.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBidder().getUsername()));
        colAmount.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%,.0f ₫", c.getValue().getAmount())));
        colBidTime.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTimestamp().format(FMT_SEC)));
    }

    private void setupChart() {
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Giá đấu");
        priceChart.getData().add(priceSeries);
        priceChart.setCreateSymbols(true);
        priceChart.setAnimated(false);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Static info (called once)
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
        for (BidTransaction bid : history) { addBidToChart(bid); addBidToFeed(bid); }
        knownBidCount = history.size();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scheduler – re-fetches from DB every second
    // ──────────────────────────────────────────────────────────────────────────

    private void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AuctionDetail-Scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> Platform.runLater(this::tickRefresh),
                1, 1, TimeUnit.SECONDS);
    }

    private void tickRefresh() {
        if (auctionId == null) return;
        // Re-fetch from DB for status updates (PENDING -> RUNNING -> CLOSED)
        app.findAuctionById(auctionId).ifPresent(fresh -> {
            if (currentAuction != null) {
                // Preserve highest bid and history if local is ahead (WS updates)
                if (currentAuction.getHighestBid() > fresh.getHighestBid()) {
                    fresh.setHighestBid(currentAuction.getHighestBid());
                }
                if (currentAuction.getBidHistory().size() > fresh.getBidHistory().size()) {
                    // This is a bit simplified but keeps the UI consistent for remote users
                    // who don't have the full bid history in their local DB.
                    List<BidTransaction> localHistory = currentAuction.getBidHistory();
                    for (int i = fresh.getBidHistory().size(); i < localHistory.size(); i++) {
                        fresh.injectBid(localHistory.get(i));
                    }
                }
            }
            currentAuction = fresh;
        });
        refreshLivePanel();
    }


    // ──────────────────────────────────────────────────────────────────────────
    // Live panel (called every second)
    // ──────────────────────────────────────────────────────────────────────────

    private void refreshLivePanel() {
        if (currentAuction == null) return;

        // Price + count
        currentPriceLabel.setText(String.format("%,.0f ₫", currentAuction.getHighestBid()));
        bidCountLabel.setText(currentAuction.getBidHistory().size() + " lượt đấu giá");
        minBidHint.setText("Tối thiểu: " + String.format("%,.0f ₫", currentAuction.getHighestBid() + 1));
        lastUpdateLabel.setText("Cập nhật: " + LocalDateTime.now().format(TIME_FMT)
                + (wsConnected ? " 🟢 Server" : " 🔴 Offline"));

        // Status badge (this was the "not updating" bug — fixed by re-fetching above)
        updateStatusBadge();

        // startTime
        LocalDateTime st = currentAuction.getStartTime();
        startTimeLabel.setText(st != null ? st.format(FMT) : "Chưa bắt đầu");

        // Timer
        AuctionStatus status = currentAuction.getStatus();
        switch (status) {
            case PENDING  -> timeRemainingLabel.setText("⏳ Chờ Admin duyệt");
            case OPEN     -> timeRemainingLabel.setText("🟢 Chờ Admin bắt đầu");
            case CLOSED, CANCELED -> {
                timeRemainingLabel.setText("Đã kết thúc");
                shutdown();
            }
            case RUNNING -> {
                Duration remaining = Duration.between(LocalDateTime.now(), currentAuction.getEndTime());
                timeRemainingLabel.setText(remaining.isNegative() ? "Hết giờ" :
                        String.format("%02d:%02d:%02d",
                                remaining.toHours(), remaining.toMinutesPart(), remaining.toSecondsPart()));
            }
        }

        // New bids → chart + feed
        List<BidTransaction> history = currentAuction.getBidHistory();
        if (history.size() > knownBidCount) {
            for (int i = knownBidCount; i < history.size(); i++) {
                addBidToChart(history.get(i));
                addBidToFeed(history.get(i));
            }
            knownBidCount = history.size();
        }

        // Bid history table
        bidHistoryTable.setItems(FXCollections.observableArrayList(history));

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

        // Bid button + balance display for Bidder
        User user = SessionManager.getInstance().getCurrentUser();
        boolean canBid = status == AuctionStatus.RUNNING && user instanceof Bidder;
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
        // Remove ALL existing badge style classes first, then add exactly one
        statusBadge.getStyleClass().removeAll(
                "badge-pending", "badge-open", "badge-running", "badge-closed", "badge-canceled");
        String badgeClass = switch (status) {
            case PENDING  -> "badge-pending";
            case OPEN     -> "badge-open";
            case RUNNING  -> "badge-running";
            case CLOSED   -> "badge-closed";
            case CANCELED -> "badge-canceled";
        };
        statusBadge.getStyleClass().add(badgeClass);
    }

    private void addBidToChart(BidTransaction bid) {
        chartTick++;
        priceSeries.getData().add(new XYChart.Data<>("#" + chartTick, bid.getAmount()));
    }

    private void addBidToFeed(BidTransaction bid) {
        String entry = String.format("[%s]  %s  →  %,.0f ₫",
                bid.getTimestamp().format(TIME_FMT), bid.getBidder().getUsername(), bid.getAmount());
        ObservableList<String> items = liveFeedList.getItems();
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

        if (wsConnected && wsClient != null) {
            // ── WS mode: server handles validation + broadcasts to ALL clients ──
            JsonObject req = new JsonObject();
            req.addProperty("type", "PLACE_BID");
            req.addProperty("auctionId", currentAuction.getId());
            req.addProperty("bidderId", bidder.getId());
            req.addProperty("bidderUsername", bidder.getUsername());
            req.addProperty("bidderBalance", bidder.getAccountBalance());
            req.addProperty("amount", amount);
            wsClient.send(req.toString());
            bidAmountField.clear();

        } else {
            // ── Server offline – block the bid ──
            bidErrorLabel.setText(
                    "❌ Không thể kết nối server. Vui lòng chờ server hoạt động trở lại.");
        }
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
