package com.auction.controller;

import com.auction.model.*;
import com.auction.service.AppFacade;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import com.auction.client.AuctionClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.ContentDisplay;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BidHistoryController – Bidder's bid history.
 * Uses AppFacade — no direct service/repository access.
 */
public class BidHistoryController {

    @FXML private HeaderController headerController;

    @FXML
    private Label totalBidsLabel;
    @FXML
    private Label wonAuctionsLabel;
    @FXML
    private Label activeParticipationsLabel;
    @FXML
    private Label totalSpentLabel;
    @FXML
    private Label currentBalanceLabel;

    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> resultFilter;
    @FXML
    private Label statusLabel;
    @FXML
    private Button viewDetailButton;
    @FXML
    private HBox paginationBox;
    @FXML
    private Button searchButton;
    @FXML
    private Button resetButton;

    @FXML
    private TableView<BidRow> historyTable;
    @FXML
    private TableColumn<BidRow, String> colItem;
    @FXML
    private TableColumn<BidRow, String> colSeller;
    @FXML
    private TableColumn<BidRow, String> colMyBid;
    @FXML
    private TableColumn<BidRow, String> colFinalBid;
    @FXML
    private TableColumn<BidRow, String> colResult;
    @FXML
    private TableColumn<BidRow, String> colStatus;
    @FXML
    private TableColumn<BidRow, String> colBidTime;

    private final AppFacade app = AppFacade.getInstance();
    private static final int PAGE_SIZE = 10;
    private List<BidRow> allRows = Collections.emptyList();
    private List<BidRow> filteredRows = Collections.emptyList();
    private int currentPageIndex;
    private AuctionClient wsClient;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static class HistoryCache {
        List<BidRow> rows;
        String totalBids, won, active, spent;
    }
    private static final java.util.Map<String, HistoryCache> cacheMap = new java.util.concurrent.ConcurrentHashMap<>();

    public static void preloadCache(java.util.List<Auction> fullAuctions) {
        cacheMap.clear();
        java.util.Map<String, java.util.List<BidRow>> rowsByBidder = new java.util.HashMap<>();
        
        for (Auction full : fullAuctions) {
            java.util.Map<String, BidTransaction> latestBidPerBidder = new java.util.HashMap<>();
            for (BidTransaction b : full.getBidHistory()) {
                latestBidPerBidder.put(b.getBidder().getId(), b); 
            }
            
            for (java.util.Map.Entry<String, BidTransaction> entry : latestBidPerBidder.entrySet()) {
                String bidderId = entry.getKey();
                BidTransaction myBid = entry.getValue();
                
                String result;
                AuctionStatus status = full.getStatus();
                if (status == AuctionStatus.RUNNING || status == AuctionStatus.OPEN || status == AuctionStatus.PENDING) {
                    result = "Đang tham gia";
                } else {
                    BidTransaction winner = full.getWinner();
                    if (winner != null && winner.getBidder().getId().equals(bidderId)) {
                        result = "Thắng";
                    } else {
                        result = "Thua";
                    }
                }
                rowsByBidder.computeIfAbsent(bidderId, k -> new java.util.ArrayList<>()).add(new BidRow(full, myBid, result));
            }
        }
        
        for (java.util.Map.Entry<String, java.util.List<BidRow>> entry : rowsByBidder.entrySet()) {
             String bidderId = entry.getKey();
             java.util.List<BidRow> allRows = entry.getValue();
             long won = 0, active = 0;
             double totalSpent = 0;
             
             for (BidRow r : allRows) {
                 if (r.result().contains("Thắng")) {
                     won++;
                     totalSpent += r.myBid().getAmount();
                 } else if (r.result().equals("Đang tham gia")) {
                     active++;
                 }
             }
             
             HistoryCache fresh = new HistoryCache();
             fresh.rows = allRows;
             fresh.totalBids = String.valueOf(allRows.size());
             fresh.won = String.valueOf(won);
             fresh.active = String.valueOf(active);
             fresh.spent = String.format("%,.0f ₫", totalSpent);
             cacheMap.put(bidderId, fresh);
        }
    }

    public record BidRow(Auction auction, BidTransaction myBid, String result) {
    }

    @FXML
    public void initialize() {
        // Set header title
        if (headerController != null) {
            headerController.setTitle("Lịch sử Đấu giá", "Xem lại các lượt đặt giá của bạn");
        }

        resultFilter.setItems(FXCollections.observableArrayList("Tất cả", "Thắng", "Thua", "Đang tham gia"));
        resultFilter.getSelectionModel().selectFirst();
        setupActionIcons();
        setupTableColumns();
        loadHistory();
        connectWebSocket();
    }

