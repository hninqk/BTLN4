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
 * Role-based visibility: shows Admin/Seller menus only when appropriate.
 */
public class SidebarController {

    @FXML private Label  userNameLabel;
    @FXML private Label  userRoleLabel;
    @FXML private Button btnDashboard;
    @FXML private Button btnAuctionList;
    @FXML private Button btnHistory;
    @FXML private Button btnSeller;
    @FXML private Button btnAdmin;
    @FXML private Button btnProfile;

    @FXML
    public void initialize() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null) {
            userNameLabel.setText(user.getUsername());
            userRoleLabel.setText(user.getRole());

            // Role-based menu visibility
            boolean isSeller = "Seller".equals(user.getRole());
            boolean isAdmin  = "Admin".equals(user.getRole());

            btnSeller.setVisible(isSeller || isAdmin);
            btnSeller.setManaged(isSeller || isAdmin);
            btnAdmin.setVisible(isAdmin);
            btnAdmin.setManaged(isAdmin);
            btnHistory.setVisible(!isAdmin);
            btnHistory.setManaged(!isAdmin);
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
        navigate(NavigationManager.ADMIN_MGMT, "Quản trị hệ thống");
    }

    @FXML
    private void handleProfile(ActionEvent event) {
        navigate(NavigationManager.USER_PROFILE, "Hồ sơ cá nhân");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        SessionManager.getInstance().logout();
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
