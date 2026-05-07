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

    private NavigationManager() {}

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

    /**
     * Navigate to the given FXML screen (without passing data).
     * @param fxmlName  file name without extension, e.g. "Dashboard"
     */
    public void navigateTo(String fxmlName) throws IOException {
        navigateTo(fxmlName, null, null);
    }

    /**
     * Navigate and pass an optional data object to the next controller.
     */
    public void navigateTo(String fxmlName, String title, Object data) throws IOException {
        FXMLLoader loader = createLoader(fxmlName);
        Parent root = loader.load();

        // If the controller supports data injection, pass it
        Object controller = loader.getController();
        if (data != null && controller instanceof DataReceiver) {
            ((DataReceiver) controller).receiveData(data);
        }

        Scene scene = primaryStage.getScene();
        if (scene == null) {
            scene = new Scene(root, 1100, 700);
            scene.getStylesheets().add(
                    getClass().getResource("/com/auction/styles/main.css").toExternalForm()
            );
            primaryStage.setScene(scene);
        } else {
            scene.setRoot(root);
        }

        if (title != null) {
            primaryStage.setTitle("Hệ thống Đấu giá - " + title);
        }
        primaryStage.show();
    }

    /**
     * Create an FXMLLoader for the given screen name.
     */
    public FXMLLoader createLoader(String fxmlName) {
        return new FXMLLoader(
                getClass().getResource("/com/auction/" + fxmlName + ".fxml")
        );
    }

    // Screen name constants
    public static final String LOGIN       = "Login";
    public static final String REGISTER    = "Register";
    public static final String DASHBOARD   = "Dashboard";
    public static final String AUCTION_LIST   = "AuctionList";
    public static final String AUCTION_DETAIL = "AuctionDetail";
    public static final String LIVE_BIDDING   = "LiveBidding";
    public static final String SELLER_MGMT    = "SellerManagement";
    public static final String ADMIN_MGMT     = "AdminManagement";
    public static final String USER_PROFILE   = "UserProfile";
    public static final String HISTORY        = "BidHistory";
}
