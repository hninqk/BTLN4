package com.auction.controller;

import com.auction.model.Auction;
import com.auction.model.User;
import com.auction.service.AppFacade;
import com.auction.util.ImageLoaderUtil;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AuctionListController – browse auctions (role-filtered).
 *
 * Data is fetched from the server via REST (AppFacade → ApiClient).
 * The initial load and every refresh run on a background thread so the
 * FX thread is never blocked.
 */
public class AuctionListController {

    @FXML private TextField           searchField;
    @FXML private ComboBox<String>    statusFilter;
    @FXML private ComboBox<String>    categoryFilter;
    @FXML private Label               statusLabel;

    @FXML private TableView<Auction>             auctionTable;
    @FXML private TableColumn<Auction, String>   colImage;
    @FXML private TableColumn<Auction, String>   colName;
    @FXML private TableColumn<Auction, String>   colCategory;
    @FXML private TableColumn<Auction, String>   colSeller;
    @FXML private TableColumn<Auction, String>   colStatus;
    @FXML private TableColumn<Auction, String>   colStartPrice;
    @FXML private TableColumn<Auction, String>   colCurrentBid;
    @FXML private TableColumn<Auction, String>   colEndTime;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        setupFilters();
        setupTableColumns();
        loadAuctions();   // async – does not block FX thread
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
            {
                iv.setFitWidth(64); iv.setFitHeight(44); iv.setPreserveRatio(true);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
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

    /**
     * Async auction load — fetches from the server REST API in the background.
     */
    private void loadAuctions() {
        statusLabel.setText("Đang tải dữ liệu từ server...");
        boolean admin = isAdmin();

        Task<List<Auction>> task = new Task<>() {
            @Override
            protected List<Auction> call() {
                AppFacade app = AppFacade.getInstance();
                return admin ? app.getAllAuctions() : app.getPublicAuctions();
            }
        };

        task.setOnSucceeded(e -> {
            List<Auction> source = task.getValue();
            auctionTable.setItems(FXCollections.observableArrayList(source));
            statusLabel.setText("Tổng: " + source.size() + " phiên đấu giá");
        });

        task.setOnFailed(e -> Platform.runLater(() ->
                statusLabel.setText("Lỗi tải dữ liệu: " + task.getException().getMessage())));

        new Thread(task, "fetch-auctions").start();
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        String keyword     = searchField.getText().trim().toLowerCase();
        String statusSel   = statusFilter.getValue();
        String categorySel = categoryFilter.getValue();

        statusLabel.setText("Đang tìm kiếm...");
        boolean admin = isAdmin();

        Task<List<Auction>> task = new Task<>() {
            @Override
            protected List<Auction> call() {
                AppFacade app = AppFacade.getInstance();
                List<Auction> source = admin ? app.getAllAuctions() : app.getPublicAuctions();
                return source.stream()
                        .filter(a -> {
                            boolean matchName = keyword.isEmpty()
                                    || a.getItem().getName().toLowerCase().contains(keyword)
                                    || a.getSeller().getUsername().toLowerCase().contains(keyword);
                            boolean matchStatus = statusSel == null || statusSel.equals("Tất cả")
                                    || a.getStatusDisplay().equals(statusSel);
                            boolean matchCat = categorySel == null || categorySel.equals("Tất cả")
                                    || a.getItem().getCategory().equals(categorySel);
                            return matchName && matchStatus && matchCat;
                        }).collect(Collectors.toList());
            }
        };

        task.setOnSucceeded(e -> {
            List<Auction> filtered = task.getValue();
            auctionTable.setItems(FXCollections.observableArrayList(filtered));
            statusLabel.setText("Kết quả: " + filtered.size() + " phiên đấu giá");
        });

        task.setOnFailed(e -> Platform.runLater(() ->
                statusLabel.setText("Lỗi tìm kiếm: " + task.getException().getMessage())));

        new Thread(task, "search-auctions").start();
    }

    @FXML
    private void handleReset(ActionEvent event) {
        searchField.clear();
        statusFilter.getSelectionModel().selectFirst();
        categoryFilter.getSelectionModel().selectFirst();
        loadAuctions();
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
