package com.auction.ui.util;

import javafx.scene.paint.Color;

public final class AnimationUtil {

    private AnimationUtil() {}

    public static void createWaveBackground(javafx.scene.layout.Pane parent) {
        boolean alreadyInstalled = parent.getChildren().stream()
                .anyMatch(node -> node.getStyleClass().contains("wave-background-canvas"));
        if (alreadyInstalled) {
            return;
        }

        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas();
        canvas.getStyleClass().add("wave-background-canvas");
        canvas.widthProperty().bind(parent.widthProperty());
        canvas.heightProperty().bind(parent.heightProperty());
        canvas.setManaged(false);

        parent.getChildren().add(0, canvas);

        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();

        javafx.animation.AnimationTimer timer = new javafx.animation.AnimationTimer() {
            private long startTime = -1;
            private long lastUpdate = 0;
            private static final long FRAME_INTERVAL = 33_000_000;

            @Override
            public void handle(long now) {

                if (now - lastUpdate < FRAME_INTERVAL) {
                    return;
                }
                lastUpdate = now;

                if (startTime < 0) startTime = now;
                double t = (now - startTime) / 1_000_000_000.0;

                double w = canvas.getWidth();
                double h = canvas.getHeight();

                if (w < 10 || h < 10 || !canvas.isVisible()) {
                    return;
                }

                gc.setFill(javafx.scene.paint.Color.web("#0F172A"));
                gc.fillRect(0, 0, w, h);
                drawWave(gc, w, h, t, 0.5, 0.005, h * 0.1, javafx.scene.paint.Color.web("#3B82F6", 0.2));
                drawWave(gc, w, h, t, 0.8, 0.004, h * 0.15, javafx.scene.paint.Color.web("#00f2fe", 0.15));
                drawWave(gc, w, h, t, 1.2, 0.006, h * 0.05, javafx.scene.paint.Color.web("#ffffff", 0.05));
            }
        };
        timer.start();

        canvas.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                timer.stop();
            }
        });
    }

    private static void drawWave(javafx.scene.canvas.GraphicsContext gc, double w, double h, double time,
                                 double speed, double frequency, double amplitude, javafx.scene.paint.Color color) {
        gc.setStroke(color);
        gc.setLineWidth(2.0);
        gc.beginPath();
        for (int x = 0; x <= w; x += 20) {
            double y = h / 2.0 + Math.sin(x * frequency + time * speed) * amplitude;
            if (x == 0) gc.moveTo(x, y);
            else gc.lineTo(x, y);
        }
        gc.stroke();
    }
}
