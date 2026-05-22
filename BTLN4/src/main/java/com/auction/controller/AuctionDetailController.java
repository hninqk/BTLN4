package com.auction.controller;

import com.auction.client.AuctionClient;
import com.auction.model.*;
import com.auction.util.DataReceiver;
import com.auction.util.HotItemCache;
import com.auction.util.ImageLoaderUtil;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import com.auction.util.TimeSyncManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
 *
 * ─── PERFORMANCE ARCHITECTURE ───────────────────────────────────────────────
 * The monolithic refreshLivePanel() was split into 6 targeted refresh methods.
 * Each event source (scheduler, WS bid, WS balance, WS status, reconnect) now
 * calls ONLY the refresh methods for the UI subset it affects, avoiding
 * redundant JavaFX property invalidations, layout recalculations, and CSS
 * re-applications on unchanged nodes.
 *
 * Guard helpers (setTextIfChanged, setVisibleIfChanged, setManagedIfChanged)
 * prevent JavaFX StringProperty / BooleanProperty invalidation when the value
 * hasn't actually changed — each invalidation triggers a full layout pass up
 * to the Scene root and a CSS re-application pass down from the node.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class AuctionDetailController implements DataReceiver, com.auction.service.AuctionWebSocketService.AuctionWebSocketListener {

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
    @FXML private Button    autoBidToggleButton;
    // mainScrollPane removed – page now fits without scrolling

    // ── Auto-Bid popup panel ─────────────────────────────────────────────────
    @FXML private VBox      autoBidPopup;     // floating popup panel
    @FXML private Label     autoBidStatusBadge;
    @FXML private Label     currentAutoBidLabel;
    @FXML private TextField autoMaxBidField;
    @FXML private TextField autoIncrementField;
    @FXML private Label     autoBidErrorLabel;
    @FXML private Button    registerAutoBidButton;

    // ── Chart tooltip ─────────────────────────────────────────────────────────
    @FXML private StackPane rootStackPane;

    // ── Live feed ─────────────────────────────────────────────────────────────
    @FXML private ListView<String> liveFeedList;

    // ── Price chart ───────────────────────────────────────────────────────────
    @FXML private LineChart<Number, Number> priceChart;
    @FXML private NumberAxis timeAxis;

    // ── Winner box ────────────────────────────────────────────────────────────
    @FXML private VBox  winnerBox;
    @FXML private Label winnerLabel;
    @FXML private Label winnerPriceLabel;


    // ── Internal state ────────────────────────────────────────────────────────
    private String  auctionId;
    private Auction currentAuction;
    private ScheduledExecutorService scheduler;
    private com.auction.util.AuctionChartHelper chartHelper;
    private com.auction.service.AuctionWebSocketService wsService;
    private volatile boolean wsConnected = false;
    private int knownBidCount = 0;

    /**
     * Tracks the last CSS class applied to statusBadge so we can skip
     * redundant removeAll()/add() calls.
     * PERF: Each styleClass mutation triggers CSS re-resolution for the node
     * and all its descendants, even if the resulting class list is identical.
     */
    private String currentStatusBadgeClass = "";

    // In-memory bid list (synced from server via WS)
    private final List<BidTransaction> wsKnownBids = new ArrayList<>();

    private static final DateTimeFormatter FMT      = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_SEC  = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ══════════════════════════════════════════════════════════════════════════
    // JavaFX UPDATE GUARDS
    // ══════════════════════════════════════════════════════════════════════════
    //
    // PERF: Every call to Label.setText() fires a StringProperty invalidation,
    // which marks the node's layout as dirty, schedules a layout pass on the
    // parent chain up to the Scene root, and queues a CSS re-application pass.
    // If the text hasn't actually changed, all of that work is wasted.
    //
    // Similarly, setVisible()/setManaged() each trigger property invalidations
    // and layout recalculations even when the boolean value is already correct.
    //
    // These guards eliminate the wasted invalidation cycles.
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sets label text only if it differs from the current value.
     * PERF: Avoids StringProperty invalidation → layout pass → CSS pass
     * when the text is already the desired value.
     */
    private static void setTextIfChanged(Labeled node, String newText) {
        if (node != null && !Objects.equals(node.getText(), newText)) {
            node.setText(newText);
        }
    }

    /**
     * Sets node visibility only if it differs from the current value.
     * PERF: Avoids BooleanProperty invalidation → parent layout recalculation
     * when visibility hasn't actually changed.
     */
    private static void setVisibleIfChanged(Node node, boolean visible) {
        if (node != null && node.isVisible() != visible) {
            node.setVisible(visible);
        }
    }

    /**
     * Sets node managed state only if it differs from the current value.
     * PERF: When managed changes, the parent must recompute its preferred size
     * and re-layout all children. Skipping no-ops avoids this entirely.
     */
    private static void setManagedIfChanged(Node node, boolean managed) {
        if (node != null && node.isManaged() != managed) {
            node.setManaged(managed);
        }
    }

    /**
     * Sets the disable state only if it differs from the current value.
     * PERF: Node.setDisable() triggers a pseudo-class state change (:disabled)
     * which forces CSS re-resolution on the node and its children.
     */
    private static void setDisableIfChanged(Node node, boolean disable) {
        if (node != null && node.isDisable() != disable) {
            node.setDisable(disable);
        }
    }

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
            // Full refresh on initial load – all sections need populating
            refreshAll();

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
                    // Full refresh after REST data arrives with complete bid history
                    refreshAll();
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

    public void initialize() {
        bidErrorLabel.setText("");
        chartHelper = new com.auction.util.AuctionChartHelper(priceChart, timeAxis);

        UnaryOperator<TextFormatter.Change> numericFilter = change ->
                change.getControlNewText().matches("[0-9,.]*") ? change : null;
        bidAmountField.setTextFormatter(new TextFormatter<>(numericFilter));

        if (autoMaxBidField != null) autoMaxBidField.setTextFormatter(new TextFormatter<>(numericFilter));
        if (autoIncrementField != null) autoIncrementField.setTextFormatter(new TextFormatter<>(numericFilter));

        // Close auto-bid popup when clicking outside it
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

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) scheduler.shutdown();
        if (wsService != null) wsService.disconnect();
    }

    /** Called by NavigationManager when leaving this screen. */
    public void cleanup() { shutdown(); }

    // ──────────────────────────────────────────────────────────────────────────
    // WebSocket
    // ──────────────────────────────────────────────────────────────────────────

    private void connectWebSocket() {
        wsService = new com.auction.service.AuctionWebSocketService(currentAuction != null ? currentAuction.getId() : null, this);
        wsService.connect();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WebSocket Listener Implementation
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void onWsConnected() {
        wsConnected = true;
        // PERF: On reconnect, only refresh status (to re-enable bid controls)
        // and balance (session balance may have changed while disconnected).
        // No need to refresh countdown (scheduler handles it) or bid section
        // (server will send FULL_SYNC for any missed bids).
        Platform.runLater(() -> {
            refreshStatusSection();
            refreshBalanceSection();
            refreshControls();
        });
        
        // Fetch auto-bid status so the label displays immediately
        User user = SessionManager.getInstance().getCurrentUser();
        if (user instanceof Bidder bidder && currentAuction != null) {
            JsonObject req = new JsonObject();
            req.addProperty("type", "CHECK_AUTO_BID");
            req.addProperty("auctionId", currentAuction.getId());
            req.addProperty("bidderId", bidder.getId());
            wsService.send(req.toString());
        }
    }

    @Override
    public void onWsDisconnected(String error) {
        wsConnected = false;
        // PERF: Only disable the bid controls and show error – no need to
        // re-render countdown, price, balance, or winner sections.
        setDisableIfChanged(placeBidButton, true);
        setDisableIfChanged(bidAmountField, true);
        setTextIfChanged(bidErrorLabel, "🔴 Mất kết nối server – không thể đặt giá.");
        // Refresh controls to reflect the disconnected state for auto-bid UI
        refreshControls();
    }

    @Override
    public void onWsError(String errorMsg) {
        String errMsg = "⚠ " + errorMsg;
        if (autoBidErrorLabel != null && !autoBidErrorLabel.getText().isEmpty()) {
            setTextIfChanged(autoBidErrorLabel, errMsg);
            autoBidErrorLabel.setStyle("-fx-text-fill: red;");
            setDisableIfChanged(registerAutoBidButton, false);
            setDisableIfChanged(autoMaxBidField, false);
            setDisableIfChanged(autoIncrementField, false);
        }
        setTextIfChanged(bidErrorLabel, errMsg);
        bidErrorLabel.setStyle("-fx-text-fill: red;");
        setDisableIfChanged(placeBidButton, false);
        setDisableIfChanged(bidAmountField, false);
    }

    /** New bid placed on server – update price, feed, chart. */
    @Override
    public void onBidUpdate(JsonObject json) {
        String aid           = json.has("auctionId")      ? json.get("auctionId").getAsString()      : null;
        double amount        = json.get("amount").getAsDouble();
        String bidderName    = json.get("bidderUsername").getAsString();
        String bidderId      = json.has("bidderId")       ? json.get("bidderId").getAsString()       : "remote";
        String timeStr       = json.has("time")           ? json.get("time").getAsString()           : TimeSyncManager.getNow().toString();

        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;

        // O(1) cache update
        if (aid != null) HotItemCache.getInstance().recordBid(aid);
        else if (currentAuction != null) HotItemCache.getInstance().recordBid(currentAuction.getId());

        LocalDateTime ts = LocalDateTime.parse(timeStr);
        if (com.auction.util.ServerConfig.isRemote()) {
            ts = ts.plusHours(7); // Convert UTC (Render) to Vietnam time (GMT+7)
        }

        if (currentAuction != null) {
            currentAuction.setHighestBid(amount);
            Bidder dummy = new Bidder(bidderId, TimeSyncManager.getNow(), bidderName, "", 0);
            BidTransaction dummyBid = new BidTransaction(
                    java.util.UUID.randomUUID().toString(),
                    ts, dummy, currentAuction, amount);
            currentAuction.injectBid(dummyBid);
        }

        String timeDisplay = ts.format(TIME_FMT);
        appendToFeed(String.format("[%s]  %s  →  %,.0f ₫", timeDisplay, bidderName, amount));
        chartHelper.addRawBid(amount, ts);

        setTextIfChanged(bidErrorLabel, "");

        // PERF: A new bid only affects the price/count/hint labels and the
        // bid controls (e.g. highest-bidder detection). No need to touch
        // countdown, balance, status badge, or winner section.
        refreshBidSection();
        refreshControls();
    }

    @Override
    public void onAutoBidLog(JsonObject json) {
        String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;
        
        String msg = json.get("message").getAsString();
        String timeDisplay = TimeSyncManager.getNow().format(TIME_FMT);
        appendToFeed(String.format("[%s] ⚡ %s", timeDisplay, msg));
    }
    
    @Override
    public void onAutoBidAck(JsonObject json) {
        String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;
        
        if (autoBidErrorLabel != null) {
            setTextIfChanged(autoBidErrorLabel, "✅ Đăng ký thành công.");
            autoBidErrorLabel.setStyle("-fx-text-fill: #81c784;");
        }
        if (autoMaxBidField != null) autoMaxBidField.clear();
        if (autoIncrementField != null) autoIncrementField.clear();
        
        if (autoBidStatusBadge != null) {
            setTextIfChanged(autoBidStatusBadge, "Đang Hoạt Động");
            autoBidStatusBadge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;");
            setTextIfChanged(registerAutoBidButton, "Cập nhật Auto-Bid");
        }
        
        // Fetch new status to update the label and fields
        User user = SessionManager.getInstance().getCurrentUser();
        if (user instanceof Bidder bidder && wsConnected && wsService != null) {
            JsonObject req = new JsonObject();
            req.addProperty("type", "CHECK_AUTO_BID");
            req.addProperty("auctionId", aid != null ? aid : currentAuction.getId());
            req.addProperty("bidderId", bidder.getId());
            wsService.send(req.toString());
        }
        
        // PERF: Auto-bid acknowledgement only needs to refresh controls
        // (to re-enable the auto-bid form). Other sections are unaffected.
        refreshControls();
    }

    @Override
    public void onAutoBidStatus(JsonObject json) {
        String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;
        
        double maxBid = json.get("maxBid").getAsDouble();
        double increment = json.get("increment").getAsDouble();
        
        Platform.runLater(() -> {
            if (autoMaxBidField != null) autoMaxBidField.setText(String.format("%.0f", maxBid));
            if (autoIncrementField != null) autoIncrementField.setText(String.format("%.0f", increment));
            if (autoBidStatusBadge != null) {
                setTextIfChanged(autoBidStatusBadge, "Đang Hoạt Động");
                autoBidStatusBadge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;");
                if (registerAutoBidButton != null) setTextIfChanged(registerAutoBidButton, "Cập nhật Auto-Bid");
            }
            if (currentAutoBidLabel != null) {
                currentAutoBidLabel.setText(String.format("Auto-Bid của bạn: %,.0f ₫ (Bước giá: %,.0f ₫)", maxBid, increment));
                currentAutoBidLabel.setVisible(true);
                currentAutoBidLabel.setManaged(true);
            }
        });
    }
    
    @Override
    public void onAutoBidDeactivated(JsonObject json) {
        String aid = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;
        
        String bidderId = json.has("bidderId") ? json.get("bidderId").getAsString() : null;
        User user = SessionManager.getInstance().getCurrentUser();
        
        // Only update UI if the deactivated auto-bid belongs to the current user
        if (user != null && user.getId().equals(bidderId)) {
            Platform.runLater(() -> {
                if (autoBidStatusBadge != null) {
                    setTextIfChanged(autoBidStatusBadge, "Đã Dừng");
                    autoBidStatusBadge.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626;");
                }
                if (autoBidErrorLabel != null) {
                    setTextIfChanged(autoBidErrorLabel, "⚠️ Auto-Bid đã dừng: Có người đặt giá cao hơn giới hạn tối đa của bạn.");
                    autoBidErrorLabel.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                }
                if (currentAutoBidLabel != null) {
                    currentAutoBidLabel.setVisible(false);
                    currentAutoBidLabel.setManaged(false);
                }
            });
        }
    }

    @Override
    public void onStatusChanged(JsonObject json) {
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

        // PERF: Status change affects the badge, controls (enable/disable bid
        // based on new status), and winner section (if CLOSED). The countdown
        // will pick up the new status on its next scheduler tick. Balance is
        // unaffected by status changes. Bid section is refreshed only if the
        // status payload included a highestBid update.
        refreshStatusSection();
        refreshWinnerSection();
        refreshControls();
        if (highestBid >= 0) {
            refreshBidSection();
        }
        System.out.println("[AuctionDetail] Status → " + newStatusStr);

        // Hiển thị thông báo người chiến thắng khi phiên CLOSED
        if ("CLOSED".equals(newStatusStr) && !AuctionStatus.CLOSED.equals(previousStatus)) {
            String winnerUsername = json.has("winnerUsername") ? json.get("winnerUsername").getAsString() : null;
            double winnerBid      = json.has("winnerBid")      ? json.get("winnerBid").getAsDouble()      : -1;
            showWinnerAnnouncement(winnerUsername, winnerBid);
        }
    }

    @Override
    public void onBalanceUpdate(JsonObject json) {
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
            // PERF: Balance update only affects the balance/frozen labels.
            // No need to re-render countdown, price, status, winner, or controls.
            refreshBalanceSection();
        }
    }

    @Override
    public void onFullSync(JsonObject json) {
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

        // FULL_SYNC is a complete server state snapshot – refresh everything.
        refreshAll();
        // Rebuild chart/feed fully after sync
        rebuildChartAndFeed();
    }

    private void rebuildChartAndFeed() {
        chartHelper.clear();
        liveFeedList.getItems().clear();
        List<BidTransaction> history = currentAuction.getBidHistory();
        for (BidTransaction bt : history) {
            chartHelper.addBid(bt);
            addBidToFeed(bt);
        }
    }

    @Override
    public void onLegacyBidUpdate(JsonObject json) {
        String aid        = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        double amount     = json.get("amount").getAsDouble();
        String bidderName = json.get("bidder").getAsString();
        String timeStr    = json.has("time") ? json.get("time").getAsString() : TimeSyncManager.getNow().toString();

        if (aid != null && currentAuction != null && !aid.equals(currentAuction.getId())) return;
        if (currentAuction != null) {
            currentAuction.setHighestBid(amount);
            Bidder dummy = new Bidder("remote", TimeSyncManager.getNow(), bidderName, "", 0);
            BidTransaction dummyBid = new BidTransaction(
                    java.util.UUID.randomUUID().toString(),
                    TimeSyncManager.getNow(), dummy, currentAuction, amount);
            currentAuction.injectBid(dummyBid);
        }
        // PERF: Legacy bid update – same targeted refresh as modern onBidUpdate.
        refreshBidSection();
        refreshControls();
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
        // PERF: The scheduler now calls ONLY refreshCountdown() instead of the
        // old refreshLivePanel(). This is the single most impactful optimization:
        //
        // Before: Every 2 seconds, the scheduler updated ~20 UI properties
        //         (price, bid count, badge, balance, controls, winner box, etc.)
        //         even though only the countdown timer text actually changes.
        //
        // After:  Every 2 seconds, the scheduler updates at most 2 labels
        //         (timeRemainingLabel, lastUpdateLabel) via setTextIfChanged,
        //         meaning zero JavaFX invalidations if the text happens to be
        //         the same (e.g. "Hết giờ" stays stable after auction ends).
        //
        // This reduces the FX Application Thread work per tick from ~20 property
        // sets + layout + CSS to at most 2 property sets + minimal layout.
        scheduler.scheduleAtFixedRate(
                () -> Platform.runLater(this::refreshCountdown), 0, 2, TimeUnit.SECONDS);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Setup
    // ──────────────────────────────────────────────────────────────────────────


    // Setup removed; delegated to AuctionChartHelper

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
            chartHelper.addBid(bid);
            addBidToFeed(bid);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TARGETED REFRESH METHODS
    // ══════════════════════════════════════════════════════════════════════════
    //
    // These replace the old monolithic refreshLivePanel(). Each method updates
    // only the minimal set of JavaFX nodes relevant to a specific concern.
    //
    // PERF RATIONALE:
    // JavaFX uses an invalidation-based rendering pipeline. Each property
    // change (setText, setVisible, setManaged, setDisable, styleClass mutation)
    // marks the node's layout as dirty and schedules a pulse. During the pulse,
    // JavaFX walks the dirty-node tree to recompute sizes and positions.
    //
    // By splitting into targeted methods, each event source only dirties the
    // nodes it actually affects, dramatically reducing the dirty-node set and
    // the work done per pulse.
    // ══════════════════════════════════════════════════════════════════════════

    private void loadAutoBidState() {
        // Will now be loaded asynchronously via WS on connection
    }

    /**
     * Refreshes ONLY the countdown timer and sync timestamp.
     * Called by: scheduler (every 2 seconds).
     *
     * PERF: This is the highest-frequency caller (every 2s). By restricting it
     * to just 2 labels, we reduce the per-tick FX thread work from ~20 property
     * mutations to at most 2. The setTextIfChanged guard further eliminates
     * invalidations when the text hasn't changed (e.g. stable "Hết giờ" or
     * "⏳ Chờ Admin duyệt" states).
     */
    private void refreshCountdown() {
        if (currentAuction == null) return;

        // Sync timestamp
        setTextIfChanged(lastUpdateLabel, "Đồng bộ : " + TimeSyncManager.getNow().format(TIME_FMT));

        // Countdown timer – compute the display string based on current status
        AuctionStatus status = currentAuction.getStatus();
        String countdownText = switch (status) {
            case PENDING  -> "⏳ Chờ Admin duyệt";
            case OPEN     -> "🟢 Chờ Admin bắt đầu";
            case CLOSED, CANCELED -> {
                // Shut down the scheduler – no more countdown updates needed
                if (scheduler != null && !scheduler.isShutdown()) scheduler.shutdown();
                yield "Đã kết thúc";
            }
            case RUNNING -> {
                Duration remaining = Duration.between(TimeSyncManager.getNow(), currentAuction.getEndTime());
                yield remaining.isNegative() ? "Hết giờ" :
                        String.format("%02d:%02d:%02d",
                                remaining.toHours(), remaining.toMinutesPart(), remaining.toSecondsPart());
            }
        };
        setTextIfChanged(timeRemainingLabel, countdownText);
    }

    /**
     * Refreshes ONLY the bid-related labels: current price, bid count, min bid hint.
     * Called by: onBidUpdate, onLegacyBidUpdate, onFullSync, onStatusChanged (with price update).
     *
     * PERF: These 3 labels change only when a new bid arrives. The scheduler
     * no longer touches them, eliminating ~3 wasted setText() calls per 2-second
     * tick (= ~3 layout invalidations avoided per tick).
     */
    private void refreshBidSection() {
        if (currentAuction == null) return;

        setTextIfChanged(currentPriceLabel, String.format("%,.0f ₫", currentAuction.getHighestBid()));
        setTextIfChanged(bidCountLabel, currentAuction.getBidHistory().size() + " lượt đấu giá");
        setTextIfChanged(minBidHint, "Tối thiểu: " + String.format("%,.0f ₫", currentAuction.getHighestBid() + 1));

        // Track known bid count for chart/feed dedup
        List<BidTransaction> history = currentAuction.getBidHistory();
        if (history.size() > knownBidCount) {
            knownBidCount = history.size();
        }
    }

    /**
     * Refreshes ONLY the balance and frozen-balance labels.
     * Called by: onBalanceUpdate, onWsConnected.
     *
     * PERF: Balance changes are infrequent (only after a bid is placed or
     * refunded). By isolating this, the frequent scheduler ticks no longer
     * re-render balance labels, saving ~4 property mutations per tick.
     */
    private void refreshBalanceSection() {
        if (currentAuction == null) return;
        if (balanceLabel == null) return;

        User user = SessionManager.getInstance().getCurrentUser();
        if (user instanceof Bidder bidder) {
            double available = bidder.getAvailableBalance();
            double frozen    = bidder.getFrozenBalance();
            setTextIfChanged(balanceLabel, String.format("Khả dụng: %,.0f ₫", available));
            setVisibleIfChanged(balanceLabel, true);

            if (frozenLabel != null) {
                if (frozen > 0) {
                    setTextIfChanged(frozenLabel, String.format("Đóng băng: %,.0f ₫", frozen));
                    setVisibleIfChanged(frozenLabel, true);
                    setManagedIfChanged(frozenLabel, true);
                } else {
                    setVisibleIfChanged(frozenLabel, false);
                    setManagedIfChanged(frozenLabel, false);
                }
            }
        } else {
            setVisibleIfChanged(balanceLabel, false);
            if (frozenLabel != null) {
                setVisibleIfChanged(frozenLabel, false);
                setManagedIfChanged(frozenLabel, false);
            }
        }
    }

    /**
     * Refreshes ONLY the status badge and start time label.
     * Called by: onStatusChanged, onWsConnected.
     *
     * PERF: Status changes are rare (typically 3-4 times per auction lifecycle).
     * The badge CSS class guard prevents removeAll()/add() when the status
     * hasn't changed, avoiding a full CSS re-resolution pass on the badge node.
     */
    private void refreshStatusSection() {
        if (currentAuction == null) return;

        // Status badge (with CSS class guard)
        updateStatusBadge();

        // Start time
        LocalDateTime st = currentAuction.getStartTime();
        setTextIfChanged(startTimeLabel, st != null ? st.format(FMT) : "Chưa bắt đầu");
    }

    /**
     * Refreshes ONLY the winner box visibility and labels.
     * Called by: onStatusChanged.
     *
     * PERF: Winner box is relevant only in CLOSED state. By isolating it,
     * bid updates and scheduler ticks don't waste time evaluating winner
     * visibility on every cycle.
     */
    private void refreshWinnerSection() {
        if (currentAuction == null) return;

        AuctionStatus status = currentAuction.getStatus();
        if (status == AuctionStatus.CLOSED) {
            BidTransaction winner = currentAuction.getWinner();
            if (winner != null) {
                setVisibleIfChanged(winnerBox, true);
                setManagedIfChanged(winnerBox, true);
                setTextIfChanged(winnerLabel, "Người thắng: " + winner.getBidder().getUsername());
                setTextIfChanged(winnerPriceLabel, String.format("Giá chốt: %,.0f ₫", winner.getAmount()));
            }
        } else {
            setVisibleIfChanged(winnerBox, false);
            setManagedIfChanged(winnerBox, false);
        }
    }

    /**
     * Refreshes ONLY the bid controls: place-bid button, bid input field,
     * auto-bid toggle, auto-bid form, seller warning, and highest-bidder message.
     * Called by: onBidUpdate, onStatusChanged, onWsConnected, onAutoBidAck.
     *
     * PERF: Control state depends on multiple inputs (status, user role, WS
     * connection, highest-bidder check). This method is called from several
     * event sources, but each invocation is lightweight – it only sets boolean
     * disable/visible properties, and the guards skip no-ops.
     */
    private void refreshControls() {
        if (currentAuction == null) return;

        AuctionStatus status = currentAuction.getStatus();
        User user = SessionManager.getInstance().getCurrentUser();
        boolean isExpired = currentAuction.getEndTime() != null && TimeSyncManager.getNow().isAfter(currentAuction.getEndTime());
        boolean canBid = status == AuctionStatus.RUNNING && !isExpired && user instanceof Bidder && wsConnected;

        if (sellerWarningLabel != null) {
            boolean isSeller = user instanceof Seller;
            setVisibleIfChanged(sellerWarningLabel, isSeller);
            setManagedIfChanged(sellerWarningLabel, isSeller);
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

        setDisableIfChanged(placeBidButton, !canBid);
        setDisableIfChanged(bidAmountField, !canBid);

        // Auto-bid toggle button: always visible for layout stability; disabled when not eligible
        if (autoBidToggleButton != null) {
            setDisableIfChanged(autoBidToggleButton, !canAutoBid);
            setVisibleIfChanged(autoBidToggleButton, true);   // always visible – keeps HBox layout stable
            setManagedIfChanged(autoBidToggleButton, true);
            // Close the popup if auto-bid is no longer available (e.g. auction closed)
            if (!canAutoBid && autoBidPopup != null && autoBidPopup.isVisible()) {
                autoBidPopup.setVisible(false);
                autoBidPopup.setManaged(false);
            }
        }

        if (registerAutoBidButton != null) {
            setDisableIfChanged(registerAutoBidButton, !canAutoBid);
            setDisableIfChanged(autoMaxBidField, !canAutoBid);
            setDisableIfChanged(autoIncrementField, !canAutoBid);

            if (autoBidErrorLabel != null) {
                String abCur = autoBidErrorLabel.getText();
                if (abCur.contains("Đang gửi")) {
                    setTextIfChanged(autoBidErrorLabel, "");
                    autoBidErrorLabel.setStyle("");
                }
            }
        }

        if (isHighestBidder) {
            setTextIfChanged(bidErrorLabel, "🏆 Bạn đang giữ giá cao nhất. Chờ người khác đặt giá cao hơn.");
            bidErrorLabel.setStyle("-fx-text-fill: #e5a93c; -fx-font-weight: bold;");
        } else {
            // Reset về default nếu đang hiển thị thông báo highest-bidder hoặc pending
            String cur = bidErrorLabel.getText();
            if (cur.contains("giữ giá cao nhất") || cur.contains("Đang gửi")) {
                setTextIfChanged(bidErrorLabel, "");
                bidErrorLabel.setStyle(""); // Reset màu về mặc định
            }
        }
    }

    /**
     * Full refresh – calls all 6 targeted methods.
     * Used ONLY on initial data load and FULL_SYNC, where every UI section
     * needs to be populated from scratch.
     *
     * PERF: Even in the full-refresh case, the setTextIfChanged/setVisibleIfChanged
     * guards still protect against redundant invalidations when the underlying
     * data hasn't changed (e.g. a FULL_SYNC that confirms the current state).
     */
    private void refreshAll() {
        if (currentAuction == null) return;
        refreshCountdown();
        refreshBidSection();
        refreshBalanceSection();
        refreshStatusSection();
        refreshWinnerSection();
        refreshControls();
    }

    /**
     * Updates the status badge text and CSS class.
     *
     * PERF: The old code unconditionally called removeAll() + add() on every
     * refresh, which triggers CSS re-resolution even if the status hasn't
     * changed. This version tracks the current class and skips the mutation
     * when it's already correct.
     *
     * ObservableList.removeAll() on styleClass creates a new backing array,
     * fires ListChangeListener events, and forces the CSS subsystem to
     * re-resolve all matching selectors for this node. By guarding with
     * currentStatusBadgeClass, we avoid this entirely for the common case.
     */
    private void updateStatusBadge() {
        AuctionStatus status = currentAuction.getStatus();
        setTextIfChanged(statusBadge, currentAuction.getStatusDisplay());
        String cls = switch (status) {
            case PENDING  -> "badge-pending";
            case OPEN     -> "badge-open";
            case RUNNING  -> "badge-running";
            case CLOSED   -> "badge-closed";
            case CANCELED -> "badge-canceled";
        };
        // PERF: Only mutate the styleClass list if the badge class actually changed.
        // Each removeAll()/add() triggers CSS re-resolution for this node.
        if (!cls.equals(currentStatusBadgeClass)) {
            statusBadge.getStyleClass().removeAll(
                    "badge-pending", "badge-open", "badge-running", "badge-closed", "badge-canceled");
            statusBadge.getStyleClass().add(cls);
            currentStatusBadgeClass = cls;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Chart / Feed helpers
    // ──────────────────────────────────────────────────────────────────────────


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
            return;
        }

        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Bidder bidder)) {
            bidErrorLabel.setText("Chỉ Bidder mới có thể đặt giá."); return;
        }

        if (!wsConnected || wsService == null) {
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
        wsService.send(req.toString());
        bidAmountField.clear();

        // Disable immediately to prevent double-click while waiting for server
        placeBidButton.setDisable(true);
        bidAmountField.setDisable(true);
        bidErrorLabel.setText("⏳ Đang gửi giá đặt đến server...");
        bidErrorLabel.setStyle("-fx-text-fill: #64b5f6;");
    }

    /** Toggle the floating auto-bid popup panel. */
    @FXML
    private void handleToggleAutoBid(ActionEvent event) {
        if (autoBidPopup == null) return;
        boolean nowVisible = !autoBidPopup.isVisible();
        autoBidPopup.setVisible(nowVisible);
        autoBidPopup.setManaged(nowVisible);
        // Position popup in the center of the StackPane
        if (nowVisible) {
            autoBidPopup.setTranslateX(0);
            autoBidPopup.setTranslateY(0);
            StackPane.setAlignment(autoBidPopup, javafx.geometry.Pos.CENTER);

            // Fetch current auto-bid status
            User user = SessionManager.getInstance().getCurrentUser();
            if (user instanceof Bidder bidder && wsConnected && wsService != null) {
                JsonObject req = new JsonObject();
                req.addProperty("type", "CHECK_AUTO_BID");
                req.addProperty("auctionId", currentAuction.getId());
                req.addProperty("bidderId", bidder.getId());
                wsService.send(req.toString());
            }
        }
    }

    /** Close the auto-bid popup via the ✕ button in its header. */
    @FXML
    private void handleCloseAutoBidPopup(ActionEvent event) {
        if (autoBidPopup == null) return;
        autoBidPopup.setVisible(false);
        autoBidPopup.setManaged(false);
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

        if (!wsConnected || wsService == null) {
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
        wsService.send(req.toString());

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
