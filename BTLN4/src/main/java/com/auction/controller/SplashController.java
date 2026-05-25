package com.auction.controller;

import com.auction.client.ApiClient;
import com.auction.util.NavigationManager;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class SplashController {
    @FXML private StackPane splashRoot;

    @FXML private Label lblStatus;
    @FXML private Label animatedWordLabel;
    @FXML private Label logStreamLabel;
    @FXML private javafx.scene.image.ImageView logoImageView;

    private ScaleTransition logoPulse;

    private static final String[] WORDS = {"Đấu giá", "Chiến thắng", "Giao thương"};
    private int wordIndex = 0;
    private Timeline wordCycler;

    @FXML
    public void initialize() {
        startWordCycle();

        Platform.runLater(() -> {
            if (splashRoot != null) {
                com.auction.util.AnimationUtil.createWaveBackground(splashRoot);
            }
        });

            if (logoImageView != null) {
                ScaleTransition scale = new ScaleTransition(Duration.millis(1200), logoImageView);
                scale.setFromX(0.0);
                scale.setFromY(0.0);
                scale.setToX(1.0);
                scale.setToY(1.0);
                scale.setInterpolator(Interpolator.EASE_OUT);

                FadeTransition fade = new FadeTransition(Duration.millis(1200), logoImageView);
                fade.setFromValue(0.0);
                fade.setToValue(1.0);

                ParallelTransition entrance = new ParallelTransition(scale, fade);
                entrance.setOnFinished(ev -> {
                    // start a smooth pulsing scale animation after entrance
                    logoPulse = new ScaleTransition(Duration.millis(1400), logoImageView);
                    logoPulse.setFromX(1.0);
                    logoPulse.setFromY(1.0);
                    logoPulse.setToX(1.06);
                    logoPulse.setToY(1.06);
                    logoPulse.setAutoReverse(true);
                    logoPulse.setCycleCount(Animation.INDEFINITE);
                    logoPulse.setInterpolator(Interpolator.EASE_BOTH);
                    logoPulse.play();
                });
                entrance.play();
            }

        Task<Void> bootTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Step 1: Connecting
                updateProgress(0.1, 1.0);
                updateMessage("Đang tải dữ liệu...");
                Platform.runLater(() -> logStreamLabel.setText("[THÔNG BÁO] Đang kết nối API..."));
                
                boolean connected = false;
                while (!connected && !isCancelled()) {
                    try {
                        String resp = ApiClient.getInstance().getSync("/api/auctions");
                        if (resp != null && (resp.contains("[") || resp.contains("{"))) {
                            connected = true;
                        } else {
                            Thread.sleep(1000);
                        }
                    } catch (Exception e) {
                        Platform.runLater(() -> logStreamLabel.setText("Đang chờ máy chủ..."));
                        Thread.sleep(1500);
                    }
                }

                // Step 2: Confirm remote API is reachable; clients never open DB connections.
                updateProgress(0.4, 1.0);
                Platform.runLater(() -> logStreamLabel.setText("[THÔNG BÁO] Đã kết nối máy chủ..."));
                Thread.sleep(400); // Simulate/Wait for pool init

                // Step 3: Fetch Configs
                updateProgress(0.7, 1.0);
                Platform.runLater(() -> logStreamLabel.setText("[THÔNG BÁO] Đang tải cấu hình Auto-Bid..."));
                Thread.sleep(300);

                // Step 4: Cache Assets
                updateProgress(0.9, 1.0);
                Platform.runLater(() -> logStreamLabel.setText("[THÔNG BÁO] Đang tải chi tiết đấu giá để lưu Cache..."));
                
                try {
                    com.auction.service.AppFacade facade = com.auction.service.AppFacade.getInstance();
                    java.util.List<com.auction.model.Auction> allAuctions = facade.getAllAuctions();
                    java.util.List<com.auction.model.Auction> fullAuctions = new java.util.ArrayList<>();
                    for (com.auction.model.Auction a : allAuctions) {
                        facade.findAuctionById(a.getId()).ifPresent(fullAuctions::add);
                    }
                    UserProfileController.preloadCache(fullAuctions);
                    BidHistoryController.preloadCache(fullAuctions);

                    // Preload images at every size actually used in the UI so that
                    // CacheManager hits occur on all screens (keys include dimensions).
                    // Dashboard card  → 200×120
                    // AuctionDetail   → 420×250
                    // AuctionList row → 120×80
                    int preloadedImages = 0;
                    for (com.auction.model.Auction a : fullAuctions) {
                        String imgUrl = a.getItem() != null ? a.getItem().getImageUrl() : null;
                        if (imgUrl != null && !imgUrl.isEmpty()) {
                            com.auction.util.ImageLoaderUtil.loadItemImageSync(imgUrl, 200, 120);
                            com.auction.util.ImageLoaderUtil.loadItemImageSync(imgUrl, 420, 250);
                            com.auction.util.ImageLoaderUtil.loadItemImageSync(imgUrl, 120, 80);
                            preloadedImages++;
                            if (preloadedImages >= 20) break;
                        }
                    }
                    System.out.printf("[Splash] Pre-cached images for %d auctions at 3 sizes.%n", preloadedImages);
                } catch (Exception e) {
                    System.err.println("Cache preload error: " + e.getMessage());
                }

                // Step 5: Done
                updateProgress(1.0, 1.0);
                updateMessage("Sẵn sàng.");
                Platform.runLater(() -> logStreamLabel.setText("[THÔNG BÁO] Quá trình khởi động hoàn tất trong " + String.format("%.2fs", 1.5) + "."));
                Thread.sleep(200);
                
                return null;
            }
        };

        lblStatus.textProperty().bind(bootTask.messageProperty());

        bootTask.setOnSucceeded(e -> {
            if (wordCycler != null) wordCycler.stop();

            Platform.runLater(() -> {
                try {
                    NavigationManager.getInstance().navigateTo(NavigationManager.LOGIN, "Đăng nhập", null);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        });

        Thread t = new Thread(bootTask);
        t.setDaemon(true);
        t.start();
    }

    private void startWordCycle() {
        playWordIn(WORDS[0], 0);

        wordCycler = new Timeline(new KeyFrame(Duration.seconds(2.4), e -> cycleWord()));
        wordCycler.setDelay(Duration.seconds(1.2));
        wordCycler.setCycleCount(Timeline.INDEFINITE);
        wordCycler.play();
    }

    private void cycleWord() {
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(320), animatedWordLabel);
        slideOut.setToX(-60);
        slideOut.setInterpolator(Interpolator.SPLINE(0.55, 0.0, 1.0, 0.45));
        FadeTransition fadeOut = new FadeTransition(Duration.millis(280), animatedWordLabel);
        fadeOut.setToValue(0);
        ParallelTransition exit = new ParallelTransition(slideOut, fadeOut);

        exit.setOnFinished(done -> {
            wordIndex = (wordIndex + 1) % WORDS.length;
            animatedWordLabel.setTranslateX(60);
            animatedWordLabel.setText(WORDS[wordIndex]);
            playWordIn(WORDS[wordIndex], 0);
        });
        exit.play();
    }

    private void playWordIn(String word, long delayMs) {
        animatedWordLabel.setText(word);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(520), animatedWordLabel);
        slideIn.setFromX(60);
        slideIn.setToX(0);
        slideIn.setInterpolator(Interpolator.SPLINE(0.1, 0.8, 0.15, 1.0));
        slideIn.setDelay(Duration.millis(delayMs));

        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), animatedWordLabel);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.setDelay(Duration.millis(delayMs));

        new ParallelTransition(slideIn, fadeIn).play();
    }
}
