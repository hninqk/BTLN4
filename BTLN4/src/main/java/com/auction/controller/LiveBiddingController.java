package com.auction.controller;

import com.auction.client.AuctionClient;
import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;
import com.auction.model.*;
import com.auction.service.AuctionService;
import com.auction.util.DataReceiver;
import com.auction.util.ImageLoaderUtil;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;

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
 * LiveBiddingController – real-time bidding screen.
 *
 * Uses WebSocket (AuctionClient) for real-time broadcast updates from the server.
 * When a bid arrives via WS, ALL connected clients update simultaneously.
 * Falls back to 1-second polling if WS connection fails.
 *
 * Works with ngrok: set -Dauction.server.url=ws://<ngrok-host>:<port>/auction
 */
public class LiveBiddingController implements DataReceiver {

    @FXML private Label     auctionTitleLabel;
    @FXML private Label     auctionSubtitleLabel;
    @FXML private ImageView itemImageView;
    @FXML private Label     statusBadge;
    @FXML private Label     livePriceLabel;
    @FXML private Label     liveTimerLabel;
    @FXML private Label     bidCountLabel;
    @FXML private TextField bidAmountField;
    @FXML private Label     minBidHint;
    @FXML private Label     bidErrorLabel;
    @FXML private Button    placeBidButton;
    @FXML private ListView<String> liveFeedList;
    @FXML private Label     lastUpdateLabel;
    @FXML private LineChart<String, Number> priceChart;

    private Auction currentAuction;
    private ScheduledExecutorService scheduler;
    private XYChart.Series<String, Number> priceSeries;
    private int bidTickCounter = 0;

    // WebSocket client for real-time updates
    private AuctionClient wsClient;
    private boolean wsConnected = false;

    private final Gson gson = new Gson();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // =========================================================================
    // DataReceiver
    // =========================================================================

    @Override
    public void receiveData(Object data) {
        if (data instanceof Auction a) {
            currentAuction = a;
            populateView();
            connectWebSocket();   // Real-time WS connection
            startTimerScheduler(); // Timer countdown (UI only, no data polling)
        }
    }

    // =========================================================================
    // Initialize
    // =========================================================================

    @FXML
    public void initialize() {
        bidErrorLabel.setText("");
        setupChart();

        UnaryOperator<TextFormatter.Change> numericFilter = change -> {
            String newText = change.getControlNewText();
            return newText.matches("[0-9,.]*") ? change : null;
        };
        bidAmountField.setTextFormatter(new TextFormatter<>(numericFilter));
    }

    // =========================================================================
    // WebSocket Connection
    // =========================================================================

    /**
     * Connect to the WS server and register the broadcast callback.
     * On any WS message (new bid), refresh the auction from DB and update UI.
     */
    private void connectWebSocket() {
        wsClient = new AuctionClient();

        // Run connect in background (it's a blocking join)
        Thread wsThread = new Thread(() -> {
            wsClient.connect(
                    msg -> Platform.runLater(() -> handleWsMessage(msg)),
                    err -> Platform.runLater(() -> {
                        wsConnected = false;
                        System.err.println("[LiveBidding] WS error: " + err);
                        // Disable bidding – server is unreachable
                        placeBidButton.setDisable(true);
                        bidAmountField.setDisable(true);
                        bidErrorLabel.setText("🔴 Mất kết nối server – không thể đặt giá.");
                        startPollingFallback(); // still show live data if DB is local
                    })
            );
            wsConnected = wsClient.isConnected();
            Platform.runLater(this::refreshDisplay);
        }, "WS-Connect-Thread");
        wsThread.setDaemon(true);
        wsThread.start();
    }

