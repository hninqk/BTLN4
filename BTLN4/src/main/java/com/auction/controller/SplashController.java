package com.auction.controller;

import com.auction.client.ApiClient;
import com.auction.util.NavigationManager;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class SplashController {
    @FXML private Rectangle sq1;
    @FXML private Rectangle sq2;
    @FXML private Rectangle sq3;

    @FXML private Label lblStatus;
    @FXML private Label animatedWordLabel;
    
    private static final String[] WORDS = {"Đấu giá", "Chiến thắng", "Giao thương"};
    private int wordIndex = 0;
    private Timeline wordCycler;

    @FXML
    public void initialize() {
        animateSquare(sq1, 0);
        animateSquare(sq2, 150);
        animateSquare(sq3, 300);

        startWordCycle();

        Task<Void> pingTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                boolean connected = false;
                while (!connected && !isCancelled()) {
                    try {
                        String resp = ApiClient.getInstance().getSync("/api/auctions");
                        // If it responds with JSON (or array brackets), it's awake!
                        if (resp != null && (resp.contains("[") || resp.contains("{"))) {
                            connected = true;
                        } else {
                            Thread.sleep(2000);
                        }
                    } catch (Exception e) {
                        Platform.runLater(() -> lblStatus.setText("Đang chờ máy chủ (khởi động lạnh mất tới 15s)..."));
                        Thread.sleep(2500);
                    }
                }
                return null;
            }
        };

        pingTask.setOnSucceeded(e -> {
            if (wordCycler != null) wordCycler.stop();
            Platform.runLater(() -> {
                try {
                    NavigationManager.getInstance().navigateTo(NavigationManager.LOGIN, "Đăng nhập", null);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        });

        Thread t = new Thread(pingTask);
        t.setDaemon(true);
        t.start();
    }

    private void animateSquare(Rectangle sq, int delay) {
        ScaleTransition stUp = new ScaleTransition(Duration.millis(300), sq);
        stUp.setToY(1.5);
        stUp.setToX(1.5);
        
        ScaleTransition stDown = new ScaleTransition(Duration.millis(300), sq);
        stDown.setToY(1.0);
        stDown.setToX(1.0);

        SequentialTransition seq = new SequentialTransition(stUp, stDown);
        seq.setDelay(Duration.millis(delay));
        seq.setCycleCount(SequentialTransition.INDEFINITE);
        seq.play();
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
