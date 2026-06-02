package com.auction.ui.controller;

import com.auction.ui.support.ui.BackgroundTaskRunner;
import com.auction.ui.support.ui.FxBackgroundTaskRunner;
import com.auction.core.model.User;
import com.auction.service.AppFacade;
import com.auction.ui.util.NavigationManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.Optional;

/**
 * RegisterController – handles new user registration via REST API.
 *
 * The register call runs on a background thread (Task) to keep the UI responsive.
 */
public class RegisterController extends BaseController {

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ComboBox<String> roleCombo;
    @FXML private Label         errorLabel;
    @FXML private Label         successLabel;
    @FXML private Button        registerButton;
    @FXML private StackPane     rootPane;

    @FXML
    public void initialize() {
        roleCombo.setItems(FXCollections.observableArrayList("Bidder", "Seller"));
        roleCombo.getSelectionModel().selectFirst();
        errorLabel.setText("");
        successLabel.setText("");
        
        Platform.runLater(() -> {
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(100));
            pause.setOnFinished(e -> {
                if (rootPane != null) {
                    com.auction.ui.util.AnimationUtil.createWaveBackground(rootPane);
                }
            });
            pause.play();
        });
    }

    @FXML
    private void handleRegister(ActionEvent event) {
        errorLabel.setText("");
        successLabel.setText("");

        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirm  = confirmPasswordField.getText();
        String role     = roleCombo.getValue();

        // Client-side validation (no server round-trip needed for these)
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

        registerButton.setDisable(true);
        successLabel.setText("Đang đăng ký...");

        taskRunner.run("register-task", () -> app.register(username, password, role), result -> {
            registerButton.setDisable(false);
            if (result.isPresent()) {
                successLabel.setText("Đăng ký thành công! Bạn có thể đăng nhập ngay.");
                clearForm();
            } else {
                errorLabel.setText("Tên đăng nhập đã tồn tại. Vui lòng chọn tên khác.");
                successLabel.setText("");
            }
        }, error -> {
            registerButton.setDisable(false);
            errorLabel.setText(error.getMessage());
            successLabel.setText("");
        });
    }

    @FXML
    private void handleGoToLogin(ActionEvent event) {
        try {
            nav.navigateTo(NavigationManager.LOGIN, "Đăng nhập", null);
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
