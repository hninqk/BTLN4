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
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
    @FXML private TableColumn<Auction, String> colAuctionBids;
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

    // In-memory auction list driven by WS broadcasts
    private final ObservableList<Auction> auctionList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupFilters();
        setupUserTableColumns();
        setupAuctionTableColumns();
        loadUsers();
        // Auctions loaded via FULL_SYNC from WS; fall back to local DB if WS fails
        loadAuctionsFromLocalDb();
        loadStats();
        disableAuctionButtons();

        allAuctionTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, nw) -> updateAuctionButtons(nw));

        // Connect WebSocket – broadcasts drive all auction table updates
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
                        // Fall back to local DB
                        loadAuctionsFromLocalDb();
                    }),
                    () -> {
                        // Connected: request full state from server
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
            JsonObject json = gson.fromJson(msg, JsonObject.class);
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
        for (int i = 0; i < arr.size(); i++) {
            buildAuctionFromJson(arr.get(i).getAsJsonObject()).ifPresent(synced::add);
        }
        auctionList.setAll(synced);
        allAuctionTable.setItems(auctionList);
        auctionCountLabel.setText("Tổng: " + auctionList.size() + " phiên");
        loadStats();
        System.out.println("[AdminMgmt] FULL_SYNC received: " + synced.size() + " auctions.");
    }

    /** A seller just created a new auction. */
    private void onAuctionCreated(JsonObject json) {
        buildAuctionFromJson(json).ifPresent(a -> {
            auctionList.add(0, a);
            auctionCountLabel.setText("Tổng: " + auctionList.size() + " phiên");
            loadStats();
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

        for (int i = 0; i < auctionList.size(); i++) {
            Auction a = auctionList.get(i);
            if (a.getId().equals(auctionId)) {
                // Reload from local DB for full object (seller, item, bids)
                app.findAuctionById(auctionId).ifPresent(fresh -> {
                    auctionList.set(auctionList.indexOf(a), fresh);
                });
                break;
            }
        }
        allAuctionTable.refresh();
        loadStats();
    }

    /** A new bid was placed — update bid count & highest price in the table row. */
    private void onBidUpdate(JsonObject json) {
        String auctionId = json.has("auctionId") ? json.get("auctionId").getAsString() : null;
        double amount    = json.get("amount").getAsDouble();
        if (auctionId == null) return;

        for (Auction a : auctionList) {
            if (a.getId().equals(auctionId)) {
                a.setHighestBid(amount);
                // Inject a bid entry so bid count updates
                String bidderName = json.has("bidderUsername") ? json.get("bidderUsername").getAsString() : "?";
                String bidderId   = json.has("bidderId")       ? json.get("bidderId").getAsString()       : "remote";
                String timeStr    = json.has("time")           ? json.get("time").getAsString()           : LocalDateTime.now().toString();
                Bidder dummy = new Bidder(bidderId, LocalDateTime.now(), bidderName, "", 0);
                a.injectBid(new BidTransaction(
                        java.util.UUID.randomUUID().toString(),
                        LocalDateTime.parse(timeStr), dummy, a, amount));
                break;
            }
        }
        allAuctionTable.refresh();
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
        colAuctionBids.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getBidHistory().size())));
        colAuctionEnd.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEndTime().format(FMT)));
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

    private void loadUsers() {
        List<User> users = app.getAllUsers();
        userTable.setItems(FXCollections.observableArrayList(users));
        userCountLabel.setText("Tổng: " + users.size() + " người dùng");
    }

    /** Used as initial load and WS fallback. */
    private void loadAuctionsFromLocalDb() {
        List<Auction> auctions = app.getAllAuctions();
        auctionList.setAll(auctions);
        allAuctionTable.setItems(auctionList);
        auctionCountLabel.setText("Tổng: " + auctions.size() + " phiên");
        disableAuctionButtons();
    }

    private void loadStats() {
        List<User>    users    = app.getAllUsers();
        List<Auction> auctions = new ArrayList<>(auctionList);
        statTotalUsers.setText(String.valueOf(users.size()));
        statTotalAuctions.setText(String.valueOf(auctions.size()));
        statPending.setText(String.valueOf(auctions.stream().filter(a -> a.getStatus() == AuctionStatus.PENDING).count()));
        statOpen.setText(String.valueOf(auctions.stream().filter(a -> a.getStatus() == AuctionStatus.OPEN).count()));
        statRunning.setText(String.valueOf(auctions.stream().filter(a -> a.getStatus() == AuctionStatus.RUNNING).count()));
        statFinished.setText(String.valueOf(auctions.stream().filter(a -> a.getStatus() == AuctionStatus.CLOSED).count()));
        statBidders.setText(String.valueOf(users.stream().filter(u -> u instanceof Bidder).count()));
        statSellers.setText(String.valueOf(users.stream().filter(u -> u instanceof Seller).count()));
        statCanceled.setText(String.valueOf(auctions.stream().filter(a -> a.getStatus() == AuctionStatus.CANCELED).count()));
    }

    // ── User tab actions ──────────────────────────────────────────────────────

    @FXML private void handleUserSearch(ActionEvent event) {
        String keyword = userSearchField.getText().trim().toLowerCase();
        String role    = roleFilter.getValue();
        List<User> filtered = app.getAllUsers().stream()
                .filter(u -> {
                    boolean matchName = keyword.isEmpty() || u.getUsername().toLowerCase().contains(keyword);
                    boolean matchRole = role == null || role.equals("Tất cả") || u.getRole().equals(role);
                    return matchName && matchRole;
                }).collect(Collectors.toList());
        userTable.setItems(FXCollections.observableArrayList(filtered));
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
            if (btn == ButtonType.YES) { app.deleteUser(sel.getId()); loadUsers(); loadStats(); }
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
            loadAuctionsFromLocalDb();
        }
        loadStats();
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
        System.out.println("[AdminMgmt] Sent ADMIN_ACTION action=" + action + " auctionId=" + auctionId);
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
        auctionCountLabel.setText("Kết quả: " + filtered.size() + " phiên");
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @FXML private void handleRefreshStats(ActionEvent event) { loadStats(); }

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
     * Falls back to local DB lookup for the full Seller/Item graph.
     */
    private java.util.Optional<Auction> buildAuctionFromJson(JsonObject json) {
        try {
            String auctionId = json.get("auctionId").getAsString();
            // Try local DB first (has full object graph)
            java.util.Optional<Auction> fromDb = app.findAuctionById(auctionId);
            if (fromDb.isPresent()) return fromDb;

            // If not in local DB yet (new auction from another machine), build minimal object
            String itemName     = json.get("itemName").getAsString();
            String sellerUsername = json.get("sellerUsername").getAsString();
            String sellerId     = json.get("sellerId").getAsString();
            String statusStr    = json.get("status").getAsString();
            double highestBid   = json.get("highestBid").getAsDouble();
            String endTimeStr   = json.get("endTime").getAsString();
            String createdAtStr = json.has("auctionCreatedAt") ? json.get("auctionCreatedAt").getAsString() : LocalDateTime.now().toString();

            AuctionStatus status = AuctionStatus.valueOf(statusStr);
            LocalDateTime endTime   = LocalDateTime.parse(endTimeStr);
            LocalDateTime createdAt = LocalDateTime.parse(createdAtStr);
            String startTimeStr = json.has("startTime") ? json.get("startTime").getAsString() : "";
            LocalDateTime startTime = (startTimeStr.isEmpty()) ? null : LocalDateTime.parse(startTimeStr);

            double startPrice = json.has("startPrice") ? json.get("startPrice").getAsDouble() : highestBid;
            String itemId     = json.has("itemId")     ? json.get("itemId").getAsString()     : auctionId + "-item";
            String desc       = json.has("itemDesc")   ? json.get("itemDesc").getAsString()   : "";
            String imageUrl   = json.has("itemImageUrl") ? json.get("itemImageUrl").getAsString() : "";
            String category   = json.has("itemCategory") ? json.get("itemCategory").getAsString() : "Điện tử";

            Seller seller = new Seller(sellerId, LocalDateTime.now(), sellerUsername, "", sellerUsername + "_Shop", 0, 0);
            Item item = switch (category) {
                case "Nghệ thuật" -> new Art(itemId, createdAt, itemName, desc, startPrice, seller);
                case "Xe cộ"      -> new Vehicle(itemId, createdAt, itemName, desc, startPrice, seller);
                default           -> new Electronics(itemId, createdAt, itemName, desc, startPrice, seller);
            };
            item.setImageUrl(imageUrl);

            Auction a = new Auction(auctionId, createdAt, seller, item, status, highestBid, startTime, endTime);

            // Inject bid history from snapshot
            if (json.has("bidHistory")) {
                JsonArray bids = json.get("bidHistory").getAsJsonArray();
                for (int i = 0; i < bids.size(); i++) {
                    JsonObject b  = bids.get(i).getAsJsonObject();
                    String bidId  = b.get("bidId").getAsString();
                    double amt    = b.get("amount").getAsDouble();
                    String bName  = b.get("bidderUsername").getAsString();
                    String bId    = b.get("bidderId").getAsString();
                    LocalDateTime ts = LocalDateTime.parse(b.get("time").getAsString());
                    Bidder dummy = new Bidder(bId, ts, bName, "", 0);
                    a.injectBid(new BidTransaction(bidId, ts, dummy, a, amt));
                }
            }
            return java.util.Optional.of(a);
        } catch (Exception e) {
            System.err.println("[AdminMgmt] buildAuctionFromJson error: " + e.getMessage());
            return java.util.Optional.empty();
        }
    }
}
