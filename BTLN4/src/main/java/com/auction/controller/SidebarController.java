package com.auction.controller;

import com.auction.model.User;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.io.IOException;

/**
 * SidebarController – navigation controller for the reusable sidebar component.
 * Strict Role-based visibility: Admin, Seller, and Bidder features are
 * completely isolated.
 */
public class SidebarController {

    @FXML
    private Label userNameLabel;
    @FXML
    private Label userRoleLabel;
    @FXML
    private Button btnDashboard;
    @FXML
    private Button btnAuctionList;
    @FXML
    private Button btnHistory;
    @FXML
    private Button btnSeller;
    @FXML
    private Button btnAdmin;
    @FXML
    private Button btnProfile;

    @FXML
    public void initialize() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null) {
            userNameLabel.setText(user.getUsername());
            userRoleLabel.setText(user.getRole());

            // Strict Role-based menu visibility (using equalsIgnoreCase for safety)
            boolean isSeller = "Seller".equalsIgnoreCase(user.getRole());
            boolean isAdmin = "Admin".equalsIgnoreCase(user.getRole());
            boolean isBidder = "Bidder".equalsIgnoreCase(user.getRole());

            // SELLER strictly sees Seller Management
            btnSeller.setVisible(isSeller);
            btnSeller.setManaged(isSeller);

            // ADMIN strictly sees Admin Management
            btnAdmin.setVisible(isAdmin);
            btnAdmin.setManaged(isAdmin);

            // BIDDER strictly sees Bid History
            btnHistory.setVisible(isBidder);
            btnHistory.setManaged(isBidder);
        }
    }

    @FXML
    private void handleDashboard(ActionEvent event) {
        navigate(NavigationManager.DASHBOARD, "Tổng quan");
    }

    @FXML
    private void handleAuctionList(ActionEvent event) {
        navigate(NavigationManager.AUCTION_LIST, "Danh sách đấu giá");
    }

    @FXML
    private void handleHistory(ActionEvent event) {
        navigate(NavigationManager.HISTORY, "Lịch sử đấu giá");
    }

    @FXML
    private void handleSeller(ActionEvent event) {
        navigate(NavigationManager.SELLER_MGMT, "Quản lý sản phẩm");
    }

    @FXML
    private void handleAdmin(ActionEvent event) {
        navigate(NavigationManager.ADMIN_MGMT, "Danh sách chờ duyệt");
    }

    @FXML
    private void handleProfile(ActionEvent event) {
        navigate(NavigationManager.USER_PROFILE, "Hồ sơ cá nhân");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        SessionManager.getInstance().logoutUser(); // Adjusted to match your SessionManager setup
        navigate(NavigationManager.LOGIN, "Đăng nhập");
    }

    private void navigate(String screen, String title) {
        try {
            NavigationManager.getInstance().navigateTo(screen, title, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}