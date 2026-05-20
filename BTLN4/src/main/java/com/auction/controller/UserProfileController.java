package com.auction.controller;

import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.service.AppFacade;
import com.auction.util.SessionManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.function.UnaryOperator;

public class UserProfileController {

    @FXML
    private Label avatarLabel;
    @FXML
    private Label displayNameLabel;
    @FXML
    private Label roleLabel;
    @FXML
    private Label memberSinceLabel;

    @FXML
    private VBox bidderStatsBox;
    @FXML
    private Label balanceLabel;
    @FXML
    private Label totalBidsLabel;

    @FXML
    private VBox sellerStatsBox;
    @FXML
    private Label shopNameLabel;
    @FXML
    private Label auctionCountLabel;

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

    private User currentUser;
    private final AppFacade app = AppFacade.getInstance();

    @FXML
    public void initialize() {
        currentUser = SessionManager.getInstance().getCurrentUser();
        profileErrorLabel.setText("");
        profileSuccessLabel.setText("");

        UnaryOperator<javafx.scene.control.TextFormatter.Change> numericFilter = change -> {
            String newText = change.getControlNewText();
            return newText.matches("[0-9,.]*") ? change : null;
        };
        depositField.setTextFormatter(new javafx.scene.control.TextFormatter<>(numericFilter));

        if (currentUser != null) {
            populateProfile();
            // Fetch fresh data from server to ensure balance is up-to-date
            Task<java.util.Optional<User>> task = new Task<>() {
                @Override
                protected java.util.Optional<User> call() {
                    return app.findUserById(currentUser.getId());
                }
            };
            task.setOnSucceeded(e -> {
                task.getValue().ifPresent(u -> {
                    SessionManager.getInstance().setCurrentUser(u);
                    currentUser = u;
                    populateProfile();
                });
            });
            Thread t = new Thread(task, "profile-refresh");
            t.setDaemon(true);
            t.start();
        }
    }

    private void populateProfile() {
        displayNameLabel.setText(currentUser.getUsername());
        roleLabel.setText(currentUser.getRole());
        memberSinceLabel.setText("Thành viên từ: " + currentUser.getCreatedAt().toLocalDate());

        if (bidderStatsBox != null) {
            bidderStatsBox.setVisible(false);
            bidderStatsBox.setManaged(false);
        }
        if (sellerStatsBox != null) {
            sellerStatsBox.setVisible(false);
            sellerStatsBox.setManaged(false);
        }
        if (balanceBox != null) {
            balanceBox.setVisible(false);
            balanceBox.setManaged(false);
        }
        if (shopNameBox != null) {
            shopNameBox.setVisible(false);
            shopNameBox.setManaged(false);
        }

        if (currentUser instanceof Bidder bidder) {
            if (bidderStatsBox != null) {
                bidderStatsBox.setVisible(true);
                bidderStatsBox.setManaged(true);
            }
            if (balanceBox != null) {
                balanceBox.setVisible(true);
                balanceBox.setManaged(true);
            }
            balanceLabel.setText(String.format("%,.0f ₫", bidder.getAccountBalance()));
            loadBidCount(bidder);

        } else if (currentUser instanceof Seller seller) {
            if (sellerStatsBox != null) {
                sellerStatsBox.setVisible(true);
                sellerStatsBox.setManaged(true);
            }
            if (shopNameBox != null) {
                shopNameBox.setVisible(true);
                shopNameBox.setManaged(true);
            }
            shopNameLabel.setText(seller.getShopName());
            shopNameField.setText(seller.getShopName());
            loadSellerAuctionCount(seller);
        }
    }

    private static final java.util.Map<String, Long> bidCountCache = new java.util.concurrent.ConcurrentHashMap<>();

    public static void preloadCache(java.util.List<Auction> fullAuctions) {
        bidCountCache.clear();
        for (Auction full : fullAuctions) {
            for (com.auction.model.BidTransaction b : full.getBidHistory()) {
                bidCountCache.merge(b.getBidder().getId(), 1L, Long::sum);
            }
        }
    }

    private void loadBidCount(Bidder bidder) {
        if (totalBidsLabel == null)
            return;
            
        Long cached = bidCountCache.get(bidder.getId());
        if (cached != null) {
            totalBidsLabel.setText(String.valueOf(cached));
        } else {
            totalBidsLabel.setText("...");
        }
        
        Task<Long> task = new Task<>() {
            @Override
            protected Long call() {
                long count = 0;
                for (Auction shallow : app.getAllAuctions()) {
                    Auction full = app.findAuctionById(shallow.getId()).orElse(null);
                    if (full != null) {
                        count += full.getBidHistory().stream()
                                .filter(b -> b.getBidder().getId().equals(bidder.getId()))
                                .count();
                    }
                }
                return count;
            }
        };
        task.setOnSucceeded(e -> {
            bidCountCache.put(bidder.getId(), task.getValue());
            totalBidsLabel.setText(String.valueOf(task.getValue()));
        });
        task.setOnFailed(e -> {
            if (bidCountCache.get(bidder.getId()) == null) totalBidsLabel.setText("?");
        });
        new Thread(task, "profile-bid-count").start();
    }

    private void loadSellerAuctionCount(Seller seller) {
        if (auctionCountLabel == null)
            return;
        auctionCountLabel.setText("...");
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                return app.getAuctionsBySeller(seller).size();
            }
        };
        task.setOnSucceeded(e -> auctionCountLabel.setText(String.valueOf(task.getValue())));
        task.setOnFailed(e -> auctionCountLabel.setText("?"));
        new Thread(task, "profile-auction-count").start();
    }

    @FXML
    private void handleSaveProfile(ActionEvent event) {
        profileErrorLabel.setText("");
        profileSuccessLabel.setText("Đang lưu...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                if (currentUser instanceof Seller seller
                        && !shopNameField.getText().trim().isEmpty()) {
                    seller.setShopName(shopNameField.getText().trim());
                }
                app.saveUser(currentUser);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            profileSuccessLabel.setText("Thông tin đã được cập nhật.");
            populateProfile();
        });
        task.setOnFailed(e -> Platform.runLater(() -> {
            profileErrorLabel.setText("Lỗi lưu dữ liệu: " + task.getException().getMessage());
            profileSuccessLabel.setText("");
        }));
        new Thread(task, "save-profile").start();
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

            profileSuccessLabel.setText("Đang nạp tiền...");
            Task<Bidder> task = new Task<>() {
                @Override
                protected Bidder call() {
                    return app.topupBalance(bidder, amount);
                }
            };
            task.setOnSucceeded(e -> {
                Bidder updated = task.getValue();
                // Update session with refreshed balance from server
                SessionManager.getInstance().setCurrentUser(updated);
                currentUser = updated;
                depositField.clear();
                balanceLabel.setText(String.format("%,.0f ₫", updated.getAccountBalance()));
                profileSuccessLabel.setText(String.format("Đã nạp %,.0f ₫ vào tài khoản.", amount));
            });
            task.setOnFailed(e -> Platform.runLater(() -> {
                profileErrorLabel.setText("Lỗi nạp tiền: " + task.getException().getMessage());
                profileSuccessLabel.setText("");
            }));
            new Thread(task, "topup").start();

        } catch (NumberFormatException e) {
            profileErrorLabel.setText("Số tiền không hợp lệ.");
        }
    }
}
