package com.auction.controller;

import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.service.AuctionService;
import com.auction.util.ImageLoaderUtil;
import com.auction.util.NavigationManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.image.ImageView;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AuctionListController – search and display all auctions.
 */
public class AuctionListController {

    @FXML private TextField        searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private Label            statusLabel;

    @FXML private TableView<Auction>           auctionTable;
    @FXML private TableColumn<Auction, String> colImage;
    @FXML private TableColumn<Auction, String> colName;
    @FXML private TableColumn<Auction, String> colCategory;
    @FXML private TableColumn<Auction, String> colSeller;
    @FXML private TableColumn<Auction, String> colStatus;
    @FXML private TableColumn<Auction, String> colStartPrice;
    @FXML private TableColumn<Auction, String> colCurrentBid;
    @FXML private TableColumn<Auction, String> colEndTime;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private ObservableList<Auction> allAuctions;

    @FXML
    public void initialize() {
        setupFilters();
        setupTableColumns();
        loadAllAuctions();
    }

    private void setupFilters() {
        statusFilter.setItems(FXCollections.observableArrayList(
                "Tất cả", "Chờ bắt đầu", "Đang diễn ra", "Hoàn thành", "Đã huỷ"));
        statusFilter.getSelectionModel().selectFirst();

        categoryFilter.setItems(FXCollections.observableArrayList(
                "Tất cả", "Điện tử", "Nghệ thuật", "Xe cộ"));
        categoryFilter.getSelectionModel().selectFirst();
    }

    private void setupTableColumns() {
        colImage.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getItem().getImageUrl()));
        colImage.setCellFactory(col -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            {
                imageView.setFitWidth(64);
                imageView.setFitHeight(44);
                imageView.setPreserveRatio(true);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(String imageUrl, boolean empty) {
                super.updateItem(imageUrl, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                imageView.setImage(ImageLoaderUtil.loadItemImage(imageUrl, 64, 44));
                setGraphic(imageView);
            }
        });

        colName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getItem().getName()));
        colCategory.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getItem().getCategory()));
        colSeller.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getSeller().getUsername()));
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatusDisplay()));
        colStartPrice.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("%,.0f ₫", c.getValue().getItem().getStartingPrice())));
        colCurrentBid.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("%,.0f ₫", c.getValue().getHighestBid())));
        colEndTime.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEndTime().format(FMT)));
    }

    private void loadAllAuctions() {
        allAuctions = FXCollections.observableArrayList(AuctionService.getInstance().getAllAuctions());
        auctionTable.setItems(allAuctions);
        statusLabel.setText("Tổng: " + allAuctions.size() + " phiên đấu giá");
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        String keyword     = searchField.getText().trim().toLowerCase();
        String statusSel   = statusFilter.getValue();
        String categorySel = categoryFilter.getValue();

        List<Auction> filtered = AuctionService.getInstance().getAllAuctions().stream()
                .filter(a -> {
                    boolean matchName   = keyword.isEmpty() ||
                            a.getItem().getName().toLowerCase().contains(keyword) ||
                            a.getSeller().getUsername().toLowerCase().contains(keyword);
                    boolean matchStatus = statusSel == null || statusSel.equals("Tất cả") ||
                            a.getStatusDisplay().equals(statusSel);
                    boolean matchCat    = categorySel == null || categorySel.equals("Tất cả") ||
                            a.getItem().getCategory().equals(categorySel);
                    return matchName && matchStatus && matchCat;
                })
                .collect(Collectors.toList());

        auctionTable.setItems(FXCollections.observableArrayList(filtered));
        statusLabel.setText("Kết quả: " + filtered.size() + " phiên đấu giá");
    }

    @FXML
    private void handleReset(ActionEvent event) {
        searchField.clear();
        statusFilter.getSelectionModel().selectFirst();
        categoryFilter.getSelectionModel().selectFirst();
        loadAllAuctions();
    }

    @FXML
    private void handleRowClick(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            Auction selected = auctionTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    NavigationManager.getInstance().navigateTo(
                            NavigationManager.AUCTION_DETAIL, "Chi tiết đấu giá", selected);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
