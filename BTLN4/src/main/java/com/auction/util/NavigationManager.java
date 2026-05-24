package com.auction.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;

/**
 * Centralized navigation manager.
 * All screen transitions go through this class.
 */
public class NavigationManager {

    private static NavigationManager instance;
    private Stage primaryStage;
    private Object currentController; // tracks the active controller for cleanup
    private boolean isDarkMode = true; // Persistent theme state
    private double dragOffsetX;
    private double dragOffsetY;

    private NavigationManager() {
    }

    public static NavigationManager getInstance() {
        if (instance == null) {
            instance = new NavigationManager();
        }
        return instance;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public boolean isDarkMode() {
        return isDarkMode;
    }

    public void setDarkMode(boolean darkMode) {
        this.isDarkMode = darkMode;
        if (primaryStage != null && primaryStage.getScene() != null && primaryStage.getScene().getRoot() != null) {
            applyDarkMode(primaryStage.getScene().getRoot(), darkMode);
        }
    }

    /**
     * Navigate to the given FXML screen (without passing data).
     * 
     * @param fxmlName file name without extension, e.g. "Dashboard"
     */
    public void navigateTo(String fxmlName) throws IOException {
        navigateTo(fxmlName, null, null);
    }

    /**
     * Navigate and pass an optional data object to the next controller.
     * Calls cleanup() on the previous controller (if it supports it) before switching.
     */
    public void navigateTo(String fxmlName, String title, Object data) throws IOException {
        // Cleanup the current controller before switching screens
        if (currentController != null) {
            try {
                // Reflectively call cleanup() if the controller has it
                currentController.getClass().getMethod("cleanup").invoke(currentController);
            } catch (NoSuchMethodException ignored) {
                // Controller doesn't have cleanup() — that's fine
            } catch (Exception e) {
                System.err.println("[NavigationManager] cleanup() error: " + e.getMessage());
            }
        }

        FXMLLoader loader = createLoader(fxmlName);
        Parent root = loader.load();
        currentController = loader.getController();

        // If the controller supports data injection, pass it
        if (data != null && currentController instanceof DataReceiver) {
            ((DataReceiver) currentController).receiveData(data);
        }

        Parent framedRoot = createWindowFrame(root, title);

        Scene scene = primaryStage.getScene();
        if (scene == null) {
            scene = new Scene(framedRoot, 1100, 700);
            scene.getStylesheets().add(
                    getClass().getResource("/com/auction/styles/main.css").toExternalForm());
            primaryStage.setScene(scene);
        } else {
            scene.setRoot(framedRoot);
        }

        applyDarkMode(framedRoot, isDarkMode);
        applyDarkMode(root, isDarkMode);

        if (title != null) {
            primaryStage.setTitle("Hệ thống Đấu giá - " + title);
        }
        
        primaryStage.setResizable(true);
        primaryStage.show();
    }

    private Parent createWindowFrame(Parent content, String title) {
        Label titleLabel = new Label("Hệ thống Đấu giá" + (title == null ? "" : " - " + title));
        titleLabel.getStyleClass().add("window-title-text");
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        FontIcon appIcon = new FontIcon("fas-gavel");
        appIcon.setIconSize(14);
        appIcon.getStyleClass().add("window-title-icon");

        Button minimizeButton = createWindowButton("fas-minus", "Thu nhỏ");
        minimizeButton.setOnAction(e -> primaryStage.setIconified(true));

        Button maximizeButton = createWindowButton("fas-expand-alt", "Phóng to");
        maximizeButton.setOnAction(e -> primaryStage.setMaximized(!primaryStage.isMaximized()));

        Button closeButton = createWindowButton("fas-times", "Đóng");
        closeButton.getStyleClass().add("window-close-button");
        closeButton.setOnAction(e -> primaryStage.fireEvent(
                new WindowEvent(primaryStage, WindowEvent.WINDOW_CLOSE_REQUEST)));

        HBox titleBar = new HBox(10, appIcon, titleLabel, minimizeButton, maximizeButton, closeButton);
        titleBar.getStyleClass().add("window-title-bar");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        titleBar.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            dragOffsetX = e.getSceneX();
            dragOffsetY = e.getSceneY();
        });
        titleBar.setOnMouseDragged(e -> {
            if (e.getButton() != MouseButton.PRIMARY || primaryStage.isMaximized()) return;
            primaryStage.setX(e.getScreenX() - dragOffsetX);
            primaryStage.setY(e.getScreenY() - dragOffsetY);
        });
        titleBar.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                primaryStage.setMaximized(!primaryStage.isMaximized());
            }
        });

        VBox frame = new VBox(titleBar, content);
        frame.getStyleClass().add("window-frame");
        if (content instanceof Region region) {
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }
        VBox.setVgrow(content, Priority.ALWAYS);
        return frame;
    }

    private Button createWindowButton(String iconLiteral, String tooltip) {
        Button button = new Button();
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(12);
        icon.getStyleClass().add("window-button-icon");
        button.setGraphic(icon);
        button.getStyleClass().add("window-title-button");
        button.setAccessibleText(tooltip);
        return button;
    }

    private void applyDarkMode(Parent root, boolean darkMode) {
        if (root == null) {
            return;
        }
        if (darkMode && !root.getStyleClass().contains("dark-mode")) {
            root.getStyleClass().add("dark-mode");
        } else if (!darkMode) {
            root.getStyleClass().remove("dark-mode");
        }
        for (javafx.scene.Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof Parent parent) {
                applyDarkMode(parent, darkMode);
            }
        }
    }

    /**
     * Create an FXMLLoader for the given screen name.
     */
    public FXMLLoader createLoader(String fxmlName) {
        return new FXMLLoader(
                getClass().getResource("/com/auction/" + fxmlName + ".fxml"));
    }

    // Screen name constants
    public static final String SPLASH = "Splash";
    public static final String LOGIN = "Login";
    public static final String REGISTER = "Register";
    public static final String DASHBOARD = "Dashboard";
    public static final String AUCTION_LIST = "AuctionList";
    public static final String AUCTION_DETAIL = "AuctionDetail";
    public static final String SELLER_MGMT = "SellerManagement";
    public static final String ADMIN_MGMT = "AdminManagement";
    public static final String USER_PROFILE = "UserProfile";
    public static final String HISTORY = "BidHistory";
    public static final String SETTINGS = "Settings";
    public static final String LOGOUT = "Logout";
}
