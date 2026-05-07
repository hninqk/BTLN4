package com.auction.controller;

import com.auction.exception.InvalidStatusException;
import com.auction.model.*;
import com.auction.service.AuctionService;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SellerManagementController – Seller creates and manages their auctions.
 */
public class SellerManagementController {

    // Table
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<Auction>           auctionTable;
    @FXML private TableColumn<Auction, String> colName;
    @FXML private TableColumn<Auction, String> colStatus;
    @FXML private TableColumn<Auction, String> colPrice;
    @FXML private TableColumn<Auction, String> colBids;
    @FXML private TableColumn<Auction, String> colEndTime;

    // Action buttons
    @FXML private Button btnStart;
    @FXML private Button btnFinish;
    @FXML private Button btnCancel;
    @FXML private Button btnDelete;

    // Create/Edit form
    @FXML private Label         formTitle;
    @FXML private TextField     itemNameField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private TextArea      descriptionArea;
    @FXML private TextField     itemImageField;
    @FXML private TextField     startPriceField;
    @FXML private TextField     endTimeField;
    @FXML private Button        submitButton;
    @FXML private Label         formErrorLabel;
    @FXML private Label         formSuccessLabel;

    private static final DateTimeFormatter FMT     = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter IN_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        setupFilterCombo();
        setupTableColumns();
        loadSellerAuctions();
        disableActionButtons();
    }

    private void setupFilterCombo() {
        statusFilter.setItems(FXCollections.observableArrayList(
                "Tất cả", "Chờ bắt đầu", "Đang diễn ra", "Hoàn thành", "Đã huỷ"));
        statusFilter.getSelectionModel().selectFirst();

        categoryCombo.setItems(FXCollections.observableArrayList("Điện tử", "Nghệ thuật", "Xe cộ"));
        categoryCombo.getSelectionModel().selectFirst();
    }

    private void setupTableColumns() {
        colName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getItem().getName()));
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatusDisplay()));
        colPrice.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("%,.0f ₫", c.getValue().getHighestBid())));
        colBids.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getBidHistory().size())));
        colEndTime.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEndTime().format(FMT)));
    }

    private void loadSellerAuctions() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Seller seller)) return;

        List<Auction> auctions = AuctionService.getInstance().getAuctionsBySeller(seller);
        auctionTable.setItems(FXCollections.observableArrayList(auctions));
    }

    private void disableActionButtons() {
        btnStart.setDisable(true);
        btnFinish.setDisable(true);
        btnCancel.setDisable(true);
        btnDelete.setDisable(true);
    }

    @FXML
    private void handleRowSelect(MouseEvent event) {
        Auction sel = auctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        AuctionStatus status = sel.getStatus();
        btnStart.setDisable(status  != AuctionStatus.OPEN);
        btnFinish.setDisable(status != AuctionStatus.RUNNING);
        btnCancel.setDisable(status == AuctionStatus.PAID || status == AuctionStatus.CANCELED);
        btnDelete.setDisable(status == AuctionStatus.RUNNING);
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Seller seller)) return;
        String keyword   = searchField.getText().trim().toLowerCase();
        String statusSel = statusFilter.getValue();

        List<Auction> filtered = AuctionService.getInstance().getAuctionsBySeller(seller).stream()
                .filter(a -> {
                    boolean matchName   = keyword.isEmpty() || a.getItem().getName().toLowerCase().contains(keyword);
                    boolean matchStatus = statusSel == null || statusSel.equals("Tất cả") ||
                            a.getStatusDisplay().equals(statusSel);
                    return matchName && matchStatus;
                }).collect(Collectors.toList());
        auctionTable.setItems(FXCollections.observableArrayList(filtered));
    }

    @FXML
    private void handleStart(ActionEvent event) {
        Auction sel = auctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            AuctionService.getInstance().startAuction(sel);
            loadSellerAuctions();
            showFormSuccess("Phiên đấu giá đã bắt đầu.");
        } catch (InvalidStatusException e) {
            showFormError(e.getMessage());
        }
    }

    @FXML
    private void handleFinish(ActionEvent event) {
        Auction sel = auctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            AuctionService.getInstance().finishAuction(sel);
            loadSellerAuctions();
            showFormSuccess("Phiên đấu giá đã kết thúc.");
        } catch (InvalidStatusException e) {
            showFormError(e.getMessage());
        }
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        Auction sel = auctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            AuctionService.getInstance().cancelAuction(sel);
            loadSellerAuctions();
            showFormSuccess("Phiên đấu giá đã bị huỷ.");
        } catch (InvalidStatusException e) {
            showFormError(e.getMessage());
        }
    }

    @FXML
    private void handleDelete(ActionEvent event) {
        Auction sel = auctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        AuctionService.getInstance().removeAuction(sel.getId());
        loadSellerAuctions();
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
                new FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp")
        );
        Window owner = itemImageField.getScene() != null ? itemImageField.getScene().getWindow() : null;
        File selected = chooser.showOpenDialog(owner);
        if (selected != null) {
            itemImageField.setText(selected.getAbsolutePath());
        }
    }

    @FXML
    private void handleSave(ActionEvent event) {
        clearFormMessages();
        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Seller seller)) {
            showFormError("Bạn không có quyền tạo phiên đấu giá.");
            return;
        }

        String name        = itemNameField.getText().trim();
        String category    = categoryCombo.getValue();
        String description = descriptionArea.getText().trim();
        String imageUrl    = itemImageField.getText().trim();
        String priceStr    = startPriceField.getText().trim();
        String endTimeStr  = endTimeField.getText().trim();

        if (name.isEmpty() || priceStr.isEmpty() || endTimeStr.isEmpty()) {
            showFormError("Vui lòng điền đầy đủ các trường bắt buộc (*).");
            return;
        }

        double startPrice;
        try {
            startPrice = Double.parseDouble(priceStr.replace(",", ""));
            if (startPrice <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showFormError("Giá khởi điểm không hợp lệ (phải là số dương).");
            return;
        }

        LocalDateTime endTime;
        try {
            endTime = LocalDateTime.parse(endTimeStr, IN_FMT);
            if (endTime.isBefore(LocalDateTime.now())) throw new Exception("past");
        } catch (Exception e) {
            showFormError("Thời gian kết thúc không hợp lệ hoặc đã qua. Định dạng: yyyy-MM-dd HH:mm");
            return;
        }

        Item item = switch (category) {
            case "Nghệ thuật" -> new Art(name, description, startPrice, seller);
            case "Xe cộ"      -> new Vehicle(name, description, startPrice, seller);
            default           -> new Electronics(name, description, startPrice, seller);
        };
        item.setImageUrl(imageUrl);

        AuctionService.getInstance().createAuction(seller, item, endTime);
        loadSellerAuctions();
        handleClearForm(event);
        showFormSuccess("Phiên đấu giá đã được tạo thành công!");
    }

    @FXML
    private void handleClearForm(ActionEvent event) {
        itemNameField.clear();
        descriptionArea.clear();
        itemImageField.clear();
        startPriceField.clear();
        endTimeField.clear();
        categoryCombo.getSelectionModel().selectFirst();
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
}
