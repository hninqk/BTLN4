package com.auction.controller;

import com.auction.client.ApiClient;
import com.auction.util.NavigationManager;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
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

    @FXML
    public void initialize() {
        animateSquare(sq1, 0);
        animateSquare(sq2, 150);
        animateSquare(sq3, 300);

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
}
