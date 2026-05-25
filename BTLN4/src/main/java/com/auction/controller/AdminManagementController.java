package com.auction.controller;

import com.auction.client.AuctionClient;
import com.auction.model.*;
import com.auction.service.AppFacade;
import com.auction.util.SessionManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AdminManagementController – full system oversight.
 *
 * Connects to WebSocket server on init:
 *  – Sends ADMIN_ACTION (approve/start/finish/cancel) via WS so the server
 *    persists the change and broadcasts to ALL connected clients.
 *  – Listens for AUCTION_CREATED → auto-adds new auctions to the table.
 *  – Listens for AUCTION_STATUS_CHANGED → refreshes the auction table.
 *  – Listens for FULL_SYNC → loads all auctions from server snapshot.
 *
 * Local DB is used only as fallback when WS is unavailable.
 */
public class AdminManagementController {

    // ── User tab ──────────────────────────────────────────────────────────────
    @FXML private TextField        userSearchField;
    @FXML private ComboBox<String> roleFilter;
    @FXML private Label            userCountLabel;
    @FXML private TableView<User>  userTable;
    @FXML private TableColumn<User, String> colUserId;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, String> colUserCreated;

    // ── Auction tab ───────────────────────────────────────────────────────────
    @FXML private TextField           auctionSearchField;
    @FXML private ComboBox<String>    auctionStatusFilter;
    @FXML private Label               auctionCountLabel;
    @FXML private TableView<Auction>  allAuctionTable;
    @FXML private TableColumn<Auction, String> colAuctionName;
    @FXML private TableColumn<Auction, String> colAuctionSeller;
    @FXML private TableColumn<Auction, String> colAuctionStatus;
    @FXML private TableColumn<Auction, String> colAuctionPrice;
    @FXML private TableColumn<Auction, String> colAuctionEnd;

    @FXML private Button btnApprove;
    @FXML private Button btnStart;
    @FXML private Button btnFinish;
    @FXML private Button btnCancel;

    // ── Stats tab ─────────────────────────────────────────────────────────────
    @FXML private Label statTotalUsers;
    @FXML private Label statTotalAuctions;
    @FXML private Label statPending;
    @FXML private Label statOpen;
    @FXML private Label statRunning;
    @FXML private Label statFinished;
    @FXML private Label statBidders;
    @FXML private Label statSellers;
    @FXML private Label statCanceled;

    private final AppFacade app = AppFacade.getInstance();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // WebSocket for real-time sync
    private AuctionClient wsClient;
    private volatile boolean wsConnected = false;
    private final Gson gson = new Gson();

    // In-memory auction list driven by WS broadcasts (and REST on load)
    private final ObservableList<Auction> auctionList = FXCollections.observableArrayList();
    // Cached user list (loaded once; refreshed via handleRefreshUsers)
    private final ObservableList<User> userList = FXCollections.observableArrayList();
    private final Map<String, Integer> auctionBidCounts = new ConcurrentHashMap<>();

