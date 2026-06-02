package com.auction.ui.controller;

import com.auction.ui.support.dto.AdminStats;
import com.auction.ui.support.logic.AdminStatsService;
import com.auction.ui.support.logic.AuctionFilterService;
import com.auction.ui.support.logic.AuctionSnapshotMapper;
import com.auction.ui.support.logic.DefaultAuctionFilterService;
import com.auction.ui.support.logic.DefaultAuctionSnapshotMapper;
import com.auction.core.model.*;
import com.auction.infra.util.AlertHelper;
import com.auction.infra.util.SessionManager;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.BooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;

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
public class AdminManagementController extends RealtimeController {

    public AdminManagementController() {
        super("Admin-WS", "[AdminMgmt]");
    }

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
    @FXML private TableColumn<Auction, Boolean> colAuctionSelect;
    @FXML private TableColumn<Auction, String> colAuctionName;
    @FXML private TableColumn<Auction, String> colAuctionSeller;
    @FXML private TableColumn<Auction, String> colAuctionStatus;
    @FXML private TableColumn<Auction, String> colAuctionPrice;
    @FXML private TableColumn<Auction, String> colAuctionEnd;


    @FXML private Button btnStart;
    @FXML private Button btnFinish;
    @FXML private Button btnCancel;

    // ── Activity Log tab ──────────────────────────────────────────────────────
    @FXML private ListView<String> adminActivityLogList;

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

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AuctionSnapshotMapper snapshotMapper = new DefaultAuctionSnapshotMapper();
    private final AuctionFilterService filterService = new DefaultAuctionFilterService();
    private final AdminStatsService statsService = new AdminStatsService.Default();
    private volatile boolean wsConnected = false;

    // In-memory auction list driven by WS broadcasts (and REST on load)
    private final ObservableList<Auction> auctionList = FXCollections.observableArrayList();
    // Cached user list (loaded once; refreshed via handleRefreshUsers)
    private final ObservableList<User> userList = FXCollections.observableArrayList();
    private final Map<String, Integer> auctionBidCounts = new ConcurrentHashMap<>();
    private final Map<Auction, BooleanProperty> auctionSelectionMap = new ConcurrentHashMap<>();

