package com.auction.ui.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

public class AlertHelper {

    /**
     * Khởi tạo một Alert cơ bản có nhúng giao diện (CSS) từ main.css
     */
    public static Alert createAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        
        javafx.scene.layout.HBox headerBox = new javafx.scene.layout.HBox(10);
        headerBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(
            type == Alert.AlertType.ERROR ? org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.TIMES_CIRCLE :
            type == Alert.AlertType.WARNING ? org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.EXCLAMATION_TRIANGLE :
            org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.INFO_CIRCLE
        );
        icon.setIconSize(24);
        icon.setIconColor(javafx.scene.paint.Color.web(
            type == Alert.AlertType.ERROR ? "#DC2626" :
            type == Alert.AlertType.WARNING ? "#D97706" : "#2563EB"
        ));
        
        javafx.scene.control.Label titleLabel = new javafx.scene.control.Label(title);
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1E293B;");
        
        headerBox.getChildren().addAll(icon, titleLabel);
        alert.getDialogPane().setHeader(headerBox);
        
        javafx.scene.control.Label messageLabel = new javafx.scene.control.Label(content);
        messageLabel.setWrapText(true);
        messageLabel.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        messageLabel.setPrefWidth(380); // Đặt width cố định để bắt buộc text wrap
        
        alert.getDialogPane().setContent(messageLabel);
        applyCustomStyle(alert);
        return alert;
    }

    /**
     * Khởi tạo Alert dạng Confirmation (Xác nhận) với các nút tuỳ chỉnh
     */
    public static Alert createConfirmation(String title, String content, ButtonType... buttons) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, content, buttons);
        alert.setTitle(title);
        
        // Tạo custom header với Icon và Title in đậm
        javafx.scene.layout.HBox headerBox = new javafx.scene.layout.HBox(10);
        headerBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon(org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.QUESTION_CIRCLE);
        icon.setIconSize(24);
        icon.setIconColor(javafx.scene.paint.Color.web("#2563EB"));
        
        javafx.scene.control.Label titleLabel = new javafx.scene.control.Label(title);
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1E293B;");
        
        headerBox.getChildren().addAll(icon, titleLabel);
        alert.getDialogPane().setHeader(headerBox);
        
        javafx.scene.control.Label messageLabel = new javafx.scene.control.Label(content);
        messageLabel.setWrapText(true);
        messageLabel.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        messageLabel.setPrefWidth(380);
        
        alert.getDialogPane().setContent(messageLabel);
        applyCustomStyle(alert);
        return alert;
    }

    /**
     * Hiển thị nhanh một Alert và chờ người dùng đóng
     */
    public static void showAlert(Alert.AlertType type, String title, String content) {
        createAlert(type, title, null, content).showAndWait();
    }

    public static void showError(String title, String content) {
        showAlert(Alert.AlertType.ERROR, title, content);
    }

    public static void showInfo(String title, String content) {
        showAlert(Alert.AlertType.INFORMATION, title, content);
    }

    private static void applyCustomStyle(Alert alert) {
        alert.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        DialogPane dialogPane = alert.getDialogPane();
        
        // Sửa lỗi text bị cắt (...) bằng cách set min height và bật wrap text
        dialogPane.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        
        try {
            String cssPath = AlertHelper.class.getResource("/com/auction/styles/main.css").toExternalForm();
            dialogPane.getStylesheets().add(cssPath);
            dialogPane.getStyleClass().add("modern-alert");

            if (dialogPane.getScene() != null) {
                dialogPane.getScene().setFill(javafx.scene.paint.Color.TRANSPARENT);
            } else {
                dialogPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                    if (newScene != null) {
                        newScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Could not load alert CSS: " + e.getMessage());
        }
    }
}
