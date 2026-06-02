package com.auction.ui.controller;
import com.auction.ui.util.AnimationUtil;
import com.auction.ui.util.ImageLoaderUtil;

import com.auction.api.http.ApiClient;
import com.auction.ui.util.NavigationManager;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class SplashController extends BaseController {
    @FXML private StackPane splashRoot;

    @FXML private Label lblStatus;
    @FXML private Label animatedWordLabel;
    @FXML private Label logStreamLabel;
    @FXML private javafx.scene.image.ImageView logoImageView;

    /** Full auction list cached during boot — reused by LoginController to warm per-user caches. */
    public static volatile java.util.List<com.auction.core.model.Auction> cachedFullAuctions;

    private static final String[] WORDS = {"Đấu giá", "Chiến thắng", "Giao thương"};
    private int wordIndex = 0;
    private Timeline wordCycler;

    @FXML
    public void initialize() {
        startWordCycle();

        Platform.runLater(() -> {
            if (splashRoot != null) {
                com.auction.ui.util.AnimationUtil.createWaveBackground(splashRoot);
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
                entrance.play();
            }

        taskRunner.run("splash-boot", () -> {
            // Step 1: Connecting
            Platform.runLater(() -> {
                lblStatus.setText("Đang tải dữ liệu...");
                logStreamLabel.setText("[THÔNG BÁO] Đang kết nối API...");
            });

            boolean connected = false;
            while (!connected) {
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
            Platform.runLater(() -> logStreamLabel.setText("[THÔNG BÁO] Đã kết nối máy chủ..."));

            // Step 3: Fetch Configs
            Platform.runLater(() -> logStreamLabel.setText("[THÔNG BÁO] Đang tải cấu hình Auto-Bid..."));

            // Step 4: Cache Assets
            Platform.runLater(() -> logStreamLabel.setText("[THÔNG BÁO] Đang tải chi tiết đấu giá để lưu Cache..."));

            try {
                java.util.List<com.auction.core.model.Auction> allAuctions = app.getAllAuctions();

                java.util.List<java.util.concurrent.CompletableFuture<java.util.Optional<com.auction.core.model.Auction>>> futures = new java.util.ArrayList<>(
                        allAuctions.size());

                java.util.concurrent.ExecutorService fetchPool = java.util.concurrent.Executors.newFixedThreadPool(5);
                try {
                    for (com.auction.core.model.Auction a : allAuctions) {
                        final String id = a.getId();
                        futures.add(java.util.concurrent.CompletableFuture
                                .supplyAsync(() -> app.findAuctionById(id), fetchPool));
                    }
                    java.util.List<com.auction.core.model.Auction> fullAuctions = new java.util.ArrayList<>(
                            allAuctions.size());
                    for (var f : futures) {
                        f.join().ifPresent(fullAuctions::add);
                    }

                    cachedFullAuctions = fullAuctions;

                    // ── Parallel image preload (3 threads, 3 sizes each) ────────────
                    java.util.List<com.auction.core.model.Auction> toPreload = new java.util.ArrayList<>();
                    for (com.auction.core.model.Auction a : fullAuctions) {
                        String imgUrl = a.getItem() != null ? a.getItem().getImageUrl() : null;
                        if (imgUrl != null && !imgUrl.isEmpty()) {
                            toPreload.add(a);
                            if (toPreload.size() >= 20)
                                break;
                        }
                    }

                    int taskCount = toPreload.size() * 3; // 3 sizes per auction
                    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(taskCount);
                    java.util.concurrent.atomic.AtomicInteger preloadedImages = new java.util.concurrent.atomic.AtomicInteger(
                            0);
                    java.util.concurrent.ExecutorService imgPool = java.util.concurrent.Executors.newFixedThreadPool(3);

                    try {
                        int[][] SIZES = { { 200, 120 }, { 420, 250 }, { 120, 80 } };
                        for (com.auction.core.model.Auction a : toPreload) {
                            final String url = a.getItem().getImageUrl();
                            for (int[] sz : SIZES) {
                                final int w = sz[0], h = sz[1];
                                imgPool.submit(() -> {
                                    try {
                                        com.auction.ui.util.ImageLoaderUtil.loadItemImageSync(url, w, h);
                                    } catch (Exception ex) {
                                        System.err.printf("[Splash] Image preload failed (%dx%d) %s: %s%n",
                                                w, h, url, ex.getMessage());
                                    } finally {
                                        latch.countDown();
                                    }
                                });
                            }
                            preloadedImages.incrementAndGet();
                        }
                        latch.await(); // block until all 3×N tasks complete
                    } finally {
                        imgPool.shutdown();
                    }
                    System.out.printf("[Splash] Pre-cached images for %d auctions at 3 sizes (parallel).%n",
                            preloadedImages.get());

                } finally {
                    fetchPool.shutdown();
                }
            } catch (Exception e) {
                System.err.println("Cache preload error: " + e.getMessage());
            }

            // Step 5: Done
            Platform.runLater(() -> {
                lblStatus.setText("Sẵn sàng.");
                logStreamLabel.setText("[THÔNG BÁO] Quá trình khởi động hoàn tất.");
            });
            return null;
        }, result -> {
            if (wordCycler != null)
                wordCycler.stop();

            try {
                nav.navigateTo(NavigationManager.LOGIN, "Đăng nhập", null);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, null);
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