    @FXML
    public void initialize() {
        DesktopHeaderController.setTitleAndSubtitle("Quản trị hệ thống", null);
        setupFilters();
        setupUserTableColumns();
        setupAuctionTableColumns();
        loadUsers();               // async REST load
        loadAuctionsFromServer();  // async REST load; overridden by FULL_SYNC once WS connects
        disableAuctionButtons();
        if (btnStart != null) {
            btnStart.setVisible(false);
            btnStart.setManaged(false);
            btnStart.setDisable(true);
        }

        allAuctionTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, nw) -> updateAuctionButtons(nw));

        // Connect WebSocket – broadcasts drive all real-time auction table updates
        setupRealtime();
    }

    // ── WebSocket ─────────────────────────────────────────────────────────────

    @Override
    protected void setupRealtime() {
        realtime.connect(
                this::handleWsMessage,
                err -> {
                    wsConnected = false;
                    System.err.println("[AdminMgmt] WS error: " + err);
                },
                () -> {
                    wsConnected = true;
                    sendRequestSync();
                    System.out.println("[AdminMgmt] WS connected, sent REQUEST_SYNC.");
                });
    }

    @Override
    protected void handleWsMessage(JsonObject json) {
        try {
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

    @Override
    public void cleanup() {
        super.cleanup();
    }

    private void sendRequestSync() {
        if (!realtime.isConnected()) return;
        JsonObject req = new JsonObject();
        req.addProperty("type", "REQUEST_SYNC");
        realtime.send(req);
    }

    private void logActivity(String msg) {
        if (adminActivityLogList == null) return;
        String time = com.auction.infra.util.TimeSyncManager.getNow().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String entry = String.format("[%s] %s", time, msg);
        Platform.runLater(() -> {
            adminActivityLogList.getItems().add(0, entry);
            if (adminActivityLogList.getItems().size() > 200) {
                adminActivityLogList.getItems().remove(200, adminActivityLogList.getItems().size());
            }
        });
    }

    /** Server sent all auctions. Replace local list. */
    private void onFullSync(JsonObject json) {
        if (!json.has("auctions")) return;
        com.google.gson.JsonArray arr = json.get("auctions").getAsJsonArray();
        List<Auction> synced = new ArrayList<>();
        auctionBidCounts.clear();
        auctionSelectionMap.clear();
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
            String msg = "Sản phẩm mới được tạo: " + a.getItem().getName();
            System.out.println("[AdminMgmt] " + msg);
            logActivity(msg);
        });
    }

    /** An auction's status changed (admin action broadcast). */
    private void onStatusChanged(JsonObject json) {
        String auctionId    = json.get("auctionId").getAsString();
        String newStatusStr = json.get("newStatus").getAsString();
        double highestBid   = json.has("highestBid") ? json.get("highestBid").getAsDouble() : -1;
        String startTimeStr = json.has("startTime")  ? json.get("startTime").getAsString()  : "";
        String endTimeStr   = json.has("endTime")    ? json.get("endTime").getAsString()    : "";

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
                if (!endTimeStr.isEmpty()) {
                    try { a.setEndTime(LocalDateTime.parse(endTimeStr)); } catch (Exception ignored) {}
                }
                break;
            }
        }
        allAuctionTable.refresh();
        updateAuctionButtons(allAuctionTable.getSelectionModel().getSelectedItem());
        refreshStats();
        logActivity(String.format("Trạng thái phiên %s đổi thành %s", auctionId, newStatusStr));
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
                String timeStr    = json.has("time")           ? json.get("time").getAsString()           : com.auction.infra.util.TimeSyncManager.getNow().toString();
                Bidder dummy = new Bidder(bidderId, com.auction.infra.util.TimeSyncManager.getNow(), bidderName, "", 0);
                a.injectBid(new BidTransaction(
                        java.util.UUID.randomUUID().toString(),
                        LocalDateTime.parse(timeStr), dummy, a, amount));
                break;
            }
        }
        allAuctionTable.refresh();
        updateAuctionButtons(allAuctionTable.getSelectionModel().getSelectedItem());
        logActivity(String.format("Đấu giá mới: %,.0f ₫ (Phiên: %s)", amount, auctionId));
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void setupFilters() {
        roleFilter.setItems(FXCollections.observableArrayList("Tất cả", "Bidder", "Seller", "Admin"));
        roleFilter.getSelectionModel().selectFirst();
        auctionStatusFilter.setItems(FXCollections.observableArrayList(
                "Tất cả", "Sắp diễn ra", "Đang diễn ra", "Đã đóng", "Đã huỷ"));
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
        allAuctionTable.setEditable(true);
        colAuctionSelect.setCellValueFactory(cellData -> {
            Auction a = cellData.getValue();
            return auctionSelectionMap.computeIfAbsent(a, k -> new SimpleBooleanProperty(false));
        });
        colAuctionSelect.setCellFactory(CheckBoxTableCell.forTableColumn(colAuctionSelect));
        colAuctionSelect.setEditable(true);

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
        if (btnStart != null) btnStart.setDisable(true);
        if (btnFinish != null) btnFinish.setDisable(false);
        if (btnCancel != null) btnCancel.setDisable(false);
    }

    private void updateAuctionButtons(Auction a) {
        // Do nothing, validation happens on click
    }

    // ── Data loading ─────────────────────────────────────────────────────────

    /** Async: load all users from server REST API. */
    private void loadUsers() {
        if (userCountLabel != null) userCountLabel.setText("Đang tải...");
        taskRunner.run("admin-users", app::getAllUsers, users -> {
            userList.setAll(users);
            userTable.setItems(userList);
            if (userCountLabel != null)
                userCountLabel.setText("Tổng: " + users.size() + " người dùng");
            refreshStats();
        }, error -> {
            if (userCountLabel != null) userCountLabel.setText("Lỗi tải người dùng");
        });
    }

    /** Async: initial load of all auctions from server REST API. */
    private void loadAuctionsFromServer() {
        if (auctionCountLabel != null) auctionCountLabel.setText("Đang tải...");
        taskRunner.run("admin-auctions", app::getAllAuctions, auctions -> {
            auctionList.setAll(auctions);
            auctionBidCounts.clear();
            auctionSelectionMap.clear();
            for (Auction a : auctionList) {
                auctionBidCounts.put(a.getId(), a.getBidHistory().size());
            }
            allAuctionTable.setItems(auctionList);
            if (auctionCountLabel != null)
                auctionCountLabel.setText("Tổng: " + auctionList.size() + " phiên");
            allAuctionTable.getSelectionModel().clearSelection();
            disableAuctionButtons();
            refreshStats();
        }, error -> {
            if (auctionCountLabel != null) auctionCountLabel.setText("Lỗi tải phiên đấu giá");
        });
    }

    // ── User tab actions ──────────────────────────────────────────────────────

    @FXML private void handleUserSearch(ActionEvent event) {
        List<User> filtered = filterService.filterUsers(userList, userSearchField.getText(), roleFilter.getValue());
        userTable.setItems(FXCollections.observableArrayList(filtered));
        if (userCountLabel != null)
            userCountLabel.setText("Kết quả: " + filtered.size() + " người dùng");
    }

    @FXML private void handleDeleteUser(ActionEvent event) {
        User sel = userTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showAlert(Alert.AlertType.WARNING, "Chưa chọn", "Vui lòng chọn người dùng."); return; }
        if (sel instanceof Admin) { showAlert(Alert.AlertType.ERROR, "Không thể xoá", "Không thể xoá tài khoản Admin."); return; }
        Alert c = AlertHelper.createConfirmation("Xác nhận xoá", "Xoá người dùng \"" + sel.getUsername() + "\"?", ButtonType.YES, ButtonType.NO);
        c.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                taskRunner.run("delete-user", () -> app.deleteUser(sel.getId()), result -> loadUsers(),
                        error -> showAlert(Alert.AlertType.ERROR, "Lỗi", error.getMessage()));
            }
        });
    }

    @FXML private void handleRefreshUsers(ActionEvent event) {
        userSearchField.clear(); roleFilter.getSelectionModel().selectFirst(); loadUsers();
    }

    // ── Auction tab actions (via WebSocket) ───────────────────────────────────


    @FXML private void handleForceStart(ActionEvent event) {
        showAlert(Alert.AlertType.INFORMATION, "Không còn thao tác",
                "Phiên đấu giá sẽ tự bắt đầu theo thời gian Seller đã đặt.");
    }

    @FXML private void handleForceFinish(ActionEvent event) {
        processBulkAction("KẾT THÚC", "kết thúc", "Đang diễn ra", "finish");
    }

    @FXML private void handleForceCancel(ActionEvent event) {
        processBulkAction("HUỶ", "huỷ", "hợp lệ để huỷ", "cancel");
    }

    private void processBulkAction(String actionName, String targetVerb, String statusName, String actionCode) {
        List<Auction> selected = auctionList.stream()
                .filter(a -> auctionSelectionMap.containsKey(a) && auctionSelectionMap.get(a).get())
                .collect(Collectors.toList());

        if (selected.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Chưa chọn", "Vui lòng tick chọn ít nhất một phiên đấu giá.");
            return;
        }

        List<Auction> valid = selected.stream()
                .filter(a -> filterService.isValidForAdminAction(a.getStatus(), actionCode))
                .collect(Collectors.toList());

        int totalSelected = selected.size();
        int validCount = valid.size();
        int invalidCount = totalSelected - validCount;

        if (validCount == 0) {
            showAlert(Alert.AlertType.WARNING, "Không hợp lệ", "Không có phiên đấu giá nào hợp lệ để " + targetVerb + ".");
            return;
        }

        String message;
        if (invalidCount > 0) {
            message = String.format("Hệ thống sẽ chỉ tiến hành %s cho %d phiên đang ở trạng thái %s.\n" +
                                    "(%d phiên còn lại không hợp lệ sẽ bị bỏ qua). Bạn có muốn tiếp tục?", 
                                    actionName, validCount, statusName, invalidCount);
        } else {
            message = String.format("Bạn đang chọn %d phiên đấu giá để %s. Bạn có muốn tiếp tục?", validCount, targetVerb);
        }

        Alert c = AlertHelper.createConfirmation("Xác nhận thao tác", message, ButtonType.YES, ButtonType.NO);
        c.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                for (Auction a : valid) {
                    sendAdminAction(actionCode, a.getId());
                }
                for (Auction a : selected) {
                    if (auctionSelectionMap.containsKey(a)) {
                        auctionSelectionMap.get(a).set(false);
                    }
                }
                allAuctionTable.refresh();
            }
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

    // Quick actions removed

    /** Send ADMIN_ACTION via WebSocket so server persists + broadcasts to all clients. */
    private void sendAdminAction(String action, String auctionId) {
        if (!wsConnected || !realtime.isConnected()) {
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
        realtime.send(req);
        applyOptimisticAuctionStatus(action, auctionId);
        System.out.println("[AdminMgmt] Sent ADMIN_ACTION action=" + action + " auctionId=" + auctionId);
    }

    private void applyOptimisticAuctionStatus(String action, String auctionId) {
        AuctionStatus nextStatus = filterService.statusAfterAdminAction(action);
        if (nextStatus == null) return;

        auctionList.stream()
                .filter(a -> a.getId().equals(auctionId))
                .findFirst()
                .ifPresent(a -> {
                    a.setStatus(nextStatus);
                    if (nextStatus == AuctionStatus.RUNNING && a.getStartTime() == null) {
                        a.setStartTime(com.auction.infra.util.TimeSyncManager.getNow());
                    }
                    allAuctionTable.refresh();
                    updateAuctionButtons(allAuctionTable.getSelectionModel().getSelectedItem());
                    refreshStats();
                });
    }

    @FXML private void handleAuctionSearch(ActionEvent event) {
        List<Auction> filtered = filterService.filterAuctions(
                auctionList, auctionSearchField.getText(), auctionStatusFilter.getValue(), null);
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
        AdminStats stats = statsService.calculate(users, auctions);
        if (statTotalUsers    != null) statTotalUsers.setText(String.valueOf(stats.totalUsers()));
        if (statTotalAuctions != null) statTotalAuctions.setText(String.valueOf(stats.totalAuctions()));
        if (statPending  != null) statPending.setText("-");
        if (statOpen     != null) statOpen.setText(String.valueOf(stats.openAuctions()));
        if (statRunning  != null) statRunning.setText(String.valueOf(stats.runningAuctions()));
        if (statFinished != null) statFinished.setText(String.valueOf(stats.finishedAuctions()));
        if (statBidders  != null) statBidders.setText(String.valueOf(stats.bidders()));
        if (statSellers  != null) statSellers.setText(String.valueOf(stats.sellers()));
        if (statCanceled != null) statCanceled.setText(String.valueOf(stats.canceledAuctions()));
    }

    @FXML private void handleRefreshStats(ActionEvent event) { loadUsers(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showAlert(Alert.AlertType type, String title, String message) {
        AlertHelper.showAlert(type, title, message);
    }

    /**
     * Build an Auction object from a WS JSON snapshot.
     * Does NOT fall back to local DB — local DB is stale on remote machines.
     * All data comes exclusively from the server WS snapshot.
     */
    private java.util.Optional<Auction> buildAuctionFromJson(JsonObject json) {
        return snapshotMapper.fromServerSnapshot(json)
                .map(snapshot -> {
                    auctionBidCounts.put(snapshot.auction().getId(), snapshot.bidCount());
                    return snapshot.auction();
                });
    }
}
