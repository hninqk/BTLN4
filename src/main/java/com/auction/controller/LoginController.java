package com.auction.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {
    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    protected void handleLogin(ActionEvent event) {
        // Lấy dữ liệu người dùng nhập
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng nhập đầy đủ tài khoản và mật khẩu!");
            return;
        }

        // TODO: Chỗ này sau này bạn sẽ gọi vào Database hoặc UserService do teammate viết
        if (authenticate(username, password)) {
            try {
                switchToAuctionList(event);
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "Không thể tải giao diện: " + e.getMessage());
                e.printStackTrace();
            }

        } else {
            showAlert(Alert.AlertType.ERROR, "Lỗi đăng nhập", "Tên đăng nhập hoặc mật khẩu không chính xác!");
        }
    }

    /**
     * Hàm giả lập kiểm tra tài khoản (Mock data)
     */
    private boolean authenticate(String username, String password) {
        // Tạm thời fix cứng tài khoản là admin/123
        return "admin".equals(username) && "123".equals(password);
    }

    private void switchToAuctionList(ActionEvent event) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("/com/auction/AuctionList.fxml"));
        
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}