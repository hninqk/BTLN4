package com.auction.controller;

import com.auction.exception.InvalidBidException;
import com.auction.exception.InvalidStatusException;
import com.auction.model.*;
import com.auction.service.AuctionService;
import com.auction.util.DataReceiver;
import com.auction.util.ImageLoaderUtil;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * LiveBiddingController – real-time bidding screen.
 * Uses a scheduled executor to update timer and refresh bid feed.
 */
public class LiveBiddingController implements DataReceiver {

    @FXML
    private Label auctionTitleLabel;
    @FXML
    private Label auctionSubtitleLabel;
    @FXML
    private ImageView itemImageView;
    @FXML
    private Label statusBadge;
    @FXML
    private Label livePriceLabel;
    @FXML
    private Label liveTimerLabel;
    @FXML
    private Label bidCountLabel;
    @FXML
    private TextField bidAmountField;
    @FXML
    private Label minBidHint;
    @FXML
    private Label bidErrorLabel;
    @FXML
    private Button placeBidButton;
    @FXML
    private ListView<String> liveFeedList;
    @FXML
    private Label lastUpdateLabel;
    @FXML
    private LineChart<String, Number> priceChart;

    private Auction currentAuction;
    private ScheduledExecutorService scheduler;
    private XYChart.Series<String, Number> priceSeries;
    private int bidTickCounter = 0;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void receiveData(Object data) {
        if (data instanceof Auction) {
            currentAuction = (Auction) data;
            populateView();
            startLiveUpdates();
        }
    }

    @FXML
    public void initialize() {
        bidErrorLabel.setText("");
        setupChart();
    }

    private void setupChart() {
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Giá đấu");
        priceChart.getData().add(priceSeries);
        priceChart.setCreateSymbols(true);
        priceChart.setAnimated(false);
    }

    private void populateView() {
        if (currentAuction == null)
            return;
        auctionTitleLabel.setText(currentAuction.getItem().getName());
        itemImageView.setImage(ImageLoaderUtil.loadItemImage(currentAuction.getItem().getImageUrl(), 360, 220));
        auctionSubtitleLabel.setText("Người bán: " + currentAuction.getSeller().getUsername()
                + "  |  Kết thúc: " + currentAuction.getEndTime().format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        // Load existing bids into chart and feed
        List<BidTransaction> history = currentAuction.getBidHistory();
        for (BidTransaction bid : history) {
            addBidToChart(bid);
            addBidToFeed(bid);
        }

        refreshDisplay();

        User user = SessionManager.getInstance().getCurrentUser();
        boolean canBid = currentAuction.getStatus() == AuctionStatus.RUNNING
                && user instanceof Bidder;
        placeBidButton.setDisable(!canBid);
        bidAmountField.setDisable(!canBid);
    }

    private void startLiveUpdates() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LiveBidding-Scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> Platform.runLater(this::refreshDisplay), 0, 1, TimeUnit.SECONDS);
    }

    private void refreshDisplay() {
        if (currentAuction == null)
            return;
        livePriceLabel.setText(String.format("%,.0f ₫", currentAuction.getHighestBid()));
        bidCountLabel.setText(String.valueOf(currentAuction.getBidHistory().size()));
        minBidHint.setText("Giá tối thiểu: " +
                String.format("%,.0f ₫", currentAuction.getHighestBid() + 1));
        lastUpdateLabel.setText("Cập nhật: " + LocalDateTime.now().format(TIME_FMT));

        // Update timer
        Duration remaining = Duration.between(LocalDateTime.now(), currentAuction.getEndTime());
        if (remaining.isNegative()) {
            liveTimerLabel.setText("Đã kết thúc");
            if (scheduler != null && !scheduler.isShutdown())
                scheduler.shutdown();
        } else {
            liveTimerLabel.setText(String.format("%02d:%02d:%02d",
                    remaining.toHours(), remaining.toMinutesPart(), remaining.toSecondsPart()));
        }
    }

    private void addBidToChart(BidTransaction bid) {
        bidTickCounter++;
        priceSeries.getData().add(new XYChart.Data<>(
                "#" + bidTickCounter, bid.getAmount()));
    }

    private void addBidToFeed(BidTransaction bid) {
        String entry = String.format("[%s]  %s  →  %,.0f ₫",
                bid.getTimestamp().format(TIME_FMT),
                bid.getBidder().getUsername(),
                bid.getAmount());
        ObservableList<String> items = liveFeedList.getItems();
        items.add(0, entry); // newest first
        if (items.size() > 50)
            items.remove(50, items.size());
    }

    @FXML
    private void handlePlaceBid(ActionEvent event) {
        bidErrorLabel.setText("");
        String input = bidAmountField.getText().trim();
        if (input.isEmpty()) {
            bidErrorLabel.setText("Vui lòng nhập số tiền.");
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
            BidTransaction latest = currentAuction.getBidHistory()
                    .get(currentAuction.getBidHistory().size() - 1);
            addBidToChart(latest);
            addBidToFeed(latest);
            bidAmountField.clear();
            refreshDisplay();
        } catch (InvalidBidException e) {
            bidErrorLabel.setText("Lỗi: " + e.getMessage());
        } catch (InvalidStatusException e) {
            bidErrorLabel.setText("Phiên đấu giá không còn nhận đặt giá.");
        }
    }

    @FXML
    private void handleBack(ActionEvent event) {
        if (scheduler != null && !scheduler.isShutdown())
            scheduler.shutdown();
        try {
            NavigationManager.getInstance().navigateTo(
                    NavigationManager.AUCTION_DETAIL, "Chi tiết đấu giá", currentAuction);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
