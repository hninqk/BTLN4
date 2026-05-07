package com.auction.controller;

import com.auction.model.User;
import com.auction.service.UserService;
import com.auction.util.NavigationManager;
import com.auction.util.SessionManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.util.Optional;

/**
 * LoginController – handles user authentication.
 * Delegates business logic to UserService.
 */
public class LoginController {

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;

    @FXML
    public void initialize() {
        errorLabel.setText("");
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        errorLabel.setText("");
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
            return;
        }

        Optional<User> result = UserService.getInstance().login(username, password);
        if (result.isPresent()) {
            SessionManager.getInstance().setCurrentUser(result.get());
            try {
                NavigationManager.getInstance().navigateTo(NavigationManager.DASHBOARD, "Tổng quan", null);
            } catch (IOException e) {
                errorLabel.setText("Lỗi hệ thống: không thể tải giao diện.");
                e.printStackTrace();
            }
        } else {
            errorLabel.setText("Tên đăng nhập hoặc mật khẩu không chính xác.");
            passwordField.clear();
        }
    }

    @FXML
    private void handleGoToRegister(ActionEvent event) {
        try {
            NavigationManager.getInstance().navigateTo(NavigationManager.REGISTER, "Đăng ký", null);
        } catch (IOException e) {
            errorLabel.setText("Lỗi hệ thống: " + e.getMessage());
        }
    }
}