package com.auction.controller;

import com.auction.exception.InvalidStatusException;
import com.auction.model.*;
import com.auction.service.AppFacade;
import com.auction.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
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
 * Uses AppFacade — no direct service/repository imports.
 * NOTE: Sellers CANNOT start/stop auctions — only Admin can.
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

    @FXML
    public void initialize() {
        setupFilterCombo();
        setupTableColumns();
        loadSellerAuctions();
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
    }

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

    private void loadSellerAuctions() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Seller seller)) return;
        List<Auction> auctions = app.getAuctionsBySeller(seller);
        auctionTable.setItems(FXCollections.observableArrayList(auctions));
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
        if (!(user instanceof Seller seller)) return;
        String keyword = searchField.getText().trim().toLowerCase();
        String statusSel = statusFilter.getValue();
        List<Auction> filtered = app.getAuctionsBySeller(seller).stream()
                .filter(a -> {
                    boolean matchName = keyword.isEmpty() ||
                            a.getItem().getName().toLowerCase().contains(keyword);
                    boolean matchStatus = statusSel == null || statusSel.equals("Tất cả") ||
                            a.getStatusDisplay().equals(statusSel);
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
                try {
                    app.cancelAuction(sel);
                    loadSellerAuctions();
                    showFormSuccess("Phiên đấu giá đã bị huỷ.");
                } catch (InvalidStatusException e) { showFormError(e.getMessage()); }
            }
        });
    }

    @FXML
    private void handleDelete(ActionEvent event) {
        Auction sel = auctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        app.removeAuction(sel.getId());
        loadSellerAuctions();
        disableActionButtons();
        showFormSuccess("Đã xoá phiên đấu giá.");
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

        Item item = switch (category) {
            case "Nghệ thuật" -> new Art(name, description, startPrice, seller);
            case "Xe cộ"      -> new Vehicle(name, description, startPrice, seller);
            default           -> new Electronics(name, description, startPrice, seller);
        };
        item.setImageUrl(imageUrl);

        try {
            app.createAuction(seller, item, endTime);
            loadSellerAuctions();
            handleClearForm(event);
            showFormSuccess("Phiên đấu giá đã được gửi và đang chờ Admin duyệt!");
        } catch (Exception e) { showFormError("Lỗi khi tạo phiên đấu giá: " + e.getMessage()); }
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
}