    /**
     * Handles a broadcast message from the server.
     * Parses the JSON and updates the UI.
     */
    private void handleWsMessage(String msg) {
        try {
            JsonObject json = gson.fromJson(msg, JsonObject.class);

            // Server-side error (e.g. InvalidBidException, Auction not found)
            if (json.has("error")) {
                bidErrorLabel.setText("⚠ " + json.get("error").getAsString());
                return;
            }

            if (json.has("amount") && json.has("bidder") && json.has("time")) {
                double amount = json.get("amount").getAsDouble();
                String bidder = json.get("bidder").getAsString();
                String time   = json.get("time").getAsString();

                // Refresh auction from DB to get the latest state
                AuctionService.getInstance().findById(currentAuction.getId())
                        .ifPresent(fresh -> {
                            currentAuction = fresh;
                            refreshDisplay();
                        });

                // Add broadcast entry to feed & chart
                addBroadcastToFeed(amount, bidder, time);
                addBroadcastToChart(amount);
            }
        } catch (Exception e) {
            System.err.println("[LiveBidding] Failed to parse WS message: " + e.getMessage());
        }
    }

    /**
     * Polling fallback – used when WS is unavailable.
     * Polls DB every 2 seconds for new bids.
     */
    private void startPollingFallback() {
        if (scheduler != null && !scheduler.isShutdown()) return; // already running
        System.out.println("[LiveBidding] WS unavailable – polling local DB (view-only, bids blocked).");
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LiveBidding-Poll");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            if (currentAuction == null) return;
            AuctionService.getInstance().findById(currentAuction.getId())
                    .ifPresent(fresh -> {
                        int prevCount = currentAuction.getBidHistory().size();
                        currentAuction = fresh;
                        // Only update feed/chart for genuinely new bids
                        List<BidTransaction> history = fresh.getBidHistory();
                        for (int i = prevCount; i < history.size(); i++) {
                            addBidToChart(history.get(i));
                            addBidToFeed(history.get(i));
                        }
                        refreshDisplay();
                    });
        }), 2, 2, TimeUnit.SECONDS);
    }

    // =========================================================================
    // Timer scheduler (countdown only, no DB polling)
    // =========================================================================

    private void startTimerScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LiveBidding-Timer");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                () -> Platform.runLater(this::refreshDisplay), 0, 1, TimeUnit.SECONDS);
    }

    // =========================================================================
    // Populate / Refresh
    // =========================================================================

    private void populateView() {
        if (currentAuction == null) return;

        auctionTitleLabel.setText(currentAuction.getItem().getName());
        itemImageView.setImage(ImageLoaderUtil.loadItemImage(
                currentAuction.getItem().getImageUrl(), 360, 220));
        auctionSubtitleLabel.setText("Người bán: " + currentAuction.getSeller().getUsername()
                + "  |  Kết thúc: "
                + currentAuction.getEndTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        // Pre-load existing bids
        List<BidTransaction> history = currentAuction.getBidHistory();
        for (BidTransaction bid : history) {
            addBidToChart(bid);
            addBidToFeed(bid);
        }

        updateStatusBadge();
        refreshDisplay();

        // Enable bid input only if RUNNING and user is Bidder
        User user = SessionManager.getInstance().getCurrentUser();
        boolean canBid = currentAuction.getStatus() == AuctionStatus.RUNNING && user instanceof Bidder;
        placeBidButton.setDisable(!canBid);
        bidAmountField.setDisable(!canBid);
    }

    private void refreshDisplay() {
        if (currentAuction == null) return;
        livePriceLabel.setText(String.format("%,.0f ₫", currentAuction.getHighestBid()));
        bidCountLabel.setText(String.valueOf(currentAuction.getBidHistory().size()));
        minBidHint.setText("Giá tối thiểu: "
                + String.format("%,.0f ₫", currentAuction.getHighestBid() + 1));
        lastUpdateLabel.setText("Cập nhật: " + LocalDateTime.now().format(TIME_FMT)
                + (wsConnected ? " 🟢 Server" : " 🔴 Offline – chỉ xem, không đặt giá"));

        Duration remaining = Duration.between(LocalDateTime.now(), currentAuction.getEndTime());
        if (remaining.isNegative()) {
            liveTimerLabel.setText("Đã kết thúc");
            if (scheduler != null && !scheduler.isShutdown()) scheduler.shutdown();
        } else {
            liveTimerLabel.setText(String.format("%02d:%02d:%02d",
                    remaining.toHours(), remaining.toMinutesPart(), remaining.toSecondsPart()));
        }
        updateStatusBadge();
    }

    private void updateStatusBadge() {
        if (currentAuction == null) return;
        statusBadge.setText(currentAuction.getStatusDisplay());
        statusBadge.getStyleClass().removeAll(
                "badge-pending", "badge-open", "badge-running", "badge-closed", "badge-canceled");
        switch (currentAuction.getStatus()) {
            case PENDING  -> statusBadge.getStyleClass().add("badge-pending");
            case OPEN     -> statusBadge.getStyleClass().add("badge-open");
            case RUNNING  -> statusBadge.getStyleClass().add("badge-running");
            case CLOSED   -> statusBadge.getStyleClass().add("badge-closed");
            case CANCELED -> statusBadge.getStyleClass().add("badge-canceled");
        }
    }

    // =========================================================================
    // Chart / Feed helpers
    // =========================================================================

    private void setupChart() {
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Giá đấu");
        priceChart.getData().add(priceSeries);
        priceChart.setCreateSymbols(true);
        priceChart.setAnimated(false);
    }

    private void addBidToChart(BidTransaction bid) {
        bidTickCounter++;
        priceSeries.getData().add(new XYChart.Data<>("#" + bidTickCounter, bid.getAmount()));
    }

    private void addBidToFeed(BidTransaction bid) {
        String entry = String.format("[%s]  %s  →  %,.0f ₫",
                bid.getTimestamp().format(TIME_FMT),
                bid.getBidder().getUsername(),
                bid.getAmount());
        appendToFeed(entry);
    }

    /** Called when a WS broadcast arrives (no BidTransaction object yet) */
    private void addBroadcastToFeed(double amount, String bidder, String time) {
        String entry = String.format("[%s]  %s  →  %,.0f ₫", time, bidder, amount);
        appendToFeed(entry);
    }

    private void addBroadcastToChart(double amount) {
        bidTickCounter++;
        priceSeries.getData().add(new XYChart.Data<>("#" + bidTickCounter, amount));
    }

    private void appendToFeed(String entry) {
        ObservableList<String> items = liveFeedList.getItems();
        // Avoid duplicate entries (same text already in feed)
        if (!items.isEmpty() && items.get(0).equals(entry)) return;
        items.add(0, entry);
        if (items.size() > 50) items.remove(50, items.size());
    }

    // =========================================================================
    // Place Bid
    // =========================================================================

    @FXML
    private void handlePlaceBid(ActionEvent event) {
        bidErrorLabel.setText("");
        String input = bidAmountField.getText().trim();
        if (input.isEmpty()) {
            bidErrorLabel.setText("Vui lòng nhập số tiền.");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(input.replace(",", ""));
        } catch (NumberFormatException e) {
            bidErrorLabel.setText("Số tiền không hợp lệ.");
            return;
        }

        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Bidder bidder)) {
            bidErrorLabel.setText("Chỉ Bidder mới có thể đặt giá.");
            return;
        }

        if (wsConnected && wsClient != null) {
            // ── WS mode: send bid to server, server broadcasts to ALL clients ──
            JsonObject req = new JsonObject();
            req.addProperty("auctionId", currentAuction.getId());
            req.addProperty("bidderId", bidder.getId());
            req.addProperty("amount", amount);
            wsClient.send(req.toString());
            bidAmountField.clear();
        } else {
            // ── Server is offline – reject the bid clearly ──
            // (The old fallback wrote directly to the local DB, which made it
            //  look like sync was working on the same machine, but remote users
            //  each have their own db.auction so nothing was actually shared.)
            bidErrorLabel.setText(
                    "❌ Không thể kết nối server. Vui lòng chờ server hoạt động trở lại.");
        }
    }

    // =========================================================================
    // Back
    // =========================================================================

    @FXML
    private void handleBack(ActionEvent event) {
        // Clean up resources
        if (scheduler != null && !scheduler.isShutdown()) scheduler.shutdown();
        if (wsClient != null) wsClient.disconnect();

        try {
            NavigationManager.getInstance().navigateTo(
                    NavigationManager.AUCTION_DETAIL, "Chi tiết đấu giá", currentAuction);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
