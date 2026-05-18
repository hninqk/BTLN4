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
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BidHistoryController – Bidder's bid history.
 * Uses AppFacade — no direct service/repository access.
 */
public class BidHistoryController {

    @FXML private Label totalBidsLabel;
    @FXML private Label wonAuctionsLabel;
    @FXML private Label activeParticipationsLabel;
    @FXML private Label totalSpentLabel;

    @FXML private TextField        searchField;
    @FXML private ComboBox<String> resultFilter;
    @FXML private Label            statusLabel;
    @FXML private Button           viewDetailButton;

    @FXML private TableView<BidRow>           historyTable;
    @FXML private TableColumn<BidRow, String> colItem;
    @FXML private TableColumn<BidRow, String> colSeller;
    @FXML private TableColumn<BidRow, String> colMyBid;
    @FXML private TableColumn<BidRow, String> colFinalBid;
    @FXML private TableColumn<BidRow, String> colResult;
    @FXML private TableColumn<BidRow, String> colStatus;
    @FXML private TableColumn<BidRow, String> colBidTime;

    private final AppFacade app = AppFacade.getInstance();
    private List<BidRow> allRows;
    private AuctionClient wsClient;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public record BidRow(Auction auction, BidTransaction myBid, String result) {}

    @FXML
    public void initialize() {
        resultFilter.setItems(FXCollections.observableArrayList("Tất cả", "Thắng", "Thua", "Đang tham gia"));
        resultFilter.getSelectionModel().selectFirst();
        setupTableColumns();
        loadHistory();
        connectWebSocket();
    }

    private void setupTableColumns() {
        colItem.setCellValueFactory(c     -> new SimpleStringProperty(c.getValue().auction().getItem().getName()));
        colSeller.setCellValueFactory(c   -> new SimpleStringProperty(c.getValue().auction().getSeller().getUsername()));
        colMyBid.setCellValueFactory(c    -> new SimpleStringProperty(String.format("%,.0f ₫", c.getValue().myBid().getAmount())));
        colFinalBid.setCellValueFactory(c -> new SimpleStringProperty(String.format("%,.0f ₫", c.getValue().auction().getHighestBid())));
        colResult.setCellValueFactory(c   -> new SimpleStringProperty(c.getValue().result()));
        colStatus.setCellValueFactory(c   -> new SimpleStringProperty(c.getValue().auction().getStatusDisplay()));
        colBidTime.setCellValueFactory(c  -> new SimpleStringProperty(c.getValue().myBid().getTimestamp().format(FMT)));
    }

    private void loadHistory() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Bidder bidder)) {
            statusLabel.setText("Chỉ Bidder mới có lịch sử đấu giá."); 
            return;
        }

        allRows = new ArrayList<>();
        long won = 0, active = 0;
        double totalSpent = 0;

        for (Auction auction : app.getAllAuctions()) {
            Optional<BidTransaction> myLatest = auction.getBidHistory().stream()
                    .filter(b -> b.getBidder().getId().equals(bidder.getId()))
                    .reduce((first, second) -> second);
            if (myLatest.isEmpty()) continue;

            BidTransaction myBid = myLatest.get();
            String result;
            AuctionStatus status = auction.getStatus();
            if (status == AuctionStatus.RUNNING || status == AuctionStatus.OPEN || status == AuctionStatus.PENDING) {
                result = "Đang tham gia"; active++;
            } else {
                BidTransaction winner = auction.getWinner();
                if (winner != null && winner.getBidder().getId().equals(bidder.getId())) {
                    result = "🏆 Thắng"; won++; totalSpent += myBid.getAmount();
                } else {
                    result = "Thua";
                }
            }
            allRows.add(new BidRow(auction, myBid, result));
        }

        totalBidsLabel.setText(String.valueOf(allRows.size()));
        wonAuctionsLabel.setText(String.valueOf(won));
        activeParticipationsLabel.setText(String.valueOf(active));
        totalSpentLabel.setText(String.format("%,.0f ₫", totalSpent));
        historyTable.setItems(FXCollections.observableArrayList(allRows));
        statusLabel.setText("Tổng: " + allRows.size() + " lượt tham gia");
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        String keyword   = searchField.getText().trim().toLowerCase();
        String resultSel = resultFilter.getValue();
        List<BidRow> filtered = allRows.stream()
                .filter(r -> {
                    boolean matchName = keyword.isEmpty() || r.auction().getItem().getName().toLowerCase().contains(keyword);
                    boolean matchResult = resultSel == null || resultSel.equals("Tất cả") ||
                            r.result().contains(resultSel.replace("🏆 ", ""));
                    return matchName && matchResult;
                }).collect(Collectors.toList());
        historyTable.setItems(FXCollections.observableArrayList(filtered));
        statusLabel.setText("Kết quả: " + filtered.size() + " lượt");
    }

    @FXML private void handleReset(ActionEvent event) { searchField.clear(); resultFilter.getSelectionModel().selectFirst(); loadHistory(); }

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
        if (selected != null) navigateToDetail(selected.auction());
    }

    private void navigateToDetail(Auction auction) {
        try {
            NavigationManager.getInstance().navigateTo(
                    NavigationManager.AUCTION_DETAIL, "Chi tiết đấu giá", auction);
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ── WebSocket Live Updates ────────────────────────────────────────────────

    private void connectWebSocket() {
        wsClient = new AuctionClient();
        Thread t = new Thread(() -> {
            wsClient.connect(
                    msg -> Platform.runLater(() -> handleWsMessage(msg)),
                    err -> System.err.println("[BidHistory] WS Error: " + err)
            );
        }, "BidHistory-WS");
        t.setDaemon(true);
        t.start();
    }

    private void handleWsMessage(String msg) {
        try {
            com.google.gson.JsonObject json = new com.google.gson.Gson().fromJson(msg, com.google.gson.JsonObject.class);
            if (!json.has("type")) return;
            
            String type = json.get("type").getAsString();
            if (type.equals("BID_UPDATE") || type.equals("AUCTION_STATUS_CHANGED") || type.equals("FULL_SYNC")) {
                loadHistory(); // Reload from server
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