    @FXML
    public void initialize() {
        setupFilters();
        setupUserTableColumns();
        setupAuctionTableColumns();
        loadUsers();               // async REST load
        loadAuctionsFromServer();  // async REST load; overridden by FULL_SYNC once WS connects
        disableAuctionButtons();

        allAuctionTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, nw) -> updateAuctionButtons(nw));

        // Connect WebSocket – broadcasts drive all real-time auction table updates
        connectWebSocket();
    }

    // ── WebSocket ─────────────────────────────────────────────────────────────

    private void connectWebSocket() {
        wsClient = new AuctionClient();
        Thread t = new Thread(() -> {
            wsClient.connect(
                    msg -> Platform.runLater(() -> handleWsMessage(msg)),
                    err -> Platform.runLater(() -> {
                        wsConnected = false;
                        System.err.println("[AdminMgmt] WS error: " + err);
                        // REST already loaded data; WS failure just means no real-time push
                    }),
                    () -> {
                        // Connected: request full state from server (overrides REST-loaded data)
                        wsConnected = true;
                        sendRequestSync();
                        System.out.println("[AdminMgmt] WS connected, sent REQUEST_SYNC.");
                    }
            );
        }, "Admin-WS");
        t.setDaemon(true);
        t.start();
    }

    private void sendRequestSync() {
        if (wsClient == null || !wsClient.isConnected()) return;
        JsonObject req = new JsonObject();
        req.addProperty("type", "REQUEST_SYNC");
        wsClient.send(req.toString());
    }

    private void handleWsMessage(String msg) {
        try {
            com.google.gson.JsonElement element = gson.fromJson(msg, com.google.gson.JsonElement.class);
            if (!element.isJsonObject()) return;
            JsonObject json = element.getAsJsonObject();
            if (json.has("error")) {
                showAlert(Alert.AlertType.ERROR, "Lỗi Server", json.get("error").getAsString());
                return;
            }
            String type = json.has("type") ? json.get("type").getAsString() : "";
            switch (type) {
                case "FULL_SYNC"              -> onFullSync(json);
                case "AUCTION_CREATED"        -> onAuctionCreated(json);
                case "AUCTION_STATUS_CHANGED" -> onStatusChanged(json);
                case "BID_UPDATE"             -> onBidUpdate(json);
                // BALANCE_UPDATE is targeted to bidders – admin ignores it
            }
        } catch (Exception e) {
            System.err.println("[AdminMgmt] WS parse error: " + e.getMessage());
        }
    }

    /** Server sent all auctions. Replace local list. */
    private void onFullSync(JsonObject json) {
        if (!json.has("auctions")) return;
        JsonArray arr = json.get("auctions").getAsJsonArray();
        List<Auction> synced = new ArrayList<>();
        auctionBidCounts.clear();
        for (int i = 0; i < arr.size(); i++) {
            buildAuctionFromJson(arr.get(i).getAsJsonObject()).ifPresent(synced::add);
        }
        auctionList.setAll(synced);
        allAuctionTable.setItems(auctionList);
        auctionCountLabel.setText("Tổng: " + auctionList.size() + " phiên");
        updateAuctionButtons(allAuctionTable.getSelectionModel().getSelectedItem());
        refreshStats();
        System.out.println("[AdminMgmt] FULL_SYNC received: " + synced.size() + " auctions.");
    }

    /** A seller just created a new auction. */
    private void onAuctionCreated(JsonObject json) {
        buildAuctionFromJson(json).ifPresent(a -> {
            auctionList.add(0, a);
            auctionCountLabel.setText("Tổng: " + auctionList.size() + " phiên");
            updateAuctionButtons(allAuctionTable.getSelectionModel().getSelectedItem());
            refreshStats();
            System.out.println("[AdminMgmt] New auction: " + a.getItem().getName());
        });
    }

    /** An auction's status changed (admin action broadcast). */
    private void onStatusChanged(JsonObject json) {
        String auctionId    = json.get("auctionId").getAsString();
        String newStatusStr = json.get("newStatus").getAsString();
        double highestBid   = json.has("highestBid") ? json.get("highestBid").getAsDouble() : -1;
        String startTimeStr = json.has("startTime")  ? json.get("startTime").getAsString()  : "";

        AuctionStatus newStatus;
        try { newStatus = AuctionStatus.valueOf(newStatusStr); }
        catch (IllegalArgumentException e) { return; }

        // Patch the in-memory auction directly from WS data.
        // DO NOT reload from local DB – local DB is stale on remote machines.
        for (int i = 0; i < auctionList.size(); i++) {
            Auction a = auctionList.get(i);
            if (a.getId().equals(auctionId)) {
                a.setStatus(newStatus);
                if (highestBid >= 0) a.setHighestBid(highestBid);
                if (!startTimeStr.isEmpty()) {
                    try { a.setStartTime(LocalDateTime.parse(startTimeStr)); } catch (Exception ignored) {}
                }
                break;
            }
        }
        allAuctionTable.refresh();
        updateAuctionButtons(allAuctionTable.getSelectionModel().getSelectedItem());
        refreshStats();
    }

    /** A new bid was placed — update bid count & highest price in the table row. */
    private void onBidUpdate(JsonObject json) {
        String auctionId = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        double amount    = json.get("amount").getAsDouble();
        if (auctionId == null) return;
        auctionBidCounts.merge(auctionId, 1, Integer::sum);

        for (Auction a : auctionList) {
            if (a.getId().equals(auctionId)) {
                a.setHighestBid(amount);
                if (json.has("endTime")) {
                    try {
                        a.setEndTime(LocalDateTime.parse(json.get("endTime").getAsString()));
                    } catch (Exception ignored) {}
                }
                // Inject a bid entry so bid count updates
                String bidderName = json.has("bidderUsername") ? json.get("bidderUsername").getAsString() : "?";
                String bidderId   = json.has("bidderId")       ? json.get("bidderId").getAsString()       : "remote";
                String timeStr    = json.has("time")           ? json.get("time").getAsString()           : com.auction.util.TimeSyncManager.getNow().toString();
                Bidder dummy = new Bidder(bidderId, com.auction.util.TimeSyncManager.getNow(), bidderName, "", 0);
                a.injectBid(new BidTransaction(
                        java.util.UUID.randomUUID().toString(),
                        LocalDateTime.parse(timeStr), dummy, a, amount));
                break;
            }
        }
        allAuctionTable.refresh();
        updateAuctionButtons(allAuctionTable.getSelectionModel().getSelectedItem());
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void setupFilters() {
        roleFilter.setItems(FXCollections.observableArrayList("Tất cả", "Bidder", "Seller", "Admin"));
        roleFilter.getSelectionModel().selectFirst();
        auctionStatusFilter.setItems(FXCollections.observableArrayList(
                "Tất cả", "Chờ duyệt", "Chờ bắt đầu", "Đang diễn ra", "Đã đóng", "Đã huỷ"));
        auctionStatusFilter.getSelectionModel().selectFirst();
    }

    private void setupUserTableColumns() {
        colUserId.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getId().substring(0, Math.min(8, c.getValue().getId().length())) + "..."));
        colUsername.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        colRole.setCellValueFactory(c     -> new SimpleStringProperty(c.getValue().getRole()));
        colUserCreated.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCreatedAt().format(FMT)));
    }

    private void setupAuctionTableColumns() {
        colAuctionName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getItem().getName()));
        colAuctionSeller.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getSeller().getUsername()));
        colAuctionStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatusDisplay()));
        colAuctionPrice.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("%,.0f ₫", c.getValue().getHighestBid())));
        colAuctionEnd.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEndTime().format(FMT)));
    }

    private int getBidCount(Auction auction) {
        if (auction == null) return 0;
        return auctionBidCounts.getOrDefault(auction.getId(), auction.getBidHistory().size());
    }

    // ── Button state ─────────────────────────────────────────────────────────

    private void disableAuctionButtons() {
        btnApprove.setDisable(true);
        btnStart.setDisable(true);
        btnFinish.setDisable(true);
        btnCancel.setDisable(true);
    }

    private void updateAuctionButtons(Auction a) {
        if (a == null) { disableAuctionButtons(); return; }
        AuctionStatus s = a.getStatus();
        btnApprove.setDisable(s != AuctionStatus.PENDING);
        btnStart.setDisable(s != AuctionStatus.OPEN);
        btnFinish.setDisable(s != AuctionStatus.RUNNING);
        btnCancel.setDisable(s == AuctionStatus.CLOSED || s == AuctionStatus.CANCELED);
    }

    // ── Data loading ─────────────────────────────────────────────────────────

    /** Async: load all users from server REST API. */
    private void loadUsers() {
        if (userCountLabel != null) userCountLabel.setText("Đang tải...");
        Task<List<User>> task = new Task<>() {
            @Override protected List<User> call() { return app.getAllUsers(); }
        };
        task.setOnSucceeded(e -> {
            List<User> users = task.getValue();
            userList.setAll(users);
            userTable.setItems(userList);
            if (userCountLabel != null)
                userCountLabel.setText("Tổng: " + users.size() + " người dùng");
            refreshStats();
        });
        task.setOnFailed(e -> Platform.runLater(() -> {
            if (userCountLabel != null) userCountLabel.setText("Lỗi tải người dùng");
        }));
        new Thread(task, "admin-users").start();
    }

    /** Async: initial load of all auctions from server REST API. */
    private void loadAuctionsFromServer() {
        if (auctionCountLabel != null) auctionCountLabel.setText("Đang tải...");
        Task<List<Auction>> task = new Task<>() {
            @Override protected List<Auction> call() { return app.getAllAuctions(); }
        };
        task.setOnSucceeded(e -> {
            auctionList.setAll(task.getValue());
            auctionBidCounts.clear();
            for (Auction a : auctionList) {
                auctionBidCounts.put(a.getId(), a.getBidHistory().size());
            }
            allAuctionTable.setItems(auctionList);
            if (auctionCountLabel != null)
                auctionCountLabel.setText("Tổng: " + auctionList.size() + " phiên");
            allAuctionTable.getSelectionModel().clearSelection();
            disableAuctionButtons();
            refreshStats();
        });
        task.setOnFailed(e -> Platform.runLater(() -> {
            if (auctionCountLabel != null) auctionCountLabel.setText("Lỗi tải phiên đấu giá");
        }));
        new Thread(task, "admin-auctions").start();
    }

    // ── User tab actions ──────────────────────────────────────────────────────

    @FXML private void handleUserSearch(ActionEvent event) {
        String keyword = userSearchField.getText().trim().toLowerCase();
        String role    = roleFilter.getValue();
        // Filter the cached in-memory list (no server call needed)
        List<User> filtered = userList.stream()
                .filter(u -> {
                    boolean matchName = keyword.isEmpty() || u.getUsername().toLowerCase().contains(keyword);
                    boolean matchRole = role == null || role.equals("Tất cả") || u.getRole().equals(role);
                    return matchName && matchRole;
                }).collect(Collectors.toList());
        userTable.setItems(FXCollections.observableArrayList(filtered));
        if (userCountLabel != null)
            userCountLabel.setText("Kết quả: " + filtered.size() + " người dùng");
    }

    @FXML private void handleDeleteUser(ActionEvent event) {
        User sel = userTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showAlert(Alert.AlertType.WARNING, "Chưa chọn", "Vui lòng chọn người dùng."); return; }
        if (sel instanceof Admin) { showAlert(Alert.AlertType.ERROR, "Không thể xoá", "Không thể xoá tài khoản Admin."); return; }
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
                "Xoá người dùng \"" + sel.getUsername() + "\"?", ButtonType.YES, ButtonType.NO);
        c.setTitle("Xác nhận xoá");
        c.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                Task<Boolean> task = new Task<>() {
                    @Override protected Boolean call() { return app.deleteUser(sel.getId()); }
                };
                task.setOnSucceeded(e -> { loadUsers(); });
                task.setOnFailed(e -> Platform.runLater(() ->
                        showAlert(Alert.AlertType.ERROR, "Lỗi", task.getException().getMessage())));
                new Thread(task, "delete-user").start();
            }
        });
    }

    @FXML private void handleRefreshUsers(ActionEvent event) {
        userSearchField.clear(); roleFilter.getSelectionModel().selectFirst(); loadUsers();
    }

    // ── Auction tab actions (via WebSocket) ───────────────────────────────────

    @FXML private void handleApprove(ActionEvent event) {
        Auction sel = allAuctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        sendAdminAction("approve", sel.getId());
    }

    @FXML private void handleForceStart(ActionEvent event) {
        Auction sel = allAuctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        sendAdminAction("start", sel.getId());
    }

    @FXML private void handleForceFinish(ActionEvent event) {
        Auction sel = allAuctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        sendAdminAction("finish", sel.getId());
    }

    @FXML private void handleForceCancel(ActionEvent event) {
        Auction sel = allAuctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
                "Huỷ \"" + sel.getItem().getName() + "\"?", ButtonType.YES, ButtonType.NO);
        c.setTitle("Xác nhận huỷ");
        c.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) sendAdminAction("cancel", sel.getId());
        });
    }

    @FXML private void handleRefreshAuctions(ActionEvent event) {
        auctionSearchField.clear(); auctionStatusFilter.getSelectionModel().selectFirst();
        if (wsConnected) {
            sendRequestSync();
        } else {
            loadAuctionsFromServer();
        }
    }

    @FXML private void handleQuickApprove(ActionEvent event) {
        Auction sel = auctionList.stream().filter(a -> a.getStatus() == AuctionStatus.PENDING).findFirst().orElse(null);
        if (sel == null) {
            showAlert(Alert.AlertType.INFORMATION, "Không có", "Không có phiên đấu giá nào đang chờ duyệt.");
            return;
        }
        sendAdminAction("approve", sel.getId());
    }

    @FXML private void handleQuickStart(ActionEvent event) {
        Auction sel = auctionList.stream().filter(a -> a.getStatus() == AuctionStatus.OPEN).findFirst().orElse(null);
        if (sel == null) {
            showAlert(Alert.AlertType.INFORMATION, "Không có", "Không có phiên đấu giá nào đang chờ bắt đầu.");
            return;
        }
        sendAdminAction("start", sel.getId());
    }

    /** Send ADMIN_ACTION via WebSocket so server persists + broadcasts to all clients. */
    private void sendAdminAction(String action, String auctionId) {
        if (!wsConnected || wsClient == null) {
            showAlert(Alert.AlertType.ERROR, "Mất kết nối",
                    "Không thể kết nối đến server. Hành động không được thực hiện.");
            return;
        }
        User admin = SessionManager.getInstance().getCurrentUser();
        JsonObject req = new JsonObject();
        req.addProperty("type",      "ADMIN_ACTION");
        req.addProperty("action",    action);
        req.addProperty("auctionId", auctionId);
        req.addProperty("adminId",   admin != null ? admin.getId() : "");
        wsClient.send(req.toString());
        applyOptimisticAuctionStatus(action, auctionId);
        System.out.println("[AdminMgmt] Sent ADMIN_ACTION action=" + action + " auctionId=" + auctionId);
    }

    private void applyOptimisticAuctionStatus(String action, String auctionId) {
        AuctionStatus nextStatus = statusAfterAdminAction(action);
        if (nextStatus == null) return;

        auctionList.stream()
                .filter(a -> a.getId().equals(auctionId))
                .findFirst()
                .ifPresent(a -> {
                    a.setStatus(nextStatus);
                    if (nextStatus == AuctionStatus.RUNNING && a.getStartTime() == null) {
                        a.setStartTime(com.auction.util.TimeSyncManager.getNow());
                    }
                    allAuctionTable.refresh();
                    updateAuctionButtons(allAuctionTable.getSelectionModel().getSelectedItem());
                    refreshStats();
                });
    }

    private AuctionStatus statusAfterAdminAction(String action) {
        return switch (action) {
            case "approve" -> AuctionStatus.OPEN;
            case "start" -> AuctionStatus.RUNNING;
            case "finish" -> AuctionStatus.CLOSED;
            case "cancel" -> AuctionStatus.CANCELED;
            default -> null;
        };
    }

    @FXML private void handleAuctionSearch(ActionEvent event) {
        String keyword   = auctionSearchField.getText().trim().toLowerCase();
        String statusSel = auctionStatusFilter.getValue();
        List<Auction> filtered = auctionList.stream()
                .filter(a -> {
                    boolean matchName = keyword.isEmpty() ||
                            a.getItem().getName().toLowerCase().contains(keyword) ||
                            a.getSeller().getUsername().toLowerCase().contains(keyword);
                    boolean matchStatus = statusSel == null || statusSel.equals("Tất cả") ||
                            a.getStatusDisplay().equals(statusSel);
                    return matchName && matchStatus;
                }).collect(Collectors.toList());
        allAuctionTable.setItems(FXCollections.observableArrayList(filtered));
        allAuctionTable.getSelectionModel().clearSelection();
        disableAuctionButtons();
        auctionCountLabel.setText("Kết quả: " + filtered.size() + " phiên");
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    /**
     * Refreshes statistics from the in-memory cached lists.
     * Does NOT make any network calls – call after loadUsers() or loadAuctionsFromServer() complete.
     */
    private void refreshStats() {
        List<User>    users    = new ArrayList<>(userList);
        List<Auction> auctions = new ArrayList<>(auctionList);
        if (statTotalUsers    != null) statTotalUsers.setText(String.valueOf(users.size()));
        if (statTotalAuctions != null) statTotalAuctions.setText(String.valueOf(auctions.size()));
        if (statPending  != null) statPending.setText(String.valueOf(auctions.stream().filter(a -> a.getStatus() == AuctionStatus.PENDING).count()));
        if (statOpen     != null) statOpen.setText(String.valueOf(auctions.stream().filter(a -> a.getStatus() == AuctionStatus.OPEN).count()));
        if (statRunning  != null) statRunning.setText(String.valueOf(auctions.stream().filter(a -> a.getStatus() == AuctionStatus.RUNNING).count()));
        if (statFinished != null) statFinished.setText(String.valueOf(auctions.stream().filter(a -> a.getStatus() == AuctionStatus.CLOSED).count()));
        if (statBidders  != null) statBidders.setText(String.valueOf(users.stream().filter(u -> u instanceof Bidder).count()));
        if (statSellers  != null) statSellers.setText(String.valueOf(users.stream().filter(u -> u instanceof Seller).count()));
        if (statCanceled != null) statCanceled.setText(String.valueOf(auctions.stream().filter(a -> a.getStatus() == AuctionStatus.CANCELED).count()));
    }

    @FXML private void handleRefreshStats(ActionEvent event) { loadUsers(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Build an Auction object from a WS JSON snapshot.
     * Does NOT fall back to local DB — local DB is stale on remote machines.
     * All data comes exclusively from the server WS snapshot.
     */
    private java.util.Optional<Auction> buildAuctionFromJson(JsonObject json) {
        try {
            String auctionId      = json.get("auctionId").getAsString();
            String itemName       = json.get("itemName").getAsString();
            String sellerUsername = json.get("sellerUsername").getAsString();
            String sellerId       = json.get("sellerId").getAsString();
            String statusStr      = json.get("status").getAsString();
            double highestBid     = json.get("highestBid").getAsDouble();
            String endTimeStr     = json.get("endTime").getAsString();
            String createdAtStr = json.has("auctionCreatedAt")
                    ? json.get("auctionCreatedAt").getAsString() : com.auction.util.TimeSyncManager.getNow().toString();

            AuctionStatus status  = AuctionStatus.valueOf(statusStr);
            LocalDateTime endTime   = LocalDateTime.parse(endTimeStr);
            LocalDateTime createdAt = LocalDateTime.parse(createdAtStr);
            String startTimeStr   = json.has("startTime") ? json.get("startTime").getAsString() : "";
            LocalDateTime startTime = startTimeStr.isEmpty() ? null : LocalDateTime.parse(startTimeStr);

            double startPrice = json.has("startPrice")   ? json.get("startPrice").getAsDouble()   : highestBid;
            String desc       = json.has("itemDesc")      ? json.get("itemDesc").getAsString()      : "";
            String imageUrl   = json.has("itemImageUrl")  ? json.get("itemImageUrl").getAsString()  : "";
            String category   = json.has("itemCategory")  ? json.get("itemCategory").getAsString()  : "Điện tử";

            Seller seller = new Seller(sellerId, createdAt, sellerUsername, "", sellerUsername + "_Shop");
            // Use 4-arg normal constructors (no extra fields needed for display)
            Item item = switch (category) {
                case "Nghệ thuật" -> new Art(itemName, desc, startPrice, seller);
                case "Xe cộ"      -> new Vehicle(itemName, desc, startPrice, seller);
                default           -> new Electronics(itemName, desc, startPrice, seller);
            };
            item.setImageUrl(imageUrl);

            Auction a = new Auction(auctionId, createdAt, seller, item, status, highestBid, startTime, endTime);

            // Inject bid history from server snapshot
            if (json.has("bidHistory")) {
                JsonArray bids = json.get("bidHistory").getAsJsonArray();
                for (int i = 0; i < bids.size(); i++) {
                    JsonObject b = bids.get(i).getAsJsonObject();
                    String bidId = b.get("bidId").getAsString();
                    double amt   = b.get("amount").getAsDouble();
                    String bName = b.get("bidderUsername").getAsString();
                    String bId   = b.get("bidderId").getAsString();
                    LocalDateTime ts = LocalDateTime.parse(b.get("time").getAsString());
                    Bidder dummy = new Bidder(bId, ts, bName, "", 0);
                    a.injectBid(new BidTransaction(bidId, ts, dummy, a, amt));
                }
            }
            int bidCount = json.has("bidCount")
                    ? json.get("bidCount").getAsInt()
                    : a.getBidHistory().size();
            auctionBidCounts.put(auctionId, bidCount);
            return java.util.Optional.of(a);
        } catch (Exception e) {
            System.err.println("[AdminMgmt] buildAuctionFromJson error: " + e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /** Clean up WS connection when navigating away from this screen. */
    public void cleanup() {
        if (wsClient != null) wsClient.disconnect();
    }
}
