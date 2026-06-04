package com.auction.ui.controller;

import com.auction.ui.support.logic.AuctionFilterService;
import com.auction.ui.support.logic.DefaultAuctionFilterService;
import com.auction.core.model.Auction;
import com.auction.core.model.Item;
import com.auction.core.model.User;
import com.auction.ui.util.NavigationManager;
import com.auction.core.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuctionListController extends RealtimeController {

    public AuctionListController() {
        super("AuctionList-WS", "[AuctionList]");
    }

    @FXML private TextField           searchField;

    @FXML private ComboBox<String>    statusFilter;

    @FXML private ComboBox<String>    categoryFilter;

    @FXML private Label               statusLabel;

    @FXML private HBox                paginationBox;

    @FXML private Button              searchButton;

    @FXML private Button              resetButton;

    @FXML private TableView<Auction>             auctionTable;

    @FXML private TableColumn<Auction, String> colTitle;

    @FXML private TableColumn<Auction, String> colCategory;

    @FXML private TableColumn<Auction, String> colStatus;

    @FXML private TableColumn<Auction, String> colPrice;

    @FXML private TableColumn<Auction, String> colEndTime;

    private static final int PAGE_SIZE = 10;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AuctionFilterService filterService = new DefaultAuctionFilterService();

    private List<Auction> allAuctions = Collections.emptyList();

    private List<Auction> filteredAuctions = Collections.emptyList();

    private int currentPageIndex;

    @FXML

    public void initialize() {
        DesktopHeaderController.setTitleAndSubtitle("Danh sách đấu giá", null);
        setupFilters();
        setupActionIcons();
        setupTableColumns();
        loadAuctions();
        setupRealtime();
    }

    @Override

    protected void handleWsMessage(JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "";
        if ("AUCTION_STATUS_CHANGED".equals(type) || "AUCTION_CREATED".equals(type)) {
            Platform.runLater(this::loadAuctions);
        }
    }

    private void setupActionIcons() {
        setButtonIcon(searchButton, FontAwesomeSolid.SEARCH);
        setButtonIcon(resetButton, FontAwesomeSolid.REDO_ALT);
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

    private boolean isAdmin() {
        User u = SessionManager.getInstance().getCurrentUser();
        return u != null && "Admin".equalsIgnoreCase(u.getRole());
    }

    private void setupFilters() {
        if (isAdmin()) {
            statusFilter.setItems(FXCollections.observableArrayList(
                    "Tất cả", "Chờ duyệt", "Sắp diễn ra", "Đang diễn ra", "Đã đóng", "Đã huỷ"));
        } else {
            statusFilter.setItems(FXCollections.observableArrayList(
                    "Tất cả", "Sắp diễn ra", "Đang diễn ra", "Đã đóng"));
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
            if (item instanceof com.auction.core.model.Electronics) cat = "Điện tử";
            else if (item instanceof com.auction.core.model.Art) cat = "Nghệ thuật";
            else if (item instanceof com.auction.core.model.Vehicle) cat = "Xe cộ";
            return new SimpleStringProperty(cat);
        });
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatusDisplay()));
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%,.0f ₫", c.getValue().getHighestBid())));
        colEndTime.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEndTime().format(FMT)));

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

        taskRunner.run("auction-list-load", () -> {
            if (admin) {
                return app.getAllAuctions();
            } else {
                return app.getPublicAuctions();
            }
        }, auctions -> {
            allAuctions = auctions == null ? Collections.emptyList() : auctions;
            applyFilters(true);
        }, error -> {
            statusLabel.setText("Lỗi tải dữ liệu: " + error.getMessage());
        });
    }

    @FXML

    private void handleSearch(ActionEvent event) {
        applyFilters(true);
    }

    private void applyFilters(boolean resetPage) {
        filteredAuctions = filterService.filterAuctions(
                allAuctions, searchField.getText(), statusFilter.getValue(), categoryFilter.getValue());

        int pageCount = getPageCount();
        if (resetPage) {
            currentPageIndex = 0;
        } else if (currentPageIndex >= pageCount) {
            currentPageIndex = Math.max(0, pageCount - 1);
        }

        updateAuctionPage();
        renderPagination();
    }

    @FXML

    private void handleReset(ActionEvent event) {
        searchField.clear();
        statusFilter.getSelectionModel().selectFirst();
        categoryFilter.getSelectionModel().selectFirst();
        applyFilters(true);
    }

    private void updateAuctionPage() {
        int total = filteredAuctions.size();
        int pageCount = getPageCount();
        int fromIndex = Math.min(currentPageIndex * PAGE_SIZE, total);
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);

        List<Auction> pageRows = total == 0
                ? Collections.emptyList()
                : filteredAuctions.subList(fromIndex, toIndex);

        auctionTable.setItems(FXCollections.observableArrayList(pageRows));
        auctionTable.getSelectionModel().clearSelection();

        if (total == 0) {
            statusLabel.setText("Không có phiên phù hợp.");
        } else {
            statusLabel.setText("Kết quả: " + total + " phiên đấu giá | Trang "
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
        updateAuctionPage();
        renderPagination();
    }

    private int getPageCount() {
        return Math.max(1, (int) Math.ceil(filteredAuctions.size() / (double) PAGE_SIZE));
    }

    @FXML

    private void handleRowClick(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            Auction selected = auctionTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                statusLabel.setText("Đang tải chi tiết phiên đấu giá...");
                taskRunner.run("fetch-detail-" + selected.getId(), () -> {
                        return app.findAuctionById(selected.getId()).orElse(selected);
                }, auction -> {
                    statusLabel.setText("Tải thành công.");
                    try {
                        nav.navigateTo(
                                NavigationManager.AUCTION_DETAIL, "Chi tiết đấu giá", auction);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }, error -> {
                    statusLabel.setText("Lỗi tải chi tiết: " + error.getMessage());
                });
            }
        }
    }
}
