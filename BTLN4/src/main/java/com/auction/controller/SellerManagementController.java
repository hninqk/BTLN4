package com.auction.controller;

import com.auction.client.AuctionClient;
import com.auction.model.*;
import com.auction.service.AppFacade;
import com.auction.util.SessionManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * SellerManagementController – Seller creates and views their auctions.
 *
 * Data is fetched from the server REST API (AppFacade → ApiClient).
 * Auction creation and cancel still go via WebSocket so all clients
 * receive real-time broadcasts.
 *
 * Auction creation is sent via WebSocket (CREATE_AUCTION) so the server
 * persists it and broadcasts AUCTION_CREATED to ALL clients (especially Admin).
 *
 * Listens for:
 *  – AUCTION_CREATED        → add new auction to own table (confirmation)
 *  – AUCTION_STATUS_CHANGED → update status badge in own table
 *  – FULL_SYNC              → initial load of seller's auctions from server
 */
public class SellerManagementController {

    @FXML private TextField        searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<Auction> auctionTable;
    @FXML private TableColumn<Auction, String> colName;
    @FXML private TableColumn<Auction, String> colStatus;
    @FXML private TableColumn<Auction, String> colPrice;
    @FXML private TableColumn<Auction, String> colBids;
    @FXML private TableColumn<Auction, String> colEndTime;

    @FXML private Button btnCancel;
    @FXML private Button btnDelete;

    @FXML private Label            formTitle;
    @FXML private TextField        itemNameField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private TextArea         descriptionArea;
    @FXML private TextField        itemImageField;
    @FXML private TextField        startPriceField;
    @FXML private Label            formErrorLabel;
    @FXML private Label            formSuccessLabel;
    @FXML private DatePicker       endDatePicker;
    @FXML private ComboBox<String> hourCombo;
    @FXML private ComboBox<String> minuteCombo;

    private final AppFacade app = AppFacade.getInstance();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // WebSocket for real-time sync
    private AuctionClient wsClient;
    private volatile boolean wsConnected = false;
    private final Gson gson = new Gson();

    // In-memory list of this seller's auctions
    private final ObservableList<Auction> sellerAuctions = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupFilterCombo();
        setupTableColumns();
        loadSellerAuctionsFromServer(); // async load from server REST API
        disableActionButtons();

        for (int i = 0; i < 24; i++)
            hourCombo.getItems().add(String.format("%02d", i));
        for (int i = 0; i < 60; i += 5)
            minuteCombo.getItems().add(String.format("%02d", i));
        hourCombo.getSelectionModel().select("12");
        minuteCombo.getSelectionModel().select("00");

