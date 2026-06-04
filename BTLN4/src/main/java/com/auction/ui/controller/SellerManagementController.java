package com.auction.ui.controller;

import com.auction.core.model.*;
import javafx.scene.control.*;

import com.auction.ui.util.AlertHelper;
import com.auction.core.util.CatboxUploader;
import com.auction.core.util.CurrencyUtil;
import com.auction.ui.support.logic.AuctionFilterService;
import com.auction.ui.support.logic.AuctionSnapshotMapper;
import com.auction.ui.support.logic.DefaultAuctionFilterService;
import com.auction.ui.support.logic.DefaultAuctionSnapshotMapper;
import com.auction.core.util.SessionManager;
import com.auction.core.util.TimeSyncManager;
import com.google.gson.JsonObject;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SellerManagementController extends RealtimeController {

    public SellerManagementController() {
        super("Seller-WS", "[SellerMgmt]");
    }

    @FXML

    private TextField searchField;

    @FXML

    private ComboBox<String> statusFilter;

    @FXML

    private TableView<Auction> auctionTable;

    @FXML

    private TableColumn<Auction, String> colName;

    @FXML

    private TableColumn<Auction, String> colStatus;

    @FXML

    private TableColumn<Auction, String> colPrice;

    @FXML

    private TableColumn<Auction, String> colEndTime;

    @FXML

    private Button btnCancel;

    @FXML

    private Button btnDelete;

    @FXML

    private Label formTitle;

    @FXML

    private TextField itemNameField;

    @FXML

    private ComboBox<String> categoryCombo;

    @FXML

    private TextArea descriptionArea;

    @FXML

    private TextField itemImageField;



    @FXML

    private TextField startPriceField;

    @FXML

    private Label formErrorLabel;

    @FXML

    private Label formSuccessLabel;

    @FXML

    private DatePicker startDatePicker;

    @FXML

    private Spinner<Integer> startHourSpinner;

    @FXML

    private Spinner<Integer> startMinuteSpinner;

    @FXML

    private DatePicker endDatePicker;

    @FXML

    private Spinner<Integer> hourSpinner;

    @FXML

    private Spinner<Integer> minuteSpinner;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AuctionSnapshotMapper snapshotMapper = new DefaultAuctionSnapshotMapper();

    private final AuctionFilterService filterService = new DefaultAuctionFilterService();

    private volatile boolean wsConnected = false;

    private final ObservableList<Auction> sellerAuctions = FXCollections.observableArrayList();

    @FXML

    public void initialize() {
        setupFilterCombo();
        setupTableColumns();
        loadSellerAuctionsFromServer();
        disableActionButtons();

        DesktopHeaderController.setTitleAndSubtitle("Quản lí sản phẩm", null);

        LocalDateTime defaultStart = TimeSyncManager.getNow().plusMinutes(5);
        LocalDateTime defaultEnd = defaultStart.plusHours(1);

        startDatePicker.setValue(defaultStart.toLocalDate());
        endDatePicker.setValue(defaultEnd.toLocalDate());

        SpinnerValueFactory<Integer> startHourFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, defaultStart.getHour());
        startHourSpinner.setValueFactory(startHourFactory);

        SpinnerValueFactory<Integer> startMinuteFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, defaultStart.getMinute());
        startMinuteSpinner.setValueFactory(startMinuteFactory);

        SpinnerValueFactory<Integer> hourFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, defaultEnd.getHour());
        hourSpinner.setValueFactory(hourFactory);

        SpinnerValueFactory<Integer> minuteFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, defaultEnd.getMinute());
        minuteSpinner.setValueFactory(minuteFactory);

        startHourSpinner.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) startHourSpinner.increment(0);
        });
        startMinuteSpinner.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) startMinuteSpinner.increment(0);
        });
        hourSpinner.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) hourSpinner.increment(0);
        });
        minuteSpinner.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) minuteSpinner.increment(0);
        });

        com.auction.core.util.CurrencyUtil.setupCurrencyTextField(startPriceField);

        setupRealtime();
    }

    @Override

    protected void setupRealtime() {
        realtime.connect(
                this::handleWsMessage,
                err -> {
                    wsConnected = false;
                    System.err.println("[SellerMgmt] WS error: " + err);
                },
                () -> {
                    wsConnected = true;
                    System.out.println("[SellerMgmt] WS connected.");
                });
    }

    @Override

    protected void handleWsMessage(JsonObject json) {
        try {
            if (json.has("error")) {
                showFormError("Lỗi server: " + json.get("error").getAsString());
                return;
            }
            String type = json.has("type") ? json.get("type").getAsString() : "";
            switch (type) {
                case "AUCTION_CREATED" -> onAuctionCreated(json);
                case "AUCTION_STATUS_CHANGED" -> onStatusChanged(json);
                case "FULL_SYNC" -> onFullSync(json);
            }
        } catch (Exception e) {
            System.err.println("[SellerMgmt] WS parse error: " + e.getMessage());
        }
    }

    private void onAuctionCreated(JsonObject json) {
        User me = SessionManager.getInstance().getCurrentUser();
        if (!(me instanceof Seller mySeller))
            return;

        String sellerId = json.has("sellerId") ? json.get("sellerId").getAsString() : "";
        if (!sellerId.equals(mySeller.getId()))
            return;

        buildMinimalAuction(json, mySeller).ifPresent(a -> {
            sellerAuctions.add(0, a);
            auctionTable.refresh();
            showFormSuccess("Đã đăng bán thành công. Phiên sẽ tự chuyển trạng thái theo thời gian đã đặt.");
            System.out.println("[SellerMgmt] Auction created confirmed: " + a.getId());
        });
    }

    private void onStatusChanged(JsonObject json) {
        String auctionId = json.get("auctionId").getAsString();
        String newStatusStr = json.get("newStatus").getAsString();
        double highestBid = json.has("highestBid") ? json.get("highestBid").getAsDouble() : -1;
        String startTimeStr = json.has("startTime") ? json.get("startTime").getAsString() : "";
        String endTimeStr = json.has("endTime") ? json.get("endTime").getAsString() : "";

        AuctionStatus newStatus;
        try {
            newStatus = AuctionStatus.valueOf(newStatusStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        for (Auction a : sellerAuctions) {
            if (a.getId().equals(auctionId)) {
                a.setStatus(newStatus);
                if (highestBid >= 0)
                    a.setHighestBid(highestBid);
                if (!startTimeStr.isEmpty()) {
                    try {
                        a.setStartTime(LocalDateTime.parse(startTimeStr));
                    } catch (Exception ignored) {
                    }
                }
                if (!endTimeStr.isEmpty()) {
                    try {
                        a.setEndTime(LocalDateTime.parse(endTimeStr));
                    } catch (Exception ignored) {
                    }
                }
                break;
            }
        }
        auctionTable.refresh();
    }

    private void onFullSync(JsonObject json) {
        loadSellerAuctionsFromServer();
    }

    private void setupFilterCombo() {
        statusFilter.setItems(FXCollections.observableArrayList(
                "Tất cả", "Sắp diễn ra", "Đang diễn ra", "Đã đóng", "Đã huỷ"));
        statusFilter.getSelectionModel().selectFirst();
        categoryCombo.setItems(FXCollections.observableArrayList("Điện tử", "Nghệ thuật", "Xe cộ"));
        categoryCombo.getSelectionModel().selectFirst();
    }

    private void setupTableColumns() {
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItem().getName()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatusDisplay()));
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%,.0f ₫", c.getValue().getHighestBid())));
        colEndTime.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEndTime().format(FMT)));
    }

    private void loadSellerAuctionsFromServer() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Seller seller))
            return;
        formSuccessLabel.setText("Đang tải dữ liệu...");
        taskRunner.run("seller-auctions", () -> app.getAuctionsBySeller(seller), auctions -> {
            sellerAuctions.setAll(auctions);
            auctionTable.setItems(sellerAuctions);
            formSuccessLabel.setText("");
        }, error -> showFormError("Lỗi tải dữ liệu: " + error.getMessage()));
    }

    private void disableActionButtons() {
        btnCancel.setDisable(true);
        btnDelete.setDisable(true);
    }

    @FXML

    private void handleRowSelect(MouseEvent event) {
        Auction sel = auctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            disableActionButtons();
            return;
        }
        AuctionStatus status = sel.getStatus();
        btnCancel.setDisable(status != AuctionStatus.UPCOMING && status != AuctionStatus.OPEN);
        btnDelete.setDisable(status == AuctionStatus.RUNNING || status == AuctionStatus.UPCOMING || status == AuctionStatus.OPEN
                || status == AuctionStatus.CLOSED);
    }

    @FXML

    private void handleSearch(ActionEvent event) {
        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Seller))
            return;
        String keyword = searchField.getText().trim().toLowerCase();
        String statusSel = statusFilter.getValue();
        List<Auction> filtered = filterService.filterAuctions(sellerAuctions, keyword, statusSel, null);
        auctionTable.setItems(FXCollections.observableArrayList(filtered));
    }

    @FXML

    private void handleCancel(ActionEvent event) {
        Auction sel = auctionTable.getSelectionModel().getSelectedItem();
        if (sel == null)
            return;
        Alert confirm = com.auction.ui.util.AlertHelper.createConfirmation("Xác nhận huỷ",
                "Huỷ phiên đấu giá \"" + sel.getItem().getName() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                if (wsConnected && realtime.isConnected()) {

                    JsonObject req = new JsonObject();
                    req.addProperty("type", "ADMIN_ACTION");
                    req.addProperty("action", "cancel");
                    req.addProperty("auctionId", sel.getId());
                    realtime.send(req);
                } else {

                    showFormError("Server chưa kết nối. Vui lòng thử lại sau.");
                }
            }
        });
    }

    @FXML

    private void handleDelete(ActionEvent event) {
        Auction sel = auctionTable.getSelectionModel().getSelectedItem();
        if (sel == null)
            return;
        taskRunner.run("delete-auction", () -> app.removeAuction(sel.getId()), deleted -> {
            if (Boolean.TRUE.equals(deleted)) {
                sellerAuctions.remove(sel);
                disableActionButtons();
                showFormSuccess("Đã xoá phiên đấu giá.");
            } else {
                showFormError("Không thể xoá phiên đấu giá.");
            }
        }, error -> showFormError("Lỗi xoá: " + error.getMessage()));
    }

    @FXML

    private void handleCreateAuction(ActionEvent event) {
        handleClearForm(event);
        formTitle.setText("Tạo phiên đấu giá mới");
    }

    @FXML

    private void handleBrowseImage(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Đọi ảnh sản phẩm");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image files",
                        "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
        Window owner = itemImageField.getScene() != null
                ? itemImageField.getScene().getWindow()
                : null;
        File selected = chooser.showOpenDialog(owner);
        if (selected == null)
            return;

        itemImageField.setText("Đang tải ảnh lên máy chủ...");
        itemImageField.setDisable(true);
        showFormSuccess("Đang tải ảnh lên hệ thống...");

        taskRunner.run("cloud-upload", () -> com.auction.core.util.CatboxUploader.upload(selected), publicUrl -> {
            itemImageField.setText(publicUrl);
            itemImageField.setDisable(false);
            showFormSuccess("Ảnh đã được tải lên thành công!");
            System.out.println("[SellerMgmt] Image uploaded: " + publicUrl);
        }, error -> {
            itemImageField.setText("");
            itemImageField.setDisable(false);
            showFormError("Đăng tải ảnh thất bại: " + error.getMessage());
            System.err.println("[SellerMgmt] Image upload failed: " + error.getMessage());
        });
    }

    @FXML

    private void handleSave(ActionEvent event) {
        clearFormMessages();
        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Seller seller)) {
            showFormError("Bạn không có quyền tạo phiên đấu giá.");
            return;
        }

        String name = itemNameField.getText().trim();
        String category = categoryCombo.getValue();
        String description = descriptionArea.getText().trim();
        String imageUrl = itemImageField.getText().trim();

        String priceStr = startPriceField.getText().trim();
        LocalDate startDate = startDatePicker.getValue();
        Integer startHour = startHourSpinner.getValue();
        Integer startMinute = startMinuteSpinner.getValue();
        LocalDate endDate = endDatePicker.getValue();
        Integer endHour   = hourSpinner.getValue();
        Integer endMinute = minuteSpinner.getValue();

        if (name.isEmpty() || priceStr.isEmpty()
                || startDate == null || startHour == null || startMinute == null
                || endDate == null || endHour == null || endMinute == null) {
            showFormError("Vui lòng điền đầy đủ các trường bắt buộc (*)");
            return;
        }

        double startPrice;
        try {
            startPrice = com.auction.core.util.CurrencyUtil.parseCurrency(priceStr);
            if (startPrice <= 0)
                throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showFormError("Giá khởi điểm không hợp lệ (phải là số dương).");
            return;
        }

        LocalDateTime startTime;
        LocalDateTime endTime;
        try {
            startTime = LocalDateTime.of(startDate, LocalTime.of(startHour, startMinute));
            endTime = LocalDateTime.of(endDate, LocalTime.of(endHour, endMinute));
        } catch (Exception e) {
            showFormError("Thời gian bắt đầu hoặc kết thúc không hợp lệ.");
            return;
        }
        LocalDateTime now = TimeSyncManager.getNow();
        if (startTime.isBefore(now)) {
            showFormError("Thời gian bắt đầu phải lớn hơn hoặc bằng thời gian hiện tại.");
            return;
        }
        if (!endTime.isAfter(startTime)) {
            showFormError("Thời gian kết thúc phải sau thời gian bắt đầu.");
            return;
        }

        if (wsConnected && realtime.isConnected()) {

            JsonObject req = new JsonObject();
            req.addProperty("type", "CREATE_AUCTION");
            req.addProperty("sellerId", seller.getId());
            req.addProperty("itemName", name);
            req.addProperty("category", category);
            req.addProperty("description", description);
            req.addProperty("imageUrl", imageUrl);
            req.addProperty("startPrice", startPrice);
            req.addProperty("startTime", startTime.toString());
            req.addProperty("endTime", endTime.toString());

            realtime.send(req);
            handleClearForm(event);
            showFormSuccess("Đang gửi lên server...");
        } else {

            showFormError("Không thể kết nối server. Vui lòng đợi kết nối WebSocket.");
        }
    }

    @FXML

    private void handleClearForm(ActionEvent event) {
        itemNameField.clear();
        descriptionArea.clear();
        itemImageField.clear();
        startPriceField.clear();
        categoryCombo.getSelectionModel().selectFirst();
        LocalDateTime defaultStart = TimeSyncManager.getNow().plusMinutes(5);
        LocalDateTime defaultEnd = defaultStart.plusHours(1);
        startDatePicker.setValue(defaultStart.toLocalDate());
        startHourSpinner.getValueFactory().setValue(defaultStart.getHour());
        startMinuteSpinner.getValueFactory().setValue(defaultStart.getMinute());
        endDatePicker.setValue(defaultEnd.toLocalDate());
        hourSpinner.getValueFactory().setValue(defaultEnd.getHour());
        minuteSpinner.getValueFactory().setValue(defaultEnd.getMinute());
        clearFormMessages();
    }

    private void showFormError(String msg) {
        formErrorLabel.setText(msg);
        formSuccessLabel.setText("");
    }

    private void showFormSuccess(String msg) {
        formSuccessLabel.setText(msg);
        formErrorLabel.setText("");
    }

    private void clearFormMessages() {
        formErrorLabel.setText("");
        formSuccessLabel.setText("");
    }

    private java.util.Optional<Auction> buildMinimalAuction(JsonObject json, Seller seller) {
        return snapshotMapper.fromSellerSnapshot(json, seller);
    }
}
