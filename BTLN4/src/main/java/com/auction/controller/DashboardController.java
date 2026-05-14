package com.auction.controller;

import com.auction.model.*;
import com.auction.service.AppFacade;
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
 * DashboardController – system overview.
 * Uses AppFacade — no direct service/repository access.
 */
public class DashboardController {

    @FXML private Label welcomeLabel;
    @FXML private Label activeAuctionsLabel;  // RUNNING count
    @FXML private Label totalAuctionsLabel;
    @FXML private Label totalUsersLabel;
    @FXML private Label openAuctionsLabel;    // OPEN count
    @FXML private Label pendingAuctionsLabel; // PENDING count

    @FXML private TableView<Auction>           recentAuctionsTable;
    @FXML private TableColumn<Auction, String> colTitle;
    @FXML private TableColumn<Auction, String> colSeller;
    @FXML private TableColumn<Auction, String> colStatus;
    @FXML private TableColumn<Auction, String> colPrice;
    @FXML private TableColumn<Auction, String> colEndTime;

    private final AppFacade app = AppFacade.getInstance();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        setupTableColumns();
        loadData();
    }

    private void setupTableColumns() {
        colTitle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItem().getName()));
        colSeller.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSeller().getUsername()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatusDisplay()));
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%,.0f ₫", c.getValue().getHighestBid())));
        colEndTime.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEndTime().format(FMT)));
    }

    private void loadData() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null) {
            welcomeLabel.setText("Chào mừng, " + user.getUsername() + " (" + user.getRole() + ")");
        }

        List<Auction> all = app.getAllAuctions();
        long running = all.stream().filter(a -> a.getStatus() == AuctionStatus.RUNNING).count();
        long open    = all.stream().filter(a -> a.getStatus() == AuctionStatus.OPEN).count();
        long pending = all.stream().filter(a -> a.getStatus() == AuctionStatus.PENDING).count();

        activeAuctionsLabel.setText(String.valueOf(running));
        totalAuctionsLabel.setText(String.valueOf(all.size()));
        totalUsersLabel.setText(String.valueOf(app.getAllUsers().size()));
        openAuctionsLabel.setText(String.valueOf(open));
        if (pendingAuctionsLabel != null) pendingAuctionsLabel.setText(String.valueOf(pending));

        List<Auction> recent = all.subList(0, Math.min(all.size(), 10));
        recentAuctionsTable.setItems(FXCollections.observableArrayList(recent));
    }

    @FXML
    private void handleViewAllAuctions(ActionEvent event) {
        try {
            NavigationManager.getInstance().navigateTo(
                    NavigationManager.AUCTION_LIST, "Danh sách đấu giá", null);
        } catch (IOException e) { e.printStackTrace(); }
    }
}
