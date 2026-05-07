package com.auction.controller;

import com.auction.service.UserService;
import com.auction.util.NavigationManager;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.IOException;

/**
 * RegisterController – handles new user registration.
 */
public class RegisterController {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private ComboBox<String> roleCombo;
    @FXML
    private Label errorLabel;
    @FXML
    private Label successLabel;

    @FXML
    public void initialize() {
        roleCombo.setItems(FXCollections.observableArrayList("Bidder", "Seller"));
        roleCombo.getSelectionModel().selectFirst();
        errorLabel.setText("");
        successLabel.setText("");
    }

    @FXML
    private void handleRegister(ActionEvent event) {
        errorLabel.setText("");
        successLabel.setText("");

        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();
        String role = roleCombo.getValue();

        // Validation
        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Vui lòng điền đầy đủ thông tin bắt buộc (*).");
            return;
        }
        if (username.length() < 4) {
            errorLabel.setText("Tên đăng nhập phải có ít nhất 4 ký tự.");
            return;
        }
        if (password.length() < 6) {
            errorLabel.setText("Mật khẩu phải có ít nhất 6 ký tự.");
            return;
        }
        if (!password.equals(confirm)) {
            errorLabel.setText("Mật khẩu xác nhận không khớp.");
            return;
        }

        boolean ok = UserService.getInstance().register(username, password, role);
        if (ok) {
            successLabel.setText("Đăng ký thành công! Bạn có thể đăng nhập ngay.");
            clearForm();
        } else {
            errorLabel.setText("Tên đăng nhập đã tồn tại. Vui lòng chọn tên khác.");
        }
    }

    @FXML
    private void handleGoToLogin(ActionEvent event) {
        try {
            NavigationManager.getInstance().navigateTo(NavigationManager.LOGIN, "Đăng nhập", null);
        } catch (IOException e) {
            errorLabel.setText("Lỗi hệ thống: " + e.getMessage());
        }
    }

    private void clearForm() {
        usernameField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
        roleCombo.getSelectionModel().selectFirst();
    }
}
