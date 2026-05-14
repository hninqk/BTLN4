package com.auction.controller;

import com.auction.model.*;
import com.auction.service.AppFacade;
import com.auction.util.SessionManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.function.UnaryOperator;

/**
 * UserProfileController – view and edit personal profile.
 * Uses AppFacade — no direct service imports.
 */
public class UserProfileController {

    @FXML private Label avatarLabel;
    @FXML private Label displayNameLabel;
    @FXML private Label roleLabel;
    @FXML private Label memberSinceLabel;

    @FXML private VBox  bidderStatsBox;
    @FXML private Label balanceLabel;
    @FXML private Label totalBidsLabel;

    @FXML private VBox  sellerStatsBox;
    @FXML private Label shopNameLabel;
    @FXML private Label ratingLabel;
    @FXML private Label auctionCountLabel;

    @FXML private TextField  usernameField;
    @FXML private VBox       shopNameBox;
    @FXML private TextField  shopNameField;
    @FXML private VBox       balanceBox;
    @FXML private TextField  depositField;
    @FXML private Label      profileErrorLabel;
    @FXML private Label      profileSuccessLabel;

    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmNewPasswordField;
    @FXML private Label         pwErrorLabel;
    @FXML private Label         pwSuccessLabel;

    private User currentUser;
    private final AppFacade app = AppFacade.getInstance();

    @FXML
    public void initialize() {
        currentUser = SessionManager.getInstance().getCurrentUser();
        profileErrorLabel.setText("");
        profileSuccessLabel.setText("");
        pwErrorLabel.setText("");
        pwSuccessLabel.setText("");

        if (currentUser != null) populateProfile();

        UnaryOperator<TextFormatter.Change> numericFilter = change -> {
            String newText = change.getControlNewText();
            return newText.matches("[0-9,.]*") ? change : null;
        };
        depositField.setTextFormatter(new TextFormatter<>(numericFilter));
    }

    private void populateProfile() {
        displayNameLabel.setText(currentUser.getUsername());
        roleLabel.setText(currentUser.getRole());
        memberSinceLabel.setText("Thành viên từ: " + currentUser.getCreatedAt().toLocalDate());
        usernameField.setText(currentUser.getUsername());

        // Hide role-specific boxes by default
        if (bidderStatsBox != null) { bidderStatsBox.setVisible(false); bidderStatsBox.setManaged(false); }
        if (sellerStatsBox != null) { sellerStatsBox.setVisible(false); sellerStatsBox.setManaged(false); }
        if (balanceBox != null)     { balanceBox.setVisible(false);     balanceBox.setManaged(false); }
        if (shopNameBox != null)    { shopNameBox.setVisible(false);    shopNameBox.setManaged(false); }

        if (currentUser instanceof Bidder bidder) {
            if (bidderStatsBox != null) { bidderStatsBox.setVisible(true); bidderStatsBox.setManaged(true); }
            if (balanceBox != null)     { balanceBox.setVisible(true);     balanceBox.setManaged(true); }
            balanceLabel.setText(String.format("%,.0f ₫", bidder.getAccountBalance()));
            long bidCount = app.getAllAuctions().stream()
                    .flatMap(a -> a.getBidHistory().stream())
                    .filter(b -> b.getBidder().getId().equals(bidder.getId()))
                    .count();
            totalBidsLabel.setText(String.valueOf(bidCount));

        } else if (currentUser instanceof Seller seller) {
            if (sellerStatsBox != null) { sellerStatsBox.setVisible(true); sellerStatsBox.setManaged(true); }
            if (shopNameBox != null)    { shopNameBox.setVisible(true);    shopNameBox.setManaged(true); }
            shopNameLabel.setText(seller.getShopName());
            ratingLabel.setText(seller.getCntvoted() == 0 ? "Chưa có đánh giá"
                    : String.format("%.1f / 5.0 (%d đánh giá)", seller.getRating(), seller.getCntvoted()));
            long myAuctions = app.getAuctionsBySeller(seller).size();
            auctionCountLabel.setText(String.valueOf(myAuctions));
            shopNameField.setText(seller.getShopName());
        }
    }

    @FXML
    private void handleSaveProfile(ActionEvent event) {
        profileErrorLabel.setText("");
        profileSuccessLabel.setText("");
        if (currentUser instanceof Seller seller && !shopNameField.getText().trim().isEmpty()) {
            seller.setShopName(shopNameField.getText().trim());
            app.saveUser(currentUser);
        }
        profileSuccessLabel.setText("Thông tin đã được cập nhật.");
        populateProfile();
    }

    @FXML
    private void handleDeposit(ActionEvent event) {
        profileErrorLabel.setText(""); profileSuccessLabel.setText("");
        if (!(currentUser instanceof Bidder bidder)) return;
        String input = depositField.getText().trim();
        if (input.isEmpty()) return;
        try {
            double amount = Double.parseDouble(input.replace(",", ""));
            if (amount <= 0) throw new NumberFormatException();
            bidder.AddBalance(amount);
            app.saveUser(currentUser);
            depositField.clear();
            balanceLabel.setText(String.format("%,.0f ₫", bidder.getAccountBalance()));
            profileSuccessLabel.setText(String.format("Đã nạp %,.0f ₫ vào tài khoản.", amount));
        } catch (NumberFormatException e) {
            profileErrorLabel.setText("Số tiền không hợp lệ.");
        }
    }

    @FXML
    private void handleChangePassword(ActionEvent event) {
        pwErrorLabel.setText(""); pwSuccessLabel.setText("");
        String current = currentPasswordField.getText();
        String newPw   = newPasswordField.getText();
        String confirm = confirmNewPasswordField.getText();
        if (!current.equals(currentUser.getPassword())) { pwErrorLabel.setText("Mật khẩu hiện tại không chính xác."); return; }
        if (newPw.length() < 6) { pwErrorLabel.setText("Mật khẩu mới phải có ít nhất 6 ký tự."); return; }
        if (!newPw.equals(confirm)) { pwErrorLabel.setText("Mật khẩu xác nhận không khớp."); return; }
        currentUser.setPassword(newPw);
        app.saveUser(currentUser);
        currentPasswordField.clear(); newPasswordField.clear(); confirmNewPasswordField.clear();
        pwSuccessLabel.setText("Mật khẩu đã được thay đổi thành công.");
    }
}
