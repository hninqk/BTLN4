package com.auction.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

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
            Parent root = primaryStage.getScene().getRoot();
            if (darkMode && !root.getStyleClass().contains("dark-mode")) {
                root.getStyleClass().add("dark-mode");
            } else if (!darkMode) {
                root.getStyleClass().remove("dark-mode");
            }
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

        Scene scene = primaryStage.getScene();
        if (scene == null) {
            scene = new Scene(root, 1100, 700);
            scene.getStylesheets().add(
                    getClass().getResource("/com/auction/styles/main.css").toExternalForm());
            primaryStage.setScene(scene);
        } else {
            scene.setRoot(root);
        }

        if (isDarkMode && !root.getStyleClass().contains("dark-mode")) {
            root.getStyleClass().add("dark-mode");
        }

        if (title != null) {
            primaryStage.setTitle("Hệ thống Đấu giá - " + title);
        }
        
        primaryStage.setResizable(true);
        primaryStage.show();
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