    private void setupActionIcons() {
        setButtonIcon(searchButton, FontAwesomeSolid.SEARCH);
        setButtonIcon(resetButton, FontAwesomeSolid.REDO_ALT);
        setButtonIcon(viewDetailButton, FontAwesomeSolid.ARROW_RIGHT);
    }

    private void setButtonIcon(Button button, FontAwesomeSolid iconCode) {
        if (button == null) {
            return;
        }
        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(13);
        icon.getStyleClass().add("button-icon");
        button.setGraphic(icon);
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(8);
    }

    private void setupTableColumns() {
        colItem.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().auction().getItem().getName()));
        colSeller.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().auction().getSeller().getUsername()));
        colMyBid.setCellValueFactory(
                c -> new SimpleStringProperty(String.format("%,.0f ₫", c.getValue().myBid().getAmount())));
        colFinalBid.setCellValueFactory(
                c -> new SimpleStringProperty(String.format("%,.0f ₫", c.getValue().auction().getHighestBid())));
        colResult.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().result()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().auction().getStatusDisplay()));
        colBidTime.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().myBid().getTimestamp().format(FMT)));

        // ── Badge cell factory: Kết quả ──────────────────────────────────────
        colResult.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            {
                badge.setMaxWidth(Double.MAX_VALUE);
                badge.setAlignment(javafx.geometry.Pos.CENTER);
                badge.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
            }
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || value.isBlank()) { setGraphic(null); return; }
                badge.setText(value);
                badge.getStyleClass().setAll("table-badge", resultBadgeClass(value));
                setGraphic(badge);
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });

        // ── Badge cell factory: Trạng thái ───────────────────────────────────
        colStatus.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            {
                badge.setMaxWidth(Double.MAX_VALUE);
                badge.setAlignment(javafx.geometry.Pos.CENTER);
                badge.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
            }
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || value.isBlank()) { setGraphic(null); return; }
                badge.setText(value);
                badge.getStyleClass().setAll("table-badge", statusBadgeClass(value));
                setGraphic(badge);
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });
    }

    /** Maps result text to a CSS class. */
    private static String resultBadgeClass(String result) {
        if (result.contains("Thắng"))       return "badge-result-won";
        if (result.equals("Thua"))          return "badge-result-lost";
        if (result.equals("Đang tham gia")) return "badge-result-active";
        return "badge-status-other";
    }

    /** Maps Vietnamese status display text to a CSS class (shared with AuctionList). */
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


    private void loadHistory() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Bidder bidder)) {
            statusLabel.setText("Chỉ Bidder mới có lịch sử đấu giá.");
            return;
        }

        if (currentBalanceLabel != null) {
            currentBalanceLabel.setText(String.format("%,.0f ₫", bidder.getAccountBalance()));
        }

        HistoryCache cache = cacheMap.get(bidder.getId());
        if (cache != null) {
            allRows = cache.rows == null ? Collections.emptyList() : cache.rows;
            totalBidsLabel.setText(cache.totalBids);
            wonAuctionsLabel.setText(cache.won);
            activeParticipationsLabel.setText(cache.active);
            totalSpentLabel.setText(cache.spent);
            applyFilters(true);
        } else {
            statusLabel.setText("Đang tải dữ liệu từ server...");
        }

        javafx.concurrent.Task<List<BidRow>> fetchTask = new javafx.concurrent.Task<>() {
            @Override
            protected List<BidRow> call() throws Exception {
                List<BidRow> rows = new ArrayList<>();
                // Since getAllAuctions only returns a shallow list (no bids),
                // we must fetch the detailed versions to find the bidder's history.
                List<Auction> shallowAuctions = app.getAllAuctions();
                for (Auction shallow : shallowAuctions) {
                    Auction full = app.findAuctionById(shallow.getId()).orElse(null);
                    if (full == null) continue;

                    Optional<BidTransaction> myLatest = full.getBidHistory().stream()
                            .filter(b -> b.getBidder().getId().equals(bidder.getId()))
                            .reduce((first, second) -> second);
                            
                    if (myLatest.isEmpty()) continue;

                    BidTransaction myBid = myLatest.get();
                    String result;
                    AuctionStatus status = full.getStatus();
                    if (status == AuctionStatus.RUNNING || status == AuctionStatus.OPEN || status == AuctionStatus.PENDING) {
                        result = "Đang tham gia";
                    } else {
                        BidTransaction winner = full.getWinner();
                        if (winner != null && winner.getBidder().getId().equals(bidder.getId())) {
                            result = "Thắng";
                        } else {
                            result = "Thua";
                        }
                    }
                    rows.add(new BidRow(full, myBid, result));
                }
                return rows;
            }
        };

        fetchTask.setOnSucceeded(e -> {
            allRows = fetchTask.getValue() == null ? Collections.emptyList() : fetchTask.getValue();
            long won = 0, active = 0;
            double totalSpent = 0;

            for (BidRow r : allRows) {
                if (r.result().contains("Thắng")) {
                    won++;
                    totalSpent += r.myBid().getAmount();
                } else if (r.result().equals("Đang tham gia")) {
                    active++;
                }
            }

            HistoryCache fresh = new HistoryCache();
            fresh.rows = allRows;
            fresh.totalBids = String.valueOf(allRows.size());
            fresh.won = String.valueOf(won);
            fresh.active = String.valueOf(active);
            fresh.spent = String.format("%,.0f ₫", totalSpent);
            cacheMap.put(bidder.getId(), fresh);

            totalBidsLabel.setText(fresh.totalBids);
            wonAuctionsLabel.setText(fresh.won);
            activeParticipationsLabel.setText(fresh.active);
            totalSpentLabel.setText(fresh.spent);

            applyFilters(true);
        });
        fetchTask.setOnFailed(e -> statusLabel.setText("Không thể tải lịch sử đấu giá."));

        Thread th = new Thread(fetchTask);
        th.setDaemon(true);
        th.start();
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        applyFilters(true);
    }

    private void applyFilters(boolean resetPage) {
        String keyword = searchField.getText().trim().toLowerCase();
        String resultSel = resultFilter.getValue();
        filteredRows = allRows.stream()
                .filter(r -> {
                    boolean matchName = keyword.isEmpty()
                            || r.auction().getItem().getName().toLowerCase().contains(keyword);
                    boolean matchResult = resultSel == null || resultSel.equals("Tất cả") ||
                            r.result().contains(resultSel);
                    return matchName && matchResult;
                }).collect(Collectors.toList());

        int pageCount = getPageCount();
        if (resetPage) {
            currentPageIndex = 0;
        } else if (currentPageIndex >= pageCount) {
            currentPageIndex = Math.max(0, pageCount - 1);
        }

        updateHistoryPage();
        renderPagination();
    }

    @FXML
    private void handleReset(ActionEvent event) {
        searchField.clear();
        resultFilter.getSelectionModel().selectFirst();
        applyFilters(true);
    }

    private void updateHistoryPage() {
        int total = filteredRows.size();
        int pageCount = getPageCount();
        int fromIndex = Math.min(currentPageIndex * PAGE_SIZE, total);
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);

        List<BidRow> pageRows = total == 0
                ? Collections.emptyList()
                : filteredRows.subList(fromIndex, toIndex);

        historyTable.setItems(FXCollections.observableArrayList(pageRows));
        historyTable.getSelectionModel().clearSelection();
        viewDetailButton.setVisible(false);

        if (total == 0) {
            statusLabel.setText("Không có lượt phù hợp.");
        } else {
            statusLabel.setText("Kết quả: " + total + " lượt | Trang "
                    + (currentPageIndex + 1) + "/" + pageCount);
        }
    }

    private void renderPagination() {
        if (paginationBox == null) {
            return;
        }

        paginationBox.getChildren().clear();
        int pageCount = getPageCount();
        paginationBox.setVisible(true);
        paginationBox.setManaged(true);

        paginationBox.getChildren().add(createArrowPageButton(
                FontAwesomeSolid.ARROW_LEFT, currentPageIndex == 0, () -> goToPage(currentPageIndex - 1)));

        List<Integer> pages = visiblePageIndexes(pageCount);
        int lastAdded = -1;
        for (int page : pages) {
            if (lastAdded >= 0 && page - lastAdded > 1) {
                Label ellipsis = new Label("...");
                ellipsis.getStyleClass().add("label-subtle");
                paginationBox.getChildren().add(ellipsis);
            }
            Button pageButton = createPageButton(String.valueOf(page + 1), false, () -> goToPage(page));
            if (page == currentPageIndex) {
                pageButton.getStyleClass().add("page-button-active");
                pageButton.setMouseTransparent(true);
            }
            paginationBox.getChildren().add(pageButton);
            lastAdded = page;
        }

        paginationBox.getChildren().add(createArrowPageButton(
                FontAwesomeSolid.ARROW_RIGHT, currentPageIndex >= pageCount - 1,
                () -> goToPage(currentPageIndex + 1)));
    }

    private Button createPageButton(String text, boolean disabled, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("btn-secondary", "page-button");
        button.setDisable(disabled);
        button.setOnAction(e -> action.run());
        return button;
    }

    private Button createArrowPageButton(FontAwesomeSolid iconCode, boolean disabled, Runnable action) {
        Button button = createPageButton("", disabled, action);
        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(13);
        icon.getStyleClass().add("page-icon");
        button.setGraphic(icon);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setTooltip(new Tooltip(iconCode == FontAwesomeSolid.ARROW_LEFT ? "Trang trước" : "Trang sau"));
        return button;
    }

    private List<Integer> visiblePageIndexes(int pageCount) {
        if (pageCount <= 7) {
            List<Integer> pages = new ArrayList<>();
            for (int i = 0; i < pageCount; i++) {
                pages.add(i);
            }
            return pages;
        }

        int start = Math.max(1, currentPageIndex - 2);
        int end = Math.min(pageCount - 2, currentPageIndex + 2);
        List<Integer> pages = new ArrayList<>();
        pages.add(0);
        for (int i = start; i <= end; i++) {
            pages.add(i);
        }
        pages.add(pageCount - 1);
        return pages;
    }

    private void goToPage(int pageIndex) {
        int pageCount = getPageCount();
        if (pageIndex < 0 || pageIndex >= pageCount) {
            return;
        }
        currentPageIndex = pageIndex;
        updateHistoryPage();
        renderPagination();
    }

    private int getPageCount() {
        return Math.max(1, (int) Math.ceil(filteredRows.size() / (double) PAGE_SIZE));
    }

    @FXML
    private void handleRowClick(MouseEvent event) {
        BidRow selected = historyTable.getSelectionModel().getSelectedItem();
        viewDetailButton.setVisible(selected != null);
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && selected != null) {
            navigateToDetail(selected.auction());
        }
    }

    @FXML
    private void handleViewDetail(ActionEvent event) {
        BidRow selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected != null)
            navigateToDetail(selected.auction());
    }

    private void navigateToDetail(Auction auction) {
        try {
            NavigationManager.getInstance().navigateTo(
                    NavigationManager.AUCTION_DETAIL, "Chi tiết đấu giá", auction);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── WebSocket Live Updates ────────────────────────────────────────────────

    private void connectWebSocket() {
        wsClient = new AuctionClient();
        Thread t = new Thread(() -> {
            wsClient.connect(
                    msg -> Platform.runLater(() -> handleWsMessage(msg)),
                    err -> System.err.println("[BidHistory] WS Error: " + err));
        }, "BidHistory-WS");
        t.setDaemon(true);
        t.start();
    }

    private void handleWsMessage(String msg) {
        try {
            com.google.gson.JsonElement element = new com.google.gson.Gson().fromJson(msg, com.google.gson.JsonElement.class);
            if (!element.isJsonObject()) return;
            com.google.gson.JsonObject json = element.getAsJsonObject();
            if (!json.has("type"))
                return;

            String type = json.get("type").getAsString();
            if (type.equals("BID_UPDATE") || type.equals("AUCTION_STATUS_CHANGED") || type.equals("FULL_SYNC")) {
                loadHistory(); // Reload from server
            } else if (type.equals("BALANCE_UPDATE")) {
                String bidderId  = json.get("bidderId").getAsString();
                double newBalance = json.get("newBalance").getAsDouble();
                double frozen    = json.has("frozenBalance") ? json.get("frozenBalance").getAsDouble() : -1;

                User me = SessionManager.getInstance().getCurrentUser();
                if (me instanceof Bidder myBidder && myBidder.getId().equals(bidderId)) {
                    myBidder.setAccountBalance(newBalance);
                    if (frozen >= 0) myBidder.setFrozenBalance(frozen);
                    SessionManager.getInstance().setCurrentUser(myBidder);
                    if (currentBalanceLabel != null) {
                        double available = myBidder.getAvailableBalance();
                        double frozenAmt = myBidder.getFrozenBalance();
                        if (frozenAmt > 0) {
                            currentBalanceLabel.setText(String.format("%,.0f ₫  (đóng băng: %,.0f ₫)", available, frozenAmt));
                        } else {
                            currentBalanceLabel.setText(String.format("%,.0f ₫", available));
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[BidHistory] WS parse error: " + e.getMessage());
        }
    }

    public void cleanup() {
        if (wsClient != null) {
            wsClient.disconnect();
        }
    }
}
