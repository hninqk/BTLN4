package com.auction.ui.controller;

import com.auction.core.model.Bidder;
import com.auction.core.model.User;
import com.auction.infra.util.NavigationManager;
import com.auction.infra.util.SessionManager;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;

import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * SidebarController – navigation controller for the reusable sidebar component.
 * Strict Role-based visibility: Admin, Seller, and Bidder features are
 * completely isolated.
 */
public class SidebarController extends BaseController {

    private static final double EXPANDED_WIDTH = 220;


    @FXML
    private VBox sidebarRoot;
    @FXML
    private HBox brandBox;
    @FXML
    private VBox userBox;
    @FXML
    private Label brandTitleLabel;
    @FXML
    private Label brandSubtitleLabel;
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
    private Button btnLogout;
    @FXML
    private Label lblMain;
    @FXML
    private Label lblManagement;
    @FXML
    private Label lblAccount;
    @FXML
    private Label balanceLabel;

    /** Kept so we can unregister if ever needed. */
    private Runnable sessionChangeListener;

    private final Map<Button, String> navButtonTexts = new HashMap<>();

    private boolean showManagementSection;

    @FXML
    public void initialize() {
        setupIcons();

        if (sidebarRoot != null) {
            sidebarRoot.setMinWidth(EXPANDED_WIDTH);
            sidebarRoot.setPrefWidth(EXPANDED_WIDTH);
            sidebarRoot.setMaxWidth(EXPANDED_WIDTH);
        }

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
            showManagementSection = isSeller || isAdmin;
            if (lblManagement != null) {
                lblManagement.setVisible(showManagementSection);
                lblManagement.setManaged(showManagementSection);
            }

            // BIDDER strictly sees Bid History
            btnHistory.setVisible(isBidder);
            btnHistory.setManaged(isBidder);
        }

        // Show balance pill for Bidder; update whenever session changes
        refreshBalance();
        sessionChangeListener = () -> Platform.runLater(this::refreshBalance);
        SessionManager.getInstance().addChangeListener(sessionChangeListener);

        // Set active tab based on current screen
        String currentScreen = nav.getCurrentScreen();
        setActiveTab(currentScreen);
    }

    private void setActiveTab(String screen) {
        Button[] allButtons = {btnDashboard, btnAuctionList, btnHistory, btnSeller, btnAdmin, btnProfile};
        for (Button b : allButtons) {
            if (b != null) {
                b.getStyleClass().remove("active-tab");
            }
        }

        Button activeBtn = switch (screen) {
            case NavigationManager.DASHBOARD -> btnDashboard;
            case NavigationManager.AUCTION_LIST -> btnAuctionList;
            case NavigationManager.HISTORY -> btnHistory;
            case NavigationManager.SELLER_MGMT -> btnSeller;
            case NavigationManager.ADMIN_MGMT -> btnAdmin;
            case NavigationManager.USER_PROFILE -> btnProfile;
            default -> null;
        };

        if (activeBtn != null) {
            activeBtn.getStyleClass().add("active-tab");
        }
    }

    /**
     * Refreshes the balance pill from the current session.
     * Safe to call from any thread (internally dispatches to FX thread if needed).
     * Shows the pill only for Bidder accounts; hides it for Seller / Admin.
     */
    public void refreshBalance() {
        if (balanceLabel == null) return;
        User user = SessionManager.getInstance().getCurrentUser();
        if (user instanceof Bidder bidder) {
            String formatted = String.format("💰 %,.0f ₫", bidder.getAccountBalance());
            balanceLabel.setText(formatted);
            balanceLabel.setVisible(true);
            balanceLabel.setManaged(true);
        } else {
            balanceLabel.setVisible(false);
            balanceLabel.setManaged(false);
        }
    }

    private void setupIcons() {
        configureNavButton(btnDashboard, FontAwesomeSolid.TACHOMETER_ALT);
        configureNavButton(btnAuctionList, FontAwesomeSolid.GAVEL);
        configureNavButton(btnHistory, FontAwesomeSolid.HISTORY);
        configureNavButton(btnSeller, FontAwesomeSolid.BOX_OPEN);
        configureNavButton(btnAdmin, FontAwesomeSolid.USERS_COG);
        configureNavButton(btnProfile, FontAwesomeSolid.USER_CIRCLE);
        configureNavButton(btnLogout, FontAwesomeSolid.SIGN_OUT_ALT);
    }

    private void configureNavButton(Button button, FontAwesomeSolid iconCode) {
        if (button == null) {
            return;
        }

        String text = button.getText();

        if (button == btnLogout) {
            text = "Đăng xuất";
        }

        navButtonTexts.put(button, text);

        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(16);
        icon.getStyleClass().add("nav-icon");

        button.setGraphic(icon);
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(12);
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
            nav.navigateTo(screen, title, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}