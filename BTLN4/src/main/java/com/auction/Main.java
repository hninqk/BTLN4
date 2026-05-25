package com.auction;

import com.auction.util.NavigationManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.image.Image;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 * Main – Client entry point (JavaFX UI only).
 *
 * Connects automatically to the Render production server.
 * Friends just run this JAR – no configuration needed.
 *
 * The server is managed separately: see ServerMain.java / run_server.sh.
 */
public class Main extends Application {

    private TrayIcon trayIcon;

    @Override
    public void start(Stage stage) throws IOException {
        Platform.setImplicitExit(false);
        stage.initStyle(StageStyle.UNDECORATED);

        // Set application icon for title bar
        try {
            InputStream iconStream = getClass().getResourceAsStream("/com/auction/images/logo.png");
            if (iconStream != null) {
                Image icon = new Image(iconStream);
                stage.getIcons().add(icon);
            }
        } catch (Exception e) {
            System.err.println("Could not load application icon: " + e.getMessage());
        }

        createTrayIcon(stage);

        // Fetch server time offset asynchronously
        new Thread(() -> com.auction.util.TimeSyncManager.syncTimeWithServer(), "TimeSync-Thread").start();

        NavigationManager nav = NavigationManager.getInstance();
        nav.setPrimaryStage(stage);

        stage.setTitle("Hệ thống Đấu giá Trực tuyến");
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        nav.navigateTo(NavigationManager.SPLASH, "Đang tải...", null);

        stage.centerOnScreen();
        stage.show();
    }

    private void createTrayIcon(Stage stage) {
        if (SystemTray.isSupported()) {
            SystemTray tray = SystemTray.getSystemTray();

            // Load logo.png for system tray icon
            java.awt.Image image = null;
            try {
                InputStream iconStream = getClass().getResourceAsStream("/com/auction/images/logo.png");
                if (iconStream != null) {
                    image = ImageIO.read(iconStream);
                }
            } catch (Exception e) {
                System.err.println("Could not load tray icon: " + e.getMessage());
            }

            // Fallback to simple blue circle if logo fails to load
            if (image == null) {
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = img.createGraphics();
                g2d.setColor(Color.BLUE);
                g2d.fillOval(0, 0, 16, 16);
                g2d.dispose();
                image = img;
            }

            stage.setOnCloseRequest(event -> {
                if (trayIcon != null) {
                    stage.hide();
                    trayIcon.displayMessage("Auto-Bid Đang Chạy",
                        "Hệ thống đấu giá tự động vẫn đang hoạt động ngầm.",
                        TrayIcon.MessageType.INFO);
                }
            });

            PopupMenu popup = new PopupMenu();
            MenuItem showItem = new MenuItem("Mở Ứng Dụng");
            showItem.addActionListener(e -> Platform.runLater(() -> {
                stage.show();
                stage.toFront();
            }));

            MenuItem exitItem = new MenuItem("Thoát Hoàn Toàn");
            exitItem.addActionListener(e -> {
                Platform.exit();
                tray.remove(trayIcon);
                System.exit(0);
            });

            popup.add(showItem);
            popup.addSeparator();
            popup.add(exitItem);

            trayIcon = new TrayIcon(image, "Đấu Giá", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> Platform.runLater(() -> {
                stage.show();
                stage.toFront();
            }));

            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                System.err.println("TrayIcon could not be added.");
            }
        } else {
            // Fallback for systems without system tray support to cleanly terminate the process
            stage.setOnCloseRequest(event -> {
                Platform.exit();
                System.exit(0);
            });
        }
    }

    public static void main(String[] args) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        launch();
    }
}
