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
import java.util.concurrent.*;
import java.util.function.UnaryOperator;

/**
 * LiveBiddingController – màn hình đấu giá real-time.
 *
 * Tối ưu hiệu năng:
 * 1. DB query LUÔN chạy trên background thread (ExecutorService riêng),
 *    sau đó mới dùng Platform.runLater() để cập nhật UI. Không bao giờ
 *    gọi DB trực tiếp từ JavaFX Application Thread.
 * 2. Polling fallback: DB query chạy trên scheduler thread, UI update
 *    chỉ xảy ra khi có dữ liệu mới (không render lại mỗi giây vô ích).
 * 3. LineChart giới hạn 30 điểm — tránh quá tải render khi bid nhiều.
 * 4. refreshDisplay() chỉ cập nhật label khi giá trị thực sự thay đổi.
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
    private ScheduledExecutorService timerScheduler;   // chỉ cho countdown timer
    private ScheduledExecutorService pollScheduler;    // chỉ cho polling fallback
    private ExecutorService          bgExecutor;       // background DB queries

    private XYChart.Series<String, Number> priceSeries;
    private int bidTickCounter = 0;

    // Theo dõi giá trị cũ để tránh render không cần thiết
    private double   lastDisplayedPrice = -1;
    private int      lastDisplayedCount = -1;

    // Số điểm tối đa trên biểu đồ (tránh quá tải JavaFX chart renderer)
    private static final int MAX_CHART_POINTS = 30;

    // WebSocket client
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
            bgExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "LiveBidding-BG");
                t.setDaemon(true);
                return t;
            });
            populateView();
            connectWebSocket();
            startTimerScheduler();
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

    private void connectWebSocket() {
        wsClient = new AuctionClient();

        Thread wsThread = new Thread(() -> {
            wsClient.connect(
                    msg -> Platform.runLater(() -> handleWsMessage(msg)),
                    err -> Platform.runLater(() -> {
                        wsConnected = false;
                        System.err.println("[LiveBidding] WS error: " + err);
                        placeBidButton.setDisable(true);
                        bidAmountField.setDisable(true);
                        bidErrorLabel.setText("🔴 Mất kết nối server – không thể đặt giá.");
                        startPollingFallback();
                    })
            );
            wsConnected = wsClient.isConnected();
            Platform.runLater(this::refreshDisplay);
        }, "WS-Connect-Thread");
        wsThread.setDaemon(true);
        wsThread.start();
    }

    /**
     * Xử lý message WebSocket — chỉ cập nhật model và UI, không query DB.
     */
    private void handleWsMessage(String msg) {
        try {
            JsonObject json = gson.fromJson(msg, JsonObject.class);

            if (json.has("error")) {
                bidErrorLabel.setText("⚠ " + json.get("error").getAsString());
                return;
            }

            if (json.has("amount") && json.has("bidder") && json.has("time")) {
                double amount      = json.get("amount").getAsDouble();
                String bidderName  = json.get("bidder").getAsString();
                String time        = json.get("time").getAsString();
                String auctionId   = json.has("auctionId")
                        ? json.get("auctionId").getAsString() : null;

                // Chỉ cập nhật nếu broadcast thuộc về auction này
                if (auctionId != null && currentAuction != null
                        && !auctionId.equals(currentAuction.getId())) {
                    return;
                }

                // Cập nhật model in-memory (không cần query DB)
                if (currentAuction != null) {
                    currentAuction.setHighestBid(amount);
                    Bidder dummyBidder = new Bidder(
                            "remote", LocalDateTime.now(), bidderName, "", 0);
                    BidTransaction dummyBid = new BidTransaction(
                            java.util.UUID.randomUUID().toString(),
                            LocalDateTime.now(),
                            dummyBidder,
                            currentAuction,
                            amount);
                    currentAuction.injectBid(dummyBid);
                }

                // Cập nhật UI (đã trên FX thread vì gọi từ Platform.runLater)
                refreshDisplay();
                addBroadcastToFeed(amount, bidderName, time);
                addBroadcastToChart(amount);
            }

        } catch (Exception e) {
            System.err.println("[LiveBidding] Failed to parse WS message: " + e.getMessage());
        }
    }

    /**
     * Polling fallback khi WS không khả dụng.
     * *** FIX QUAN TRỌNG: DB query chạy trên pollScheduler thread ***
     * Chỉ gọi Platform.runLater khi có dữ liệu mới để cập nhật UI.
     */
    private void startPollingFallback() {
        if (pollScheduler != null && !pollScheduler.isShutdown()) return;

        System.out.println("[LiveBidding] WS unavailable – polling DB (view-only).");
        pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LiveBidding-Poll");
            t.setDaemon(true);
            return t;
        });

        pollScheduler.scheduleAtFixedRate(() -> {
            // ── CHẠY TRÊN BACKGROUND THREAD (không phải FX thread) ──
            if (currentAuction == null) return;
            try {
                // DB query thực hiện tại đây — không block UI
                AuctionService.getInstance()
                        .findById(currentAuction.getId())
                        .ifPresent(fresh -> {
                            int prevCount = currentAuction.getBidHistory().size();
                            List<BidTransaction> newBids = fresh.getBidHistory();

                            // Chỉ cập nhật UI nếu có bid mới
                            if (newBids.size() > prevCount || fresh.getHighestBid() != currentAuction.getHighestBid()) {
                                List<BidTransaction> addedBids = newBids.subList(prevCount, newBids.size());
                                currentAuction = fresh;

                                Platform.runLater(() -> {
                                    for (BidTransaction bid : addedBids) {
                                        addBidToChart(bid);
                                        addBidToFeed(bid);
                                    }
                                    refreshDisplay();
                                });
                            }
                        });
            } catch (Exception e) {
                System.err.println("[LiveBidding] Poll error: " + e.getMessage());
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    // =========================================================================
    // Timer scheduler (chỉ countdown, không query DB)
    // =========================================================================

    private void startTimerScheduler() {
        timerScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LiveBidding-Timer");
            t.setDaemon(true);
            return t;
        });
        // Chỉ cập nhật countdown timer mỗi giây — không trigger DB query
        timerScheduler.scheduleAtFixedRate(
                () -> Platform.runLater(this::refreshTimerOnly),
                0, 1, TimeUnit.SECONDS);
    }

    // =========================================================================
    // Populate / Refresh
    // =========================================================================

    private void populateView() {
        if (currentAuction == null) return;

        auctionTitleLabel.setText(currentAuction.getItem().getName());

        // Load ảnh trên background thread để không block UI
        String imageUrl = currentAuction.getItem().getImageUrl();
        bgExecutor.submit(() -> {
            var image = ImageLoaderUtil.loadItemImage(imageUrl, 360, 220);
            Platform.runLater(() -> itemImageView.setImage(image));
        });

        auctionSubtitleLabel.setText("Người bán: " + currentAuction.getSeller().getUsername()
                + "  |  Kết thúc: "
                + currentAuction.getEndTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        // Load lịch sử bid ban đầu
        List<BidTransaction> history = currentAuction.getBidHistory();
        for (BidTransaction bid : history) {
            addBidToChart(bid);
            addBidToFeed(bid);
        }

        updateStatusBadge();
        refreshDisplay();

        User user = SessionManager.getInstance().getCurrentUser();
        boolean canBid = currentAuction.getStatus() == AuctionStatus.RUNNING && user instanceof Bidder;
        placeBidButton.setDisable(!canBid);
        bidAmountField.setDisable(!canBid);
    }

    /**
     * Cập nhật đầy đủ các label — chỉ ghi khi giá trị thực sự thay đổi.
     */
    private void refreshDisplay() {
        if (currentAuction == null) return;

        double  currentPrice = currentAuction.getHighestBid();
        int     currentCount = currentAuction.getBidHistory().size();

        // Chỉ cập nhật price/count nếu thay đổi
        if (currentPrice != lastDisplayedPrice) {
            livePriceLabel.setText(String.format("%,.0f ₫", currentPrice));
            minBidHint.setText("Giá tối thiểu: " + String.format("%,.0f ₫", currentPrice + 1));
            lastDisplayedPrice = currentPrice;
        }
        if (currentCount != lastDisplayedCount) {
            bidCountLabel.setText(String.valueOf(currentCount));
            lastDisplayedCount = currentCount;
        }

        lastUpdateLabel.setText("Cập nhật: " + LocalDateTime.now().format(TIME_FMT)
                + (wsConnected ? " 🟢 Server" : " 🔴 Offline – chỉ xem"));

        refreshTimerOnly();
        updateStatusBadge();
    }

    /**
     * Chỉ cập nhật đồng hồ đếm ngược — rất nhẹ, gọi mỗi giây là an toàn.
     */
    private void refreshTimerOnly() {
        if (currentAuction == null) return;
        Duration remaining = Duration.between(LocalDateTime.now(), currentAuction.getEndTime());
        if (remaining.isNegative()) {
            liveTimerLabel.setText("Đã kết thúc");
            shutdownTimers();
        } else {
            liveTimerLabel.setText(String.format("%02d:%02d:%02d",
                    remaining.toHours(), remaining.toMinutesPart(), remaining.toSecondsPart()));
        }
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
        priceChart.setAnimated(false);  // tắt animation để tăng hiệu năng
    }

    private void addBidToChart(BidTransaction bid) {
        bidTickCounter++;
        addChartPoint("#" + bidTickCounter, bid.getAmount());
    }

    private void addBidToFeed(BidTransaction bid) {
        String entry = String.format("[%s]  %s  →  %,.0f ₫",
                bid.getTimestamp().format(TIME_FMT),
                bid.getBidder().getUsername(),
                bid.getAmount());
        appendToFeed(entry);
    }

    private void addBroadcastToFeed(double amount, String bidder, String time) {
        String entry = String.format("[%s]  %s  →  %,.0f ₫", time, bidder, amount);
        appendToFeed(entry);
    }

    private void addBroadcastToChart(double amount) {
        bidTickCounter++;
        addChartPoint("#" + bidTickCounter, amount);
    }

    /**
     * Thêm điểm vào chart và giới hạn tối đa MAX_CHART_POINTS điểm.
     * Tránh JavaFX re-layout toàn bộ chart khi có quá nhiều điểm.
     */
    private void addChartPoint(String label, double value) {
        ObservableList<XYChart.Data<String, Number>> data = priceSeries.getData();
        data.add(new XYChart.Data<>(label, value));
        // Xóa điểm cũ nhất nếu vượt giới hạn
        if (data.size() > MAX_CHART_POINTS) {
            data.remove(0);
        }
    }

    private void appendToFeed(String entry) {
        ObservableList<String> items = liveFeedList.getItems();
        // Bỏ qua nếu trùng entry đầu tiên
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
            // WS mode: gửi bid lên server, server broadcast cho tất cả client
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
            bidErrorLabel.setText(
                    "❌ Không thể kết nối server. Vui lòng chờ server hoạt động trở lại.");
        }
    }

    // =========================================================================
    // Back / Cleanup
    // =========================================================================

    @FXML
    private void handleBack(ActionEvent event) {
        shutdownAll();
        try {
            NavigationManager.getInstance().navigateTo(
                    NavigationManager.AUCTION_DETAIL, "Chi tiết đấu giá", currentAuction);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Dừng timer (gọi khi auction kết thúc). */
    private void shutdownTimers() {
        if (timerScheduler != null && !timerScheduler.isShutdown()) timerScheduler.shutdown();
    }

    /** Dọn dẹp tất cả resource khi thoát màn hình. */
    private void shutdownAll() {
        shutdownTimers();
        if (pollScheduler != null && !pollScheduler.isShutdown()) pollScheduler.shutdown();
        if (bgExecutor   != null && !bgExecutor.isShutdown())   bgExecutor.shutdownNow();
        if (wsClient     != null) wsClient.disconnect();
    }
}
