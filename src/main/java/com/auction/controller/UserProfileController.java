package com.auction.controller;

import com.auction.model.*;
import com.auction.service.AuctionService;
import com.auction.service.UserService;
import com.auction.util.SessionManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/**
 * UserProfileController – view and edit personal profile.
 * Dynamically shows Bidder-specific or Seller-specific sections.
 */
public class UserProfileController {

    @FXML
    private Label avatarLabel;
    @FXML
    private Label displayNameLabel;
    @FXML
    private Label roleLabel;
    @FXML
    private Label memberSinceLabel;

    // Bidder stats
    @FXML
    private VBox bidderStatsBox;
    @FXML
    private Label balanceLabel;
    @FXML
    private Label totalBidsLabel;

    // Seller stats
    @FXML
    private VBox sellerStatsBox;
    @FXML
    private Label shopNameLabel;
    @FXML
    private Label ratingLabel;
    @FXML
    private Label auctionCountLabel;

    // Edit form
    @FXML
    private TextField usernameField;
    @FXML
    private VBox shopNameBox;
    @FXML
    private TextField shopNameField;
    @FXML
    private VBox balanceBox;
    @FXML
    private TextField depositField;
    @FXML
    private Label profileErrorLabel;
    @FXML
    private Label profileSuccessLabel;

    // Password fields
    @FXML
    private PasswordField currentPasswordField;
    @FXML
    private PasswordField newPasswordField;
    @FXML
    private PasswordField confirmNewPasswordField;
    @FXML
    private Label pwErrorLabel;
    @FXML
    private Label pwSuccessLabel;

    private User currentUser;

    @FXML
    public void initialize() {
        currentUser = SessionManager.getInstance().getCurrentUser();
        profileErrorLabel.setText("");
        profileSuccessLabel.setText("");
        pwErrorLabel.setText("");
        pwSuccessLabel.setText("");

        if (currentUser != null) {
            populateProfile();
        }
    }

    private void populateProfile() {
        displayNameLabel.setText(currentUser.getUsername());
        roleLabel.setText(currentUser.getRole());
        memberSinceLabel.setText("Thành viên từ: " +
                currentUser.getCreatedAt().toLocalDate().toString());
        usernameField.setText(currentUser.getUsername());

        if (currentUser instanceof Bidder bidder) {
            bidderStatsBox.setVisible(true);
            bidderStatsBox.setManaged(true);
            balanceBox.setVisible(true);
            balanceBox.setManaged(true);

            balanceLabel.setText(String.format("%,.0f ₫", bidder.getAccountBalance()));
            long bidCount = AuctionService.getInstance().getAllAuctions().stream()
                    .flatMap(a -> a.getBidHistory().stream())
                    .filter(b -> b.getBidder().getId().equals(bidder.getId()))
                    .count();
            totalBidsLabel.setText(String.valueOf(bidCount));

        } else if (currentUser instanceof Seller seller) {
            sellerStatsBox.setVisible(true);
            sellerStatsBox.setManaged(true);
            shopNameBox.setVisible(true);
            shopNameBox.setManaged(true);

            shopNameLabel.setText(seller.getShopName());
            ratingLabel.setText(seller.getCntvoted() == 0 ? "Chưa có đánh giá"
                    : String.format("%.1f / 5.0 (%d đánh giá)", seller.getRating(), seller.getCntvoted()));
            long myAuctions = AuctionService.getInstance().getAuctionsBySeller(seller).size();
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
        }

        profileSuccessLabel.setText("Thông tin đã được cập nhật.");
        populateProfile();
    }

    @FXML
    private void handleDeposit(ActionEvent event) {
        profileErrorLabel.setText("");
        profileSuccessLabel.setText("");

        if (!(currentUser instanceof Bidder bidder))
            return;
        String input = depositField.getText().trim();
        if (input.isEmpty())
            return;
        try {
            double amount = Double.parseDouble(input.replace(",", ""));
            if (amount <= 0)
                throw new NumberFormatException();
            bidder.AddBalance(amount);
            depositField.clear();
            balanceLabel.setText(String.format("%,.0f ₫", bidder.getAccountBalance()));
            profileSuccessLabel.setText(String.format("Đã nạp %,.0f ₫ vào tài khoản.", amount));
        } catch (NumberFormatException e) {
            profileErrorLabel.setText("Số tiền không hợp lệ.");
        }
    }

    @FXML
    private void handleChangePassword(ActionEvent event) {
        pwErrorLabel.setText("");
        pwSuccessLabel.setText("");

        String current = currentPasswordField.getText();
        String newPw = newPasswordField.getText();
        String confirm = confirmNewPasswordField.getText();

        if (!current.equals(currentUser.getPassword())) {
            pwErrorLabel.setText("Mật khẩu hiện tại không chính xác.");
            return;
        }
        if (newPw.length() < 6) {
            pwErrorLabel.setText("Mật khẩu mới phải có ít nhất 6 ký tự.");
            return;
        }
        if (!newPw.equals(confirm)) {
            pwErrorLabel.setText("Mật khẩu xác nhận không khớp.");
            return;
        }
        currentUser.setPassword(newPw);
        currentPasswordField.clear();
        newPasswordField.clear();
        confirmNewPasswordField.clear();
        pwSuccessLabel.setText("Mật khẩu đã được thay đổi thành công.");
    }
}
