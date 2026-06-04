package com.auction.ui.util;

import com.auction.core.util.DataReceiver;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.kordamp.ikonli.javafx.FontIcon;
import java.io.IOException;

public class NavigationManager {

  private static NavigationManager instance;
  private Stage primaryStage;
  private Object currentController;
  private String currentScreen = DASHBOARD;
  private double dragOffsetX;
  private double dragOffsetY;
  private double normalX;
  private double normalY;
  private double normalWidth;
  private double normalHeight;
  private final BooleanProperty customMaximized = new SimpleBooleanProperty(false);

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

  public String getCurrentScreen() {
    return currentScreen;
  }

  public void navigateTo(String fxmlName) throws IOException {
    navigateTo(fxmlName, null, null);
  }

  public void navigateTo(String fxmlName, String title, Object data) throws IOException {

    if (currentController != null) {
      try {

        currentController.getClass().getMethod("cleanup").invoke(currentController);
      } catch (NoSuchMethodException ignored) {

      } catch (Exception e) {
        System.err.println("[NavigationManager] cleanup() error: " + e.getMessage());
      }
    }

    this.currentScreen = fxmlName;
    FXMLLoader loader = createLoader(fxmlName);
    Parent root = loader.load();
    currentController = loader.getController();

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

    Button maximizeButton = createWindowButton(customMaximized.get() ? "fas-window-restore" : "fas-window-maximize",
        customMaximized.get() ? "Khôi phục" : "Phóng to");
    maximizeButton.setOnAction(e -> {
      if (customMaximized.get()) {
        primaryStage.setX(normalX);
        primaryStage.setY(normalY);
        primaryStage.setWidth(normalWidth);
        primaryStage.setHeight(normalHeight);
        customMaximized.set(false);
      } else {
        normalX = primaryStage.getX();
        normalY = primaryStage.getY();
        normalWidth = primaryStage.getWidth();
        normalHeight = primaryStage.getHeight();

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        primaryStage.setX(bounds.getMinX());
        primaryStage.setY(bounds.getMinY());
        primaryStage.setWidth(bounds.getWidth());
        primaryStage.setHeight(bounds.getHeight());
        customMaximized.set(true);
      }
    });

    customMaximized.addListener((obs, oldVal, newVal) -> {
      FontIcon icon = new FontIcon(newVal ? "fas-window-restore" : "fas-window-maximize");
      icon.setIconSize(12);
      icon.getStyleClass().add("window-button-icon");
      maximizeButton.setGraphic(icon);
      maximizeButton.setAccessibleText(newVal ? "Khôi phục" : "Phóng to");
    });

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
      if (e.getButton() != MouseButton.PRIMARY || customMaximized.get()) return;
      primaryStage.setX(e.getScreenX() - dragOffsetX);
      primaryStage.setY(e.getScreenY() - dragOffsetY);
    });

    StackPane frame = new StackPane();
    frame.getStyleClass().add("window-frame");

    titleBar.setMaxHeight(38);
    titleBar.setPrefHeight(38);
    titleBar.setMinHeight(38);
    StackPane.setAlignment(titleBar, Pos.TOP_CENTER);

    if (content instanceof Region region) {
      region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
      StackPane.setMargin(region, new Insets(38, 0, 0, 0));
    }

    frame.getChildren().addAll(content, titleBar);
    titleBar.toFront();

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

  public FXMLLoader createLoader(String fxmlName) {
    return new FXMLLoader(
        getClass().getResource("/com/auction/" + fxmlName + ".fxml"));
  }

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
  public static final String LOGOUT = "Logout";
}
