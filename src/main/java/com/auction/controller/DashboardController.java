package com.auction.controller;

import com.auction.model.*;
import com.auction.service.AuctionService;
import com.auction.service.UserService;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * DashboardController – shows system overview stats and recent auctions.
 */
public class DashboardController {

    @FXML private Label     welcomeLabel;
    @FXML private Label     activeAuctionsLabel;
    @FXML private Label     totalAuctionsLabel;
    @FXML private Label     totalUsersLabel;
    @FXML private Label     openAuctionsLabel;

    @FXML private TableView<Auction>         recentAuctionsTable;
    @FXML private TableColumn<Auction, String> colTitle;
    @FXML private TableColumn<Auction, String> colSeller;
    @FXML private TableColumn<Auction, String> colStatus;
    @FXML private TableColumn<Auction, String> colPrice;
    @FXML private TableColumn<Auction, String> colEndTime;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        setupTableColumns();
        loadData();
    }

    private void setupTableColumns() {
        colTitle.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getItem().getName()));
        colSeller.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getSeller().getUsername()));
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatusDisplay()));
        colPrice.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("%,.0f ₫", c.getValue().getHighestBid())));
        colEndTime.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEndTime().format(FMT)));
    }

    private void loadData() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null) {
            welcomeLabel.setText("Chào mừng, " + user.getUsername() + " (" + user.getRole() + ")");
        }

        List<Auction> all = AuctionService.getInstance().getAllAuctions();
        long running  = all.stream().filter(a -> a.getStatus() == AuctionStatus.RUNNING).count();
        long open     = all.stream().filter(a -> a.getStatus() == AuctionStatus.OPEN).count();

        activeAuctionsLabel.setText(String.valueOf(running));
        totalAuctionsLabel.setText(String.valueOf(all.size()));
        totalUsersLabel.setText(String.valueOf(UserService.getInstance().getAllUsers().size()));
        openAuctionsLabel.setText(String.valueOf(open));

        // Show only last 10 auctions
        List<Auction> recent = all.subList(0, Math.min(all.size(), 10));
        ObservableList<Auction> items = FXCollections.observableArrayList(recent);
        recentAuctionsTable.setItems(items);
    }

    @FXML
    private void handleViewAllAuctions(ActionEvent event) {
        try {
            NavigationManager.getInstance().navigateTo(NavigationManager.AUCTION_LIST, "Danh sách đấu giá", null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
