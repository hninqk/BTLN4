package com.auction.ui.controller;

import com.auction.ui.support.logic.BidHistoryService;
import com.auction.ui.support.dto.BidHistoryStats;
import com.auction.ui.support.dto.BidRow;
import com.auction.ui.support.logic.DefaultBidHistoryService;
import com.auction.core.model.*;
import com.auction.infra.util.NavigationManager;
import com.auction.infra.util.SessionManager;
import com.google.gson.JsonObject;
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

/**
 * BidHistoryController – Bidder's bid history.
 * Uses AppFacade — no direct service/repository access.
 */
public class BidHistoryController extends RealtimeController {

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

    private static final int PAGE_SIZE = 10;
    private List<BidRow> allRows = Collections.emptyList();
    private List<BidRow> filteredRows = Collections.emptyList();
    private int currentPageIndex;
    private final BidHistoryService historyService = new DefaultBidHistoryService();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public BidHistoryController() {
        super("BidHistory-WS", "[BidHistory]");
    }

    /**
     * Warm the history cache for a single bidder only.
     * Called by LoginController after the user authenticates — avoids building
     * a cache for every user in the system on a single-machine deployment.
     */
    public static void preloadCacheForUser(java.util.List<Auction> fullAuctions, String bidderId) {
        new DefaultBidHistoryService().preload(fullAuctions, bidderId);
    }

    /** Clear all cached history — call on logout so the next user gets fresh data. */
    public static void clearCache() {
        new DefaultBidHistoryService().clearCache();
    }

    @FXML
    public void initialize() {
        DesktopHeaderController.setTitleAndSubtitle("Lịch sử đấu giá", null);
        resultFilter.setItems(FXCollections.observableArrayList("Tất cả", "Thắng", "Thua", "Đang tham gia"));
        resultFilter.getSelectionModel().selectFirst();
        setupActionIcons();
        setupTableColumns();
        loadHistory();
        setupRealtime();
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
            case "Sắp diễn ra"  -> "badge-status-open";
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

        BidHistoryStats cache = historyService.getCachedStats(bidder.getId());
        if (cache != null) {
            allRows = cache.rows() == null ? Collections.emptyList() : cache.rows();
            totalBidsLabel.setText(cache.totalBids());
            wonAuctionsLabel.setText(cache.won());
            activeParticipationsLabel.setText(cache.active());
            totalSpentLabel.setText(cache.spent());
            applyFilters(true);
        } else {
            statusLabel.setText("Đang tải dữ liệu từ server...");
        }

        taskRunner.run("bid-history-load", () -> historyService.fetchHistory(app, bidder), rows -> {
            allRows = rows == null ? Collections.emptyList() : rows;
            BidHistoryStats stats = historyService.calculateStats(allRows);

            totalBidsLabel.setText(stats.totalBids());
            wonAuctionsLabel.setText(stats.won());
            activeParticipationsLabel.setText(stats.active());
            totalSpentLabel.setText(stats.spent());

            applyFilters(true);
            statusLabel.setText("");
        }, error -> statusLabel.setText("Không thể tải lịch sử đấu giá."));
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        applyFilters(true);
    }

    private void applyFilters(boolean resetPage) {
        filteredRows = historyService.filter(allRows, searchField.getText(), resultFilter.getValue());

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
            nav.navigateTo(
                    NavigationManager.AUCTION_DETAIL, "Chi tiết đấu giá", auction);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── WebSocket Live Updates ────────────────────────────────────────────────

    @Override
    protected void handleWsMessage(JsonObject json) {
        if (!json.has("type"))
            return;

        String type = json.get("type").getAsString();
        if (type.equals("BID_UPDATE") || type.equals("AUCTION_STATUS_CHANGED") || type.equals("FULL_SYNC")) {
            Platform.runLater(this::loadHistory); // Reload from server
        } else if (type.equals("BALANCE_UPDATE")) {
            String bidderId = json.get("bidderId").getAsString();
            double newBalance = json.get("newBalance").getAsDouble();
            double frozen = json.has("frozenBalance") ? json.get("frozenBalance").getAsDouble() : -1;

            User me = SessionManager.getInstance().getCurrentUser();
            if (me instanceof Bidder myBidder && myBidder.getId().equals(bidderId)) {
                myBidder.setAccountBalance(newBalance);
                if (frozen >= 0)
                    myBidder.setFrozenBalance(frozen);
                SessionManager.getInstance().setCurrentUser(myBidder);
                Platform.runLater(() -> {
                    if (currentBalanceLabel != null) {
                        double available = myBidder.getAvailableBalance();
                        double frozenAmt = myBidder.getFrozenBalance();
                        if (frozenAmt > 0) {
                            currentBalanceLabel.setText(String.format("%,.0f ₫  (đóng băng: %,.0f ₫)", available, frozenAmt));
                        } else {
                            currentBalanceLabel.setText(String.format("%,.0f ₫", available));
                        }
                    }
                });
            }
        }
    }
}
