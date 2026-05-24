package com.auction.controller;

import com.auction.model.User;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.Parent;

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
    private Button btnSettings;
    @FXML
    private Label lblManagement;

    private Button[] navButtons;

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

            // Hide Management label if Bidder
            boolean showManagement = isSeller || isAdmin;
            if (lblManagement != null) {
                lblManagement.setVisible(showManagement);
                lblManagement.setManaged(showManagement);
            }

            // BIDDER strictly sees Bid History
            btnHistory.setVisible(isBidder);
            btnHistory.setManaged(isBidder);
        }

        // Initialize the navButtons array
        navButtons = new Button[]{btnDashboard, btnAuctionList, btnHistory, btnSeller, btnAdmin, btnProfile, btnSettings};

        // Determine the current active screen, defaulting to DASHBOARD if not set
        String currentScreen = NavigationManager.getInstance().getCurrentScreen();
        if (currentScreen == null) {
            currentScreen = NavigationManager.DASHBOARD;
        }
        highlightActiveButton(currentScreen);
    }

    private void highlightActiveButton(String currentScreen) {
        if (navButtons == null) {
            return;
        }
        
        // Remove 'active' class from all buttons
        for (Button btn : navButtons) {
            if (btn != null) {
                btn.getStyleClass().remove("active");
            }
        }
        
        // Match screen with the corresponding button
        Button targetButton = null;
        if (currentScreen != null) {
            switch (currentScreen) {
                case NavigationManager.DASHBOARD -> targetButton = btnDashboard;
                case NavigationManager.AUCTION_LIST -> targetButton = btnAuctionList;
                case NavigationManager.HISTORY -> targetButton = btnHistory;
                case NavigationManager.SELLER_MGMT -> targetButton = btnSeller;
                case NavigationManager.ADMIN_MGMT -> targetButton = btnAdmin;
                case NavigationManager.USER_PROFILE -> targetButton = btnProfile;
                case NavigationManager.SETTINGS -> targetButton = btnSettings;
                default -> {
                    targetButton = btnDashboard;
                }
            }
        } else {
            targetButton = btnDashboard;
        }

        // Apply 'active' class to target button
        if (targetButton != null) {
            if (!targetButton.getStyleClass().contains("active")) {
                targetButton.getStyleClass().add("active");
            }
        }
    }

    private void setActiveButton(Button activeButton) {
        if (navButtons == null) {
            navButtons = new Button[]{btnDashboard, btnAuctionList, btnHistory, btnSeller, btnAdmin, btnProfile, btnSettings};
        }
        for (Button btn : navButtons) {
            if (btn != null) {
                btn.getStyleClass().remove("active");
            }
        }
        if (activeButton != null) {
            if (!activeButton.getStyleClass().contains("active")) {
                activeButton.getStyleClass().add("active");
            }
        }
    }

    @FXML
    private void handleDashboard(ActionEvent event) {
        setActiveButton(btnDashboard);
        navigate(NavigationManager.DASHBOARD, "Tổng quan");
    }

    @FXML
    private void handleAuctionList(ActionEvent event) {
        setActiveButton(btnAuctionList);
        navigate(NavigationManager.AUCTION_LIST, "Danh sách đấu giá");
    }

    @FXML
    private void handleHistory(ActionEvent event) {
        setActiveButton(btnHistory);
        navigate(NavigationManager.HISTORY, "Lịch sử đấu giá");
    }

    @FXML
    private void handleSeller(ActionEvent event) {
        setActiveButton(btnSeller);
        navigate(NavigationManager.SELLER_MGMT, "Quản lý sản phẩm");
    }

    @FXML
    private void handleAdmin(ActionEvent event) {
        setActiveButton(btnAdmin);
        navigate(NavigationManager.ADMIN_MGMT, "Danh sách chờ duyệt");
    }

    @FXML
    private void handleProfile(ActionEvent event) {
        setActiveButton(btnProfile);
        navigate(NavigationManager.USER_PROFILE, "Hồ sơ cá nhân");
    }

    @FXML
    private void handleSettings(ActionEvent event) {
        setActiveButton(btnSettings);
        navigate(NavigationManager.SETTINGS, "Cài đặt");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        // Prevent multiple clicks and give immediate visual feedback
        if (event.getSource() instanceof javafx.scene.Node node) {
            if (node.getScene() != null && node.getScene().getRoot() != null) {
                node.getScene().getRoot().setDisable(true);
                node.getScene().getRoot().setOpacity(0.8);
            }
        }
        
        // Defer the scene-switch to let the button animation finish smoothly
        javafx.application.Platform.runLater(() -> {
            navigate(NavigationManager.LOGOUT, "Đang đăng xuất...");
        });
    }

    private void navigate(String screen, String title) {
        try {
            NavigationManager.getInstance().navigateTo(screen, title, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}