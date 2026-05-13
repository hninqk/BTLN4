package com.auction.controller;

import com.auction.exception.InvalidStatusException;
import com.auction.model.*;
import com.auction.service.AppFacade;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AdminManagementController – full system oversight.
 * Uses AppFacade (service layer) — no direct repository access.
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

    @FXML
    public void initialize() {
        setupFilters();
        setupUserTableColumns();
        setupAuctionTableColumns();
        loadUsers();
        loadAuctions();
        loadStats();
        disableAuctionButtons();

        allAuctionTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, nw) -> updateAuctionButtons(nw));
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
        colRole.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRole()));
        colUserCreated.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCreatedAt().format(FMT)));
    }

    private void setupAuctionTableColumns() {
        colAuctionName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItem().getName()));
        colAuctionSeller.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSeller().getUsername()));
        colAuctionStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatusDisplay()));
        colAuctionPrice.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%,.0f ₫", c.getValue().getHighestBid())));
        colAuctionBids.setCellValueFactory(c -> new SimpleStringProperty(
                String.valueOf(c.getValue().getBidHistory().size())));
        colAuctionEnd.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEndTime().format(FMT)));
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

    private void loadAuctions() {
        List<Auction> auctions = app.getAllAuctions();
        allAuctionTable.setItems(FXCollections.observableArrayList(auctions));
        auctionCountLabel.setText("Tổng: " + auctions.size() + " phiên");
        disableAuctionButtons();
    }

    private void loadStats() {
        List<User> users = app.getAllUsers();
        List<Auction> auctions = app.getAllAuctions();
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
        String role = roleFilter.getValue();
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

    @FXML private void handleRefreshUsers(ActionEvent event) { userSearchField.clear(); roleFilter.getSelectionModel().selectFirst(); loadUsers(); }

    // ── Auction tab actions ───────────────────────────────────────────────────

    @FXML private void handleAuctionSearch(ActionEvent event) {
        String keyword = auctionSearchField.getText().trim().toLowerCase();
        String statusSel = auctionStatusFilter.getValue();
        List<Auction> filtered = app.getAllAuctions().stream()
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

    @FXML private void handleApprove(ActionEvent event) {
        Auction sel = allAuctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            app.approveAuction(sel);
            showAlert(Alert.AlertType.INFORMATION, "Đã duyệt",
                    "\"" + sel.getItem().getName() + "\" → OPEN. Bidder có thể nhìn thấy phiên này.");
            refreshAuctionAndStats();
        } catch (InvalidStatusException e) { showAlert(Alert.AlertType.ERROR, "Lỗi", e.getMessage()); }
    }

    @FXML private void handleForceStart(ActionEvent event) {
        Auction sel = allAuctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            app.startAuction(sel);
            showAlert(Alert.AlertType.INFORMATION, "Đã bắt đầu",
                    "\"" + sel.getItem().getName() + "\" → RUNNING. Bidder có thể đặt giá.");
            refreshAuctionAndStats();
        } catch (InvalidStatusException e) { showAlert(Alert.AlertType.ERROR, "Lỗi", e.getMessage()); }
    }

    @FXML private void handleForceFinish(ActionEvent event) {
        Auction sel = allAuctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            app.finishAuction(sel);
            showAlert(Alert.AlertType.INFORMATION, "Đã kết thúc", "\"" + sel.getItem().getName() + "\" → CLOSED.");
            refreshAuctionAndStats();
        } catch (InvalidStatusException e) { showAlert(Alert.AlertType.ERROR, "Lỗi", e.getMessage()); }
    }

    @FXML private void handleForceCancel(ActionEvent event) {
        Auction sel = allAuctionTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
                "Huỷ \"" + sel.getItem().getName() + "\"?", ButtonType.YES, ButtonType.NO);
        c.setTitle("Xác nhận huỷ");
        c.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try { app.cancelAuction(sel); refreshAuctionAndStats(); }
                catch (InvalidStatusException e) { showAlert(Alert.AlertType.ERROR, "Lỗi", e.getMessage()); }
            }
        });
    }

    @FXML private void handleRefreshAuctions(ActionEvent event) {
        auctionSearchField.clear(); auctionStatusFilter.getSelectionModel().selectFirst();
        loadAuctions(); loadStats();
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @FXML private void handleRefreshStats(ActionEvent event) { loadStats(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refreshAuctionAndStats() { loadAuctions(); loadStats(); }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
