package com.auction.controller;

import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bidder;
import com.auction.model.User;
import com.auction.service.AppFacade;
import com.auction.util.ImageLoaderUtil;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AuctionListController – browse auctions (role-filtered).
 * Uses AppFacade — Bidders/Sellers see only OPEN+RUNNING; Admin sees all.
 */
public class AuctionListController {

    @FXML private TextField        searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private Label            statusLabel;

    @FXML private TableView<Auction> auctionTable;
    @FXML private TableColumn<Auction, String> colImage;
    @FXML private TableColumn<Auction, String> colName;
    @FXML private TableColumn<Auction, String> colCategory;
    @FXML private TableColumn<Auction, String> colSeller;
    @FXML private TableColumn<Auction, String> colStatus;
    @FXML private TableColumn<Auction, String> colStartPrice;
    @FXML private TableColumn<Auction, String> colCurrentBid;
    @FXML private TableColumn<Auction, String> colEndTime;

    private final AppFacade app = AppFacade.getInstance();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        setupFilters();
        setupTableColumns();
        loadAuctions();
    }

    private boolean isAdmin() {
        User u = SessionManager.getInstance().getCurrentUser();
        return u != null && "Admin".equalsIgnoreCase(u.getRole());
    }

    private void setupFilters() {
        if (isAdmin()) {
            statusFilter.setItems(FXCollections.observableArrayList(
                    "Tất cả", "Chờ duyệt", "Chờ bắt đầu", "Đang diễn ra", "Đã đóng", "Đã huỷ"));
        } else {
            statusFilter.setItems(FXCollections.observableArrayList(
                    "Tất cả", "Chờ bắt đầu", "Đang diễn ra", "Đã đóng"));
        }
        statusFilter.getSelectionModel().selectFirst();
        categoryFilter.setItems(FXCollections.observableArrayList("Tất cả", "Điện tử", "Nghệ thuật", "Xe cộ"));
        categoryFilter.getSelectionModel().selectFirst();
    }

    private void setupTableColumns() {
        colImage.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItem().getImageUrl()));
        colImage.setCellFactory(col -> new TableCell<>() {
            private final ImageView iv = new ImageView();
            { iv.setFitWidth(64); iv.setFitHeight(44); iv.setPreserveRatio(true); setContentDisplay(ContentDisplay.GRAPHIC_ONLY); }
            @Override protected void updateItem(String url, boolean empty) {
                super.updateItem(url, empty);
                if (empty) { setGraphic(null); return; }
                iv.setImage(ImageLoaderUtil.loadItemImage(url, 64, 44));
                setGraphic(iv);
            }
        });
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItem().getName()));
        colCategory.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItem().getCategory()));
        colSeller.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSeller().getUsername()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatusDisplay()));
        colStartPrice.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%,.0f ₫", c.getValue().getItem().getStartingPrice())));
        colCurrentBid.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%,.0f ₫", c.getValue().getHighestBid())));
        colEndTime.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEndTime().format(FMT)));
    }

    private void loadAuctions() {
        List<Auction> source = isAdmin() ? app.getAllAuctions() : app.getPublicAuctions();
        auctionTable.setItems(FXCollections.observableArrayList(source));
        statusLabel.setText("Tổng: " + source.size() + " phiên đấu giá");
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        String keyword    = searchField.getText().trim().toLowerCase();
        String statusSel  = statusFilter.getValue();
        String categorySel= categoryFilter.getValue();
        List<Auction> source = isAdmin() ? app.getAllAuctions() : app.getPublicAuctions();
        List<Auction> filtered = source.stream()
                .filter(a -> {
                    boolean matchName = keyword.isEmpty() ||
                            a.getItem().getName().toLowerCase().contains(keyword) ||
                            a.getSeller().getUsername().toLowerCase().contains(keyword);
                    boolean matchStatus = statusSel == null || statusSel.equals("Tất cả") ||
                            a.getStatusDisplay().equals(statusSel);
                    boolean matchCat = categorySel == null || categorySel.equals("Tất cả") ||
                            a.getItem().getCategory().equals(categorySel);
                    return matchName && matchStatus && matchCat;
                }).collect(Collectors.toList());
        auctionTable.setItems(FXCollections.observableArrayList(filtered));
        statusLabel.setText("Kết quả: " + filtered.size() + " phiên đấu giá");
    }

    @FXML
    private void handleReset(ActionEvent event) {
        searchField.clear(); statusFilter.getSelectionModel().selectFirst();
        categoryFilter.getSelectionModel().selectFirst(); loadAuctions();
    }

    @FXML
    private void handleRowClick(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            Auction selected = auctionTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    NavigationManager.getInstance().navigateTo(
                            NavigationManager.AUCTION_DETAIL, "Chi tiết đấu giá", selected);
                } catch (IOException e) { e.printStackTrace(); }
            }
        }
    }
}
