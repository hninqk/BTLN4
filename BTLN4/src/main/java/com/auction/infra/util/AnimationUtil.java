package com.auction.infra.util;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.ParallelTransition;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

public final class AnimationUtil {

    private AnimationUtil() {}

    /**
     * Creates a fluid ripple effect on a given Node (usually a Button or Pane) when clicked.
     * The node should be inside a layout that supports clipping (or we just let the circle overlay).
     */
    public static void addRippleEffect(Region region) {
        // We use mouse pressed to trigger the ripple immediately
        region.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            Circle ripple = new Circle(event.getX(), event.getY(), 5, Color.rgb(255, 255, 255, 0.4));
            
            // To ensure the ripple stays inside the region, we could apply a clip, but a simple overlay works too.
            if (region.getParent() instanceof javafx.scene.layout.Pane parentPane) {
                // Adjust coordinates relative to parent
                ripple.setCenterX(region.getLayoutX() + event.getX());
                ripple.setCenterY(region.getLayoutY() + event.getY());
                
                // Add ripple to parent temporarily
                parentPane.getChildren().add(ripple);

                // Target radius based on region size
                double maxDim = Math.max(region.getWidth(), region.getHeight());
                double targetRadius = maxDim * 1.5;

                ScaleTransition scale = new ScaleTransition(Duration.millis(400), ripple);
                scale.setToX(targetRadius / 5);
                scale.setToY(targetRadius / 5);

                FadeTransition fade = new FadeTransition(Duration.millis(400), ripple);
                fade.setFromValue(0.4);
                fade.setToValue(0.0);

                ParallelTransition pt = new ParallelTransition(scale, fade);
                pt.setOnFinished(e -> parentPane.getChildren().remove(ripple));
                pt.play();
            }
        });
    }

    /**
     * Injects an animated waveline background into a Pane.
     * Uses JavaFX Canvas and AnimationTimer for optical illusion effects.
     */
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
        canvas.setManaged(false); // Do not interfere with parent layout (e.g. BorderPane, VBox)
        
        // Put the canvas at the very bottom
        parent.getChildren().add(0, canvas);
        
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
        
        javafx.animation.AnimationTimer timer = new javafx.animation.AnimationTimer() {
            private long startTime = -1;
            private long lastUpdate = 0;
            private static final long FRAME_INTERVAL = 33_000_000; // ~30 FPS (33ms between frames)

            @Override
            public void handle(long now) {
                // Throttle to ~30 FPS instead of 60 FPS
                if (now - lastUpdate < FRAME_INTERVAL) {
                    return;
                }
                lastUpdate = now;

                if (startTime < 0) startTime = now;
                double t = (now - startTime) / 1_000_000_000.0;

                double w = canvas.getWidth();
                double h = canvas.getHeight();

                // Skip rendering if canvas is too small or not visible
                if (w < 10 || h < 10 || !canvas.isVisible()) {
                    return;
                }

                // Set background and wave colors (dark theme)
                gc.setFill(javafx.scene.paint.Color.web("#0F172A"));
                gc.fillRect(0, 0, w, h);
                drawWave(gc, w, h, t, 0.5, 0.005, h * 0.1, javafx.scene.paint.Color.web("#3B82F6", 0.2));
                drawWave(gc, w, h, t, 0.8, 0.004, h * 0.15, javafx.scene.paint.Color.web("#00f2fe", 0.15));
                drawWave(gc, w, h, t, 1.2, 0.006, h * 0.05, javafx.scene.paint.Color.web("#ffffff", 0.05));
            }
        };
        timer.start();
        
        // Stop timer when canvas is removed from scene
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
        for (int x = 0; x <= w; x += 20) { // step by 20 pixels for better performance
            double y = h / 2.0 + Math.sin(x * frequency + time * speed) * amplitude;
            if (x == 0) gc.moveTo(x, y);
            else gc.lineTo(x, y);
        }
        gc.stroke();
    }
}