        UnaryOperator<TextFormatter.Change> numericFilter = change ->
                change.getControlNewText().matches("[0-9,.]*") ? change : null;
        startPriceField.setTextFormatter(new TextFormatter<>(numericFilter));

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
                        System.err.println("[SellerMgmt] WS error: " + err);
                    }),
                    () -> {
                        wsConnected = true;
                        System.out.println("[SellerMgmt] WS connected.");
                    }
            );
        }, "Seller-WS");
        t.setDaemon(true);
        t.start();
    }

    private void handleWsMessage(String msg) {
        try {
            JsonObject json = gson.fromJson(msg, JsonObject.class);
            if (json.has("error")) {
                showFormError("Lỗi server: " + json.get("error").getAsString());
                return;
            }
            String type = json.has("type") ? json.get("type").getAsString() : "";
            switch (type) {
                case "AUCTION_CREATED"        -> onAuctionCreated(json);
                case "AUCTION_STATUS_CHANGED" -> onStatusChanged(json);
                case "FULL_SYNC"              -> onFullSync(json);
            }
        } catch (Exception e) {
            System.err.println("[SellerMgmt] WS parse error: " + e.getMessage());
        }
    }

    /** Server confirmed the auction was created (triggered by this seller's CREATE_AUCTION). */
    private void onAuctionCreated(JsonObject json) {
        User me = SessionManager.getInstance().getCurrentUser();
        if (!(me instanceof Seller mySeller)) return;

        String sellerId = json.has("sellerId") ? json.get("sellerId").getAsString() : "";
        if (!sellerId.equals(mySeller.getId())) return; // not my auction

        // Build from WS JSON snapshot — do NOT try local DB, it won't have server data
        buildMinimalAuction(json, mySeller).ifPresent(a -> {
            sellerAuctions.add(0, a);
            auctionTable.refresh();
            showFormSuccess("Đã gửi lên server thành công! Đang chờ Admin duyệt.");
            System.out.println("[SellerMgmt] Auction created confirmed: " + a.getId());
        });
    }

    /** An auction status changed on server. Patch in-memory directly. */
    private void onStatusChanged(JsonObject json) {
        String auctionId    = json.get("auctionId").getAsString();
        String newStatusStr = json.get("newStatus").getAsString();
        double highestBid   = json.has("highestBid") ? json.get("highestBid").getAsDouble() : -1;
        String startTimeStr = json.has("startTime")  ? json.get("startTime").getAsString()  : "";

        AuctionStatus newStatus;
        try { newStatus = AuctionStatus.valueOf(newStatusStr); }
        catch (IllegalArgumentException e) { return; }

        // DO NOT reload from local DB — local DB is stale on remote machines.
        for (Auction a : sellerAuctions) {
            if (a.getId().equals(auctionId)) {
                a.setStatus(newStatus);
                if (highestBid >= 0) a.setHighestBid(highestBid);
                if (!startTimeStr.isEmpty()) {
                    try { a.setStartTime(LocalDateTime.parse(startTimeStr)); } catch (Exception ignored) {}
                }
                break;
            }
        }
        auctionTable.refresh();
    }

    /** Full sync: reload seller's auctions from REST API. */
    private void onFullSync(JsonObject json) {
        loadSellerAuctionsFromServer();
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void setupFilterCombo() {
        statusFilter.setItems(FXCollections.observableArrayList(
                "Tất cả", "Chờ duyệt", "Chờ bắt đầu", "Đang diễn ra", "Đã đóng", "Đã huỷ"));
        statusFilter.getSelectionModel().selectFirst();
        categoryCombo.setItems(FXCollections.observableArrayList("Điện tử", "Nghệ thuật", "Xe cộ"));
        categoryCombo.getSelectionModel().selectFirst();
    }

    private void setupTableColumns() {
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItem().getName()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatusDisplay()));
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%,.0f ₫", c.getValue().getHighestBid())));
        colBids.setCellValueFactory(c -> new SimpleStringProperty(
                String.valueOf(c.getValue().getBidHistory().size())));
        colEndTime.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEndTime().format(FMT)));
    }

    /** Async: fetch this seller's auctions from the server REST API. */
    private void loadSellerAuctionsFromServer() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Seller seller)) return;
        formSuccessLabel.setText("Đang tải dữ liệu...");
        Task<List<Auction>> task = new Task<>() {
            @Override protected List<Auction> call() {
                return app.getAuctionsBySeller(seller);
            }
        };
        task.setOnSucceeded(e -> {
            sellerAuctions.setAll(task.getValue());
            auctionTable.setItems(sellerAuctions);
            formSuccessLabel.setText("");
        });
        task.setOnFailed(e -> Platform.runLater(() ->
                showFormError("Lỗi tải dữ liệu: " + task.getException().getMessage())));
        new Thread(task, "seller-auctions").start();
    }

    private void disableActionButtons() {
        btnCancel.setDisable(true);
        btnDelete.setDisable(true);
    }

    @FXML
    private void handleRowSelect(MouseEvent event) {
        Auction sel = auctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) { disableActionButtons(); return; }
        AuctionStatus status = sel.getStatus();
        btnCancel.setDisable(status != AuctionStatus.PENDING && status != AuctionStatus.OPEN);
        btnDelete.setDisable(status == AuctionStatus.RUNNING || status == AuctionStatus.OPEN
                || status == AuctionStatus.CLOSED);
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Seller)) return;
        String keyword   = searchField.getText().trim().toLowerCase();
        String statusSel = statusFilter.getValue();
        List<Auction> filtered = sellerAuctions.stream()
                .filter(a -> {
                    boolean matchName   = keyword.isEmpty() || a.getItem().getName().toLowerCase().contains(keyword);
                    boolean matchStatus = statusSel == null || statusSel.equals("Tất cả")
                            || a.getStatusDisplay().equals(statusSel);
                    return matchName && matchStatus;
                }).collect(Collectors.toList());
        auctionTable.setItems(FXCollections.observableArrayList(filtered));
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        Auction sel = auctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Huỷ phiên đấu giá \"" + sel.getItem().getName() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận huỷ");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                if (wsConnected && wsClient != null) {
                    // Send cancel via WS so server persists and broadcasts to all clients
                    JsonObject req = new JsonObject();
                    req.addProperty("type",      "ADMIN_ACTION");
                    req.addProperty("action",    "cancel");
                    req.addProperty("auctionId", sel.getId());
                    wsClient.send(req.toString());
                } else {
                    // WS unavailable — fall back to REST (no real-time broadcast)
                    showFormError("Server chưa kết nối. Vui lòng thử lại sau.");
                }
            }
        });
    }

    @FXML
    private void handleDelete(ActionEvent event) {
        Auction sel = auctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() { return app.removeAuction(sel.getId()); }
        };
        task.setOnSucceeded(e -> {
            if (Boolean.TRUE.equals(task.getValue())) {
                sellerAuctions.remove(sel);
                disableActionButtons();
                showFormSuccess("Đã xoá phiên đấu giá.");
            } else {
                showFormError("Không thể xoá phiên đấu giá.");
            }
        });
        task.setOnFailed(e -> Platform.runLater(() ->
                showFormError("Lỗi xoá: " + task.getException().getMessage())));
        new Thread(task, "delete-auction").start();
    }

    @FXML
    private void handleCreateAuction(ActionEvent event) {
        handleClearForm(event);
        formTitle.setText("Tạo phiên đấu giá mới");
    }

    @FXML
    private void handleBrowseImage(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh sản phẩm");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
        Window owner = itemImageField.getScene() != null ? itemImageField.getScene().getWindow() : null;
        File selected = chooser.showOpenDialog(owner);
        if (selected != null) itemImageField.setText(selected.getAbsolutePath());
    }

    @FXML
    private void handleSave(ActionEvent event) {
        clearFormMessages();
        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Seller seller)) {
            showFormError("Bạn không có quyền tạo phiên đấu giá."); return;
        }

        String name       = itemNameField.getText().trim();
        String category   = categoryCombo.getValue();
        String description= descriptionArea.getText().trim();
        String imageUrl   = itemImageField.getText().trim();
        if (!imageUrl.isEmpty() && !imageUrl.startsWith("http://") && !imageUrl.startsWith("https://") && !imageUrl.startsWith("data:image/")) {
            File file = new File(imageUrl);
            if (file.exists()) {
                imageUrl = com.auction.util.ImageLoaderUtil.convertFileToBase64AndResize(file);
            }
        }

        String priceStr   = startPriceField.getText().trim();
        LocalDate selDate = endDatePicker.getValue();
        String hour       = hourCombo.getValue();
        String minute     = minuteCombo.getValue();

        if (name.isEmpty() || priceStr.isEmpty() || selDate == null || hour == null || minute == null) {
            showFormError("Vui lòng điền đầy đủ các trường bắt buộc (*)."); return;
        }

        double startPrice;
        try {
            startPrice = Double.parseDouble(priceStr.replace(",", ""));
            if (startPrice <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showFormError("Giá khởi điểm không hợp lệ (phải là số dương)."); return;
        }

        LocalDateTime endTime;
        try {
            LocalTime time = LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute));
            endTime = LocalDateTime.of(selDate, time);
            if (endTime.isBefore(LocalDateTime.now())) throw new Exception("past");
        } catch (Exception e) {
            showFormError("Thời gian kết thúc không hợp lệ hoặc đã qua."); return;
        }

        if (wsConnected && wsClient != null) {
            // ── Send CREATE_AUCTION via WebSocket ──
            // Server saves to its DB and broadcasts AUCTION_CREATED to all clients
            JsonObject req = new JsonObject();
            req.addProperty("type",        "CREATE_AUCTION");
            req.addProperty("sellerId",    seller.getId());
            req.addProperty("itemName",    name);
            req.addProperty("category",    category);
            req.addProperty("description", description);
            req.addProperty("imageUrl",    imageUrl);
            req.addProperty("startPrice",  startPrice);
            req.addProperty("endTime",     endTime.toString());
            wsClient.send(req.toString());
            handleClearForm(event);
            showFormSuccess("Đang gửi lên server...");
        } else {
            // WebSocket not available – show an error instead of local DB fallback
            showFormError("Không thể kết nối server. Vui lòng đợi kết nối WebSocket.");
        }
    }

    @FXML
    private void handleClearForm(ActionEvent event) {
        itemNameField.clear(); descriptionArea.clear(); itemImageField.clear();
        startPriceField.clear(); categoryCombo.getSelectionModel().selectFirst();
        endDatePicker.setValue(null);
        hourCombo.getSelectionModel().clearSelection();
        minuteCombo.getSelectionModel().clearSelection();
        clearFormMessages();
    }

    private void showFormError(String msg)   { formErrorLabel.setText(msg);   formSuccessLabel.setText(""); }
    private void showFormSuccess(String msg) { formSuccessLabel.setText(msg); formErrorLabel.setText(""); }
    private void clearFormMessages()          { formErrorLabel.setText("");    formSuccessLabel.setText(""); }

    /** Build a minimal Auction from a WS JSON snapshot (for display while local DB syncs). */
    private java.util.Optional<Auction> buildMinimalAuction(JsonObject json, Seller seller) {
        try {
            String auctionId  = json.get("auctionId").getAsString();
            String itemName   = json.get("itemName").getAsString();
            String endTimeStr = json.get("endTime").getAsString();
            double highestBid = json.get("highestBid").getAsDouble();
            String statusStr  = json.get("status").getAsString();
            String createdStr = json.has("auctionCreatedAt") ? json.get("auctionCreatedAt").getAsString() : LocalDateTime.now().toString();
            double startPrice = json.has("startPrice") ? json.get("startPrice").getAsDouble() : highestBid;
            String category   = json.has("itemCategory") ? json.get("itemCategory").getAsString() : "Điện tử";
            String desc       = json.has("itemDesc")     ? json.get("itemDesc").getAsString()     : "";
            String imageUrl   = json.has("itemImageUrl") ? json.get("itemImageUrl").getAsString() : "";

            Item item = switch (category) {
                case "Nghệ thuật" -> new Art(itemName, desc, startPrice, seller);
                case "Xe cộ"      -> new Vehicle(itemName, desc, startPrice, seller);
                default           -> new Electronics(itemName, desc, startPrice, seller);
            };
            item.setImageUrl(imageUrl);

            Auction a = new Auction(
                    auctionId,
                    LocalDateTime.parse(createdStr),
                    seller, item,
                    AuctionStatus.valueOf(statusStr),
                    highestBid, null,
                    LocalDateTime.parse(endTimeStr));
            return java.util.Optional.of(a);
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    /** Clean up WS connection when navigating away from this screen. */
    public void cleanup() {
        if (wsClient != null) wsClient.disconnect();
    }
}
