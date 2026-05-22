package com.auction.controller;

import com.auction.model.Auction;
import com.auction.model.Item;
import com.auction.model.User;
import com.auction.service.AppFacade;
import com.auction.util.ImageLoaderUtil;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.TableRow;

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
    @FXML private TableColumn<Auction, String> colTitle;
    @FXML private TableColumn<Auction, String> colCategory;
    @FXML private TableColumn<Auction, String> colStatus;
    @FXML private TableColumn<Auction, String> colPrice;
    @FXML private TableColumn<Auction, String> colEndTime;

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
        colTitle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItem().getName()));
        colCategory.setCellValueFactory(c -> {
            Item item = c.getValue().getItem();
            String cat = "Khác";
            if (item instanceof com.auction.model.Electronics) cat = "Điện tử";
            else if (item instanceof com.auction.model.Art) cat = "Nghệ thuật";
            else if (item instanceof com.auction.model.Vehicle) cat = "Xe cộ";
            return new SimpleStringProperty(cat);
        });
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatusDisplay()));
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%,.0f ₫", c.getValue().getHighestBid())));
        colEndTime.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEndTime().format(FMT)));

        // ── Badge cell factory: Status ────────────────────────────────────────
        colStatus.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            {
                badge.setMaxWidth(Double.MAX_VALUE);
                badge.setAlignment(javafx.geometry.Pos.CENTER);
            }
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || value.isBlank()) {
                    setGraphic(null);
                    return;
                }
                badge.setText(value);
                badge.getStyleClass().setAll("table-badge", statusBadgeClass(value));
                setGraphic(badge);
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });

        // ── Badge cell factory: Category ──────────────────────────────────────
        colCategory.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            {
                badge.setMaxWidth(Double.MAX_VALUE);
                badge.setAlignment(javafx.geometry.Pos.CENTER);
            }
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || value.isBlank()) {
                    setGraphic(null);
                    return;
                }
                badge.setText(value);
                badge.getStyleClass().setAll("table-badge", categoryBadgeClass(value));
                setGraphic(badge);
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });
    }

    /** Maps a Vietnamese status display string to a CSS style class. */
    private static String statusBadgeClass(String status) {
        return switch (status) {
            case "Đang diễn ra" -> "badge-status-running";
            case "Đã đóng"      -> "badge-status-closed";
            case "Chờ duyệt"    -> "badge-status-pending";
            case "Chờ bắt đầu"  -> "badge-status-open";
            case "Đã huỷ"       -> "badge-status-canceled";
            default              -> "badge-status-other";
        };
    }

    /** Maps a Vietnamese category string to a CSS style class. */
    private static String categoryBadgeClass(String category) {
        return switch (category) {
            case "Nghệ thuật" -> "badge-cat-art";
            case "Xe cộ"      -> "badge-cat-vehicle";
            case "Điện tử"    -> "badge-cat-electronics";
            default            -> "badge-cat-other";
        };
    }


    private void loadAuctions() {
        statusLabel.setText("Đang tải dữ liệu từ server...");
        boolean admin = isAdmin();

        // 1. Create a background task
        Task<List<Auction>> task = new Task<>() {
            @Override
            protected List<Auction> call() throws Exception {
                AppFacade app = AppFacade.getInstance();
                if (admin) {
                    return app.getAllAuctions();
                } else {
                    return app.getPublicAuctions();
                }
            }
        };

        // 2. On success, update UI on the FX Application Thread
        task.setOnSucceeded(e -> {
            List<Auction> source = task.getValue();
            auctionTable.setItems(FXCollections.observableArrayList(source));
            statusLabel.setText("Tổng: " + source.size() + " phiên đấu giá");
        });

        // 3. On failure, show error
        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                statusLabel.setText("Lỗi tải dữ liệu: " + task.getException().getMessage());
            });
        });

        // 4. Start the background thread
        Thread t = new Thread(task, "fetch-auctions-thread");
        t.setDaemon(true); // Don't prevent JVM exit
        t.start();
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        String keyword = searchField.getText().trim().toLowerCase();
        String statusSel = statusFilter.getValue();
        String categorySel = categoryFilter.getValue();

        statusLabel.setText("Đang tìm kiếm...");
        boolean admin = isAdmin();

        Task<List<Auction>> task = new Task<>() {
            @Override
            protected List<Auction> call() throws Exception {
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

        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                statusLabel.setText("Lỗi tìm kiếm: " + task.getException().getMessage());
            });
        });

        Thread t = new Thread(task, "search-auctions-thread");
        t.setDaemon(true);
        t.start();
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
                statusLabel.setText("Đang tải chi tiết phiên đấu giá...");
                Task<Auction> fetchTask = new Task<>() {
                    @Override
                    protected Auction call() throws Exception {
                        return AppFacade.getInstance().findAuctionById(selected.getId()).orElse(selected);
                    }
                };
                fetchTask.setOnSucceeded(ev -> {
                    statusLabel.setText("Tải thành công.");
                    try {
                        NavigationManager.getInstance().navigateTo(
                                NavigationManager.AUCTION_DETAIL, "Chi tiết đấu giá", fetchTask.getValue());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                fetchTask.setOnFailed(ev -> {
                    statusLabel.setText("Lỗi tải chi tiết: " + fetchTask.getException().getMessage());
                });
                Thread t = new Thread(fetchTask, "fetch-detail-" + selected.getId());
                t.setDaemon(true);
                t.start();
            }
        }
    }
}
