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

    private Button[] navButtons;

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

        collapseTimer = new PauseTransition(Duration.seconds(1));
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
            collapseTimer.stop();
            setCollapsed(true);
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

        if (collapse) {
            if (!sidebarRoot.getStyleClass().contains("sidebar-collapsed")) {
                sidebarRoot.getStyleClass().add("sidebar-collapsed");
            }
            animateSidebarWidth(COLLAPSED_WIDTH, () -> {
                setInfoVisible(brandBox, false);
                setInfoVisible(userBox, false);
                setLabelVisible(lblMain, false);
                setLabelVisible(lblAccount, false);
                setLabelVisible(lblManagement, false);
            });
        } else {
            sidebarRoot.getStyleClass().remove("sidebar-collapsed");
            setInfoVisible(brandBox, true);
            setInfoVisible(userBox, true);
            setLabelVisible(lblMain, true);
            setLabelVisible(lblAccount, true);
            setLabelVisible(lblManagement, showManagementSection);
            animateSidebarWidth(EXPANDED_WIDTH, null);
        }

        navButtonTexts.forEach((button, text) -> {
            button.setText(collapse ? "" : text);
            button.setContentDisplay(ContentDisplay.LEFT);
            button.setGraphicTextGap(collapse ? 0 : 12);
        });
    }

    private void animateSidebarWidth(double targetWidth, Runnable onFinished) {
        widthAnimation = new Timeline(
                new KeyFrame(Duration.millis(180),
                        new KeyValue(sidebarRoot.minWidthProperty(), targetWidth),
                        new KeyValue(sidebarRoot.prefWidthProperty(), targetWidth),
                        new KeyValue(sidebarRoot.maxWidthProperty(), targetWidth))
        );
        widthAnimation.setOnFinished(e -> {
            sidebarRoot.setMinWidth(targetWidth);
            sidebarRoot.setPrefWidth(targetWidth);
            sidebarRoot.setMaxWidth(targetWidth);
            if (onFinished != null) {
                onFinished.run();
            }
        });
        widthAnimation.play();
    }

    private void setInfoVisible(VBox box, boolean visible) {
        if (box != null) {
            fadeNode(box, visible);
            box.setVisible(visible);
            box.setManaged(visible);
        }
    }

    private void setLabelVisible(Label label, boolean visible) {
        if (label != null) {
            fadeNode(label, visible);
            label.setVisible(visible);
            label.setManaged(visible);
        }
    }

    private void fadeNode(Node node, boolean visible) {
        node.setOpacity(visible ? 1.0 : 0.0);
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
