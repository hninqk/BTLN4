package com.auction.controller;

import com.auction.model.User;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.animation.Interpolator;
import javafx.scene.CacheHint;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * SidebarController – navigation controller for the reusable sidebar component.
 * Strict Role-based visibility: Admin, Seller, and Bidder features are
 * completely isolated.
 */
public class SidebarController {

    private static final double EXPANDED_WIDTH = 220;
    private static final double COLLAPSED_WIDTH = 64;

    @FXML
    private VBox sidebarRoot;
    @FXML
    private VBox brandBox;
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
    private Button btnSettings;
    @FXML
    private Button btnLogout;
    @FXML
    private Label lblMain;
    @FXML
    private Label lblManagement;
    @FXML
    private Label lblAccount;

    private final Map<Button, String> navButtonTexts = new HashMap<>();
    private PauseTransition collapseTimer;
    private Timeline widthAnimation;
    private boolean collapsed;
    private boolean showManagementSection;

    @FXML
    public void initialize() {
        setupIcons();
        setupAutoCollapse();

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
    }

    private void setupIcons() {
        configureNavButton(btnDashboard, FontAwesomeSolid.TACHOMETER_ALT);
        configureNavButton(btnAuctionList, FontAwesomeSolid.GAVEL);
        configureNavButton(btnHistory, FontAwesomeSolid.HISTORY);
        configureNavButton(btnSeller, FontAwesomeSolid.BOX_OPEN);
        configureNavButton(btnAdmin, FontAwesomeSolid.USERS_COG);
        configureNavButton(btnProfile, FontAwesomeSolid.USER_CIRCLE);
        configureNavButton(btnSettings, FontAwesomeSolid.COG);
        configureNavButton(btnLogout, FontAwesomeSolid.SIGN_OUT_ALT);
    }

    private void configureNavButton(Button button, FontAwesomeSolid iconCode) {
        if (button == null) {
            return;
        }

        String text = button.getText();
        navButtonTexts.put(button, text);

        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(16);
        icon.getStyleClass().add("nav-icon");

        button.setGraphic(icon);
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(12);
        button.setTooltip(new Tooltip(text));
    }

    private void setupAutoCollapse() {
        if (sidebarRoot == null) {
            return;
        }

        collapseTimer = new PauseTransition(Duration.seconds(5));
        collapseTimer.setOnFinished(e -> setCollapsed(true));

        sidebarRoot.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            collapseTimer.stop();
            setCollapsed(false);
        });
        sidebarRoot.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            collapseTimer.stop();
            setCollapsed(false);
        });
        sidebarRoot.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            collapseTimer.stop();
            setCollapsed(false);
        });
        sidebarRoot.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            collapseTimer.playFromStart();
        });
    }

    private void restartCollapseTimer() {
        if (collapseTimer != null) {
            collapseTimer.playFromStart();
        }
    }

    private void setCollapsed(boolean collapse) {
        if (sidebarRoot == null || collapsed == collapse) {
            return;
        }
        collapsed = collapse;

        if (widthAnimation != null) {
            widthAnimation.stop();
        }

        // Enable hardware acceleration
        sidebarRoot.setCache(true);
        sidebarRoot.setCacheHint(CacheHint.SPEED);

        if (collapse) {
            if (!sidebarRoot.getStyleClass().contains("sidebar-collapsed")) {
                sidebarRoot.getStyleClass().add("sidebar-collapsed");
            }

            // Animate everything together in one smooth motion
            double targetWidth = COLLAPSED_WIDTH;
            Timeline collapseAnimation = new Timeline(
                new KeyFrame(Duration.millis(350),
                    // Width animation
                    new KeyValue(sidebarRoot.minWidthProperty(), targetWidth, Interpolator.EASE_BOTH),
                    new KeyValue(sidebarRoot.prefWidthProperty(), targetWidth, Interpolator.EASE_BOTH),
                    new KeyValue(sidebarRoot.maxWidthProperty(), targetWidth, Interpolator.EASE_BOTH),
                    // Fade out text elements
                    new KeyValue(brandBox.opacityProperty(), 0.0, Interpolator.EASE_OUT),
                    new KeyValue(userBox.opacityProperty(), 0.0, Interpolator.EASE_OUT),
                    new KeyValue(lblMain.opacityProperty(), 0.0, Interpolator.EASE_OUT),
                    new KeyValue(lblAccount.opacityProperty(), 0.0, Interpolator.EASE_OUT),
                    new KeyValue(lblManagement.opacityProperty(), 0.0, Interpolator.EASE_OUT)
                )
            );

            collapseAnimation.setOnFinished(e -> {
                hideElements();
                sidebarRoot.setCache(false);
            });
            collapseAnimation.play();

        } else {
            sidebarRoot.getStyleClass().remove("sidebar-collapsed");
            showElements();

            // Animate everything together in one smooth motion
            double targetWidth = EXPANDED_WIDTH;
            Timeline expandAnimation = new Timeline(
                new KeyFrame(Duration.millis(350),
                    // Width animation
                    new KeyValue(sidebarRoot.minWidthProperty(), targetWidth, Interpolator.EASE_BOTH),
                    new KeyValue(sidebarRoot.prefWidthProperty(), targetWidth, Interpolator.EASE_BOTH),
                    new KeyValue(sidebarRoot.maxWidthProperty(), targetWidth, Interpolator.EASE_BOTH),
                    // Fade in text elements
                    new KeyValue(brandBox.opacityProperty(), 1.0, Interpolator.EASE_IN),
                    new KeyValue(userBox.opacityProperty(), 1.0, Interpolator.EASE_IN),
                    new KeyValue(lblMain.opacityProperty(), 1.0, Interpolator.EASE_IN),
                    new KeyValue(lblAccount.opacityProperty(), 1.0, Interpolator.EASE_IN),
                    new KeyValue(lblManagement.opacityProperty(), 1.0, Interpolator.EASE_IN)
                )
            );

            expandAnimation.setOnFinished(e -> {
                sidebarRoot.setCache(false);
            });
            expandAnimation.play();
        }
    }


    private void hideElements() {
        // Only hide text after animation completes
        navButtonTexts.forEach((button, text) -> {
            button.setText("");
            button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            button.setGraphicTextGap(0);
        });
    }

    private void showElements() {
        // Set opacity to 0 initially for smooth fade-in
        brandBox.setOpacity(0.0);
        userBox.setOpacity(0.0);
        lblMain.setOpacity(0.0);
        lblAccount.setOpacity(0.0);
        lblManagement.setOpacity(0.0);

        navButtonTexts.forEach((button, text) -> {
            button.setText(text);
            button.setContentDisplay(ContentDisplay.LEFT);
            button.setGraphicTextGap(12);
        });
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
    private void handleSettings(ActionEvent event) {
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
