package com.auction.controller;

import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;
import com.auction.model.*;
import com.auction.service.AuctionService;
import com.auction.util.DataReceiver;
import com.auction.util.ImageLoaderUtil;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * AuctionDetailController – shows full auction detail + bid form + bid history.
 * Implements DataReceiver to accept an Auction object from navigation.
 */
public class AuctionDetailController implements DataReceiver {

    @FXML private Label     itemNameLabel;
    @FXML private Label     auctionIdLabel;
    @FXML private Label     statusBadge;
    @FXML private Label     nameLabel;
    @FXML private Label     categoryLabel;
    @FXML private Label     sellerLabel;
    @FXML private Label     startPriceLabel;
    @FXML private Label     startTimeLabel;
    @FXML private Label     endTimeLabel;
    @FXML private Label     descriptionLabel;
    @FXML private ImageView itemImageView;
    @FXML private Label     categoryInfoLabel;
    @FXML private Label     currentPriceLabel;
    @FXML private Label     bidCountLabel;
    @FXML private Label     timeRemainingLabel;
    @FXML private TextField bidAmountField;
    @FXML private Label     bidErrorLabel;
    @FXML private Button    placeBidButton;
    @FXML private javafx.scene.layout.VBox winnerBox;
    @FXML private Label     winnerLabel;
    @FXML private Label     winnerPriceLabel;

    @FXML private TableView<BidTransaction>           bidHistoryTable;
    @FXML private TableColumn<BidTransaction, String> colBidder;
    @FXML private TableColumn<BidTransaction, String> colAmount;
    @FXML private TableColumn<BidTransaction, String> colBidTime;

    private Auction currentAuction;
    private static final DateTimeFormatter FMT     = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_SEC = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    public void receiveData(Object data) {
        if (data instanceof Auction) {
            currentAuction = (Auction) data;
            populateView();
        }
    }

    @FXML
    public void initialize() {
        setupBidHistoryColumns();
        bidErrorLabel.setText("");
    }

    private void setupBidHistoryColumns() {
        colBidder.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getBidder().getUsername()));
        colAmount.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("%,.0f ₫", c.getValue().getAmount())));
        colBidTime.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTimestamp().format(FMT_SEC)));
    }

    private void populateView() {
        if (currentAuction == null) return;
        Item item = currentAuction.getItem();

        itemNameLabel.setText(item.getName());
        auctionIdLabel.setText("ID: " + currentAuction.getId());
        nameLabel.setText(item.getName());
        categoryLabel.setText(item.getCategory());
        sellerLabel.setText(currentAuction.getSeller().getUsername());
        startPriceLabel.setText(String.format("%,.0f ₫", item.getStartingPrice()));
        startTimeLabel.setText(currentAuction.getStartTime().format(FMT));
        endTimeLabel.setText(currentAuction.getEndTime().format(FMT));
        descriptionLabel.setText(item.getDescription());
        itemImageView.setImage(ImageLoaderUtil.loadItemImage(item.getImageUrl(), 420, 250));
        categoryInfoLabel.setText(item.getCategoryInfo());

        refreshBidPanel();
        refreshBidHistory();
        updateStatusBadge();

        // Winner display
        if (currentAuction.getStatus() == AuctionStatus.PAID) {
            BidTransaction winner = currentAuction.getWinner();
            if (winner != null) {
                winnerBox.setVisible(true);
                winnerBox.setManaged(true);
                winnerLabel.setText("Người thắng: " + winner.getBidder().getUsername());
                winnerPriceLabel.setText(String.format("Giá chốt: %,.0f ₫", winner.getAmount()));
            }
        }

        // Disable bid if not running or not bidder
        User user = SessionManager.getInstance().getCurrentUser();
        boolean canBid = currentAuction.getStatus() == AuctionStatus.RUNNING
                && user instanceof Bidder;
        placeBidButton.setDisable(!canBid);
        bidAmountField.setDisable(!canBid);
    }

    private void refreshBidPanel() {
        currentPriceLabel.setText(String.format("%,.0f ₫", currentAuction.getHighestBid()));
        bidCountLabel.setText(currentAuction.getBidHistory().size() + " lượt đấu giá");
        // Simple time remaining
        java.time.Duration remaining = java.time.Duration.between(
                java.time.LocalDateTime.now(), currentAuction.getEndTime());
        if (remaining.isNegative()) {
            timeRemainingLabel.setText("Đã kết thúc");
        } else {
            long hours   = remaining.toHours();
            long minutes = remaining.toMinutesPart();
            long seconds = remaining.toSecondsPart();
            timeRemainingLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        }
    }

    private void refreshBidHistory() {
        bidHistoryTable.setItems(FXCollections.observableArrayList(currentAuction.getBidHistory()));
    }

    private void updateStatusBadge() {
        AuctionStatus status = currentAuction.getStatus();
        statusBadge.setText(currentAuction.getStatusDisplay());
        statusBadge.getStyleClass().removeAll("badge-open", "badge-running", "badge-paid", "badge-canceled");
        switch (status) {
            case OPEN     -> statusBadge.getStyleClass().add("badge-open");
            case RUNNING  -> statusBadge.getStyleClass().add("badge-running");
            case PAID     -> statusBadge.getStyleClass().add("badge-paid");
            case CANCELED -> statusBadge.getStyleClass().add("badge-canceled");
        }
    }

    @FXML
    private void handlePlaceBid(ActionEvent event) {
        bidErrorLabel.setText("");
        String input = bidAmountField.getText().trim();
        if (input.isEmpty()) {
            bidErrorLabel.setText("Vui lòng nhập số tiền đặt giá.");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(input.replace(",", ""));
        } catch (NumberFormatException e) {
            bidErrorLabel.setText("Số tiền không hợp lệ.");
            return;
        }

        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Bidder bidder)) {
            bidErrorLabel.setText("Chỉ Bidder mới có thể đặt giá.");
            return;
        }

        try {
            AuctionService.getInstance().placeBid(currentAuction, bidder, amount);
            bidAmountField.clear();
            refreshBidPanel();
            refreshBidHistory();
        } catch (InvalidBidException e) {
            bidErrorLabel.setText("Lỗi: " + e.getMessage());
        } catch (InvalidStatusException e) {
            bidErrorLabel.setText("Phiên đấu giá không còn nhận đặt giá.");
        }
    }

    @FXML
    private void handleGoToLive(ActionEvent event) {
        try {
            NavigationManager.getInstance().navigateTo(
                    NavigationManager.LIVE_BIDDING, "Đấu giá trực tiếp", currentAuction);
        } catch (IOException e) {
            bidErrorLabel.setText("Lỗi: " + e.getMessage());
        }
    }

    @FXML
    private void handleBack(ActionEvent event) {
        try {
            NavigationManager.getInstance().navigateTo(NavigationManager.AUCTION_LIST, "Danh sách đấu giá", null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
