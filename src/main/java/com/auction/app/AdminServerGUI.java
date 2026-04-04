package com.auction.app;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class AdminServerGUI extends Application {

    private HttpServer server;
    // Admin controlled setting (default 3 mins)
    private static int globalAuctionMinutes = 3;

    // UI Elements
    private ListView<String> activeItemsList;
    private ObservableList<String> itemsData;
    private Map<String, Integer> itemNameToIdMap = new HashMap<>(); // Helps us delete the right item
    private Timeline autoUpdater;

    @Override
    public void start(Stage primaryStage) {
        DatabaseManager.initializeDB();

        // 1. Build the UI
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #2e3440; -fx-padding: 20;");

        // Header
        VBox header = new VBox(10);
        header.setAlignment(Pos.CENTER);
        Label title = new Label("MutualArt Admin Control Panel");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold;");
        Label status = new Label("🟢 HTTP Server Running on Port 8080");
        status.setStyle("-fx-text-fill: #a3be8c; -fx-font-weight: bold;");
        header.getChildren().addAll(title, status);
        root.setTop(header);

        // Center Panel (Controls & List)
        VBox center = new VBox(20);
        center.setPadding(new Insets(20, 0, 0, 0));

        // Timer Controls
        HBox timerBox = new HBox(10);
        timerBox.setAlignment(Pos.CENTER_LEFT);
        Label timerLabel = new Label("Global Auction Duration (Minutes):");
        timerLabel.setStyle("-fx-text-fill: white;");
        TextField timerField = new TextField(String.valueOf(globalAuctionMinutes));
        timerField.setPrefWidth(50);
        Button updateTimerBtn = new Button("Apply Timer");
        Label timerStatus = new Label();
        timerStatus.setStyle("-fx-text-fill: #ebcb8b;");

        updateTimerBtn.setOnAction(e -> {
            try {
                globalAuctionMinutes = Integer.parseInt(timerField.getText());
                timerStatus.setText("Timer updated to " + globalAuctionMinutes + " mins (Applies to new listings)");
            } catch (NumberFormatException ex) {
                timerStatus.setText("Invalid number!");
            }
        });
        timerBox.getChildren().addAll(timerLabel, timerField, updateTimerBtn, timerStatus);

        // Live Items List
        Label listLabel = new Label("Live Auctions (Select to Delete):");
        listLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");

        itemsData = FXCollections.observableArrayList();
        activeItemsList = new ListView<>(itemsData);
        activeItemsList.setPrefHeight(300);

        Button deleteBtn = new Button("🗑️ Delete Selected Item");
        deleteBtn.setStyle("-fx-background-color: #bf616a; -fx-text-fill: white; -fx-font-weight: bold;");
        deleteBtn.setOnAction(e -> deleteSelectedItem());

        center.getChildren().addAll(timerBox, listLabel, activeItemsList, deleteBtn);
        root.setCenter(center);

        // 2. Start the Background Server
        startHttpServer();

        // 3. Start UI Auto-Updater (Queries DB directly since it IS the server)
        startAdminAutoUpdater();

        Scene scene = new Scene(root, 600, 500);
        primaryStage.setTitle("MutualArt Admin Server");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            if (server != null) server.stop(0);
            System.exit(0);
        });
        primaryStage.show();
    }

    private void startHttpServer() {
        // Run on a separate thread so it doesn't freeze the Admin UI
        new Thread(() -> {
            try {
                server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);
                server.createContext("/", new AuctionHandler());
                server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
                server.start();
                System.out.println("Background HTTP Server started.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startAdminAutoUpdater() {
        Runnable fetchLocalItems = () -> {

            // 1. Create temporary containers to hold data while DB is open
            java.util.List<String> newItemsList = new java.util.ArrayList<>();
            Map<String, Integer> newIdMap = new java.util.HashMap<>();

            try (Connection conn = DatabaseManager.getConnection();
                 Statement stmt = conn.createStatement()) {

                // Mark expired items as SOLD locally
                stmt.executeUpdate("UPDATE items SET status = 'SOLD' WHERE end_time <= datetime('now') AND status = 'ACTIVE'");

                ResultSet rs = stmt.executeQuery("SELECT id, name, current_bid, highest_bidder, CAST(strftime('%s', end_time) - strftime('%s', 'now') AS INTEGER) AS time_left FROM items WHERE status = 'ACTIVE'");

                // 2. Read all data immediately while the connection is still open!
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    double bid = rs.getDouble("current_bid");
                    String bidder = rs.getString("highest_bidder");
                    int timeLeft = rs.getInt("time_left");

                    String displayText = String.format("[ID: %d] %s | Bid: $%.2f (%s) | Time Left: %ds", id, name, bid, bidder, timeLeft);
                    newItemsList.add(displayText);
                    newIdMap.put(displayText, id);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            // 3. Now hand the safe, pre-loaded data over to the UI thread
            Platform.runLater(() -> {
                // Find out exactly which ID is currently selected before we wipe the list
                String selectedText = activeItemsList.getSelectionModel().getSelectedItem();
                Integer selectedId = null;
                if (selectedText != null) {
                    selectedId = itemNameToIdMap.get(selectedText);
                }

                // Wipe the old lists
                itemsData.clear();
                itemNameToIdMap.clear();

                // Add the loaded data to the live UI lists
                itemsData.addAll(newItemsList);
                itemNameToIdMap.putAll(newIdMap);

                // Re-select the item based on its ID, not its exact text!
                if (selectedId != null) {
                    for (Map.Entry<String, Integer> entry : itemNameToIdMap.entrySet()) {
                        if (entry.getValue().equals(selectedId)) {
                            activeItemsList.getSelectionModel().select(entry.getKey());
                            break;
                        }
                    }
                }
            });
        };

        autoUpdater = new Timeline(new KeyFrame(Duration.seconds(1), e -> fetchLocalItems.run()));
        autoUpdater.setCycleCount(Timeline.INDEFINITE);
        autoUpdater.play();
    }

    private void deleteSelectedItem() {
        String selected = activeItemsList.getSelectionModel().getSelectedItem();
        if (selected != null && itemNameToIdMap.containsKey(selected)) {
            int itemId = itemNameToIdMap.get(selected);
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM items WHERE id = ?")) {
                ps.setInt(1, itemId);
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    // --- INNER CLASS: The actual HTTP Request Handler ---
    static class AuctionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
                String action = request.get("action").getAsString();
                JsonObject response = new JsonObject();

                try (Connection conn = DatabaseManager.getConnection()) {
                    if (action.equals("LOGIN")) {
                        PreparedStatement ps = conn.prepareStatement("SELECT id, role FROM users WHERE username = ? AND password = ?");
                        ps.setString(1, request.get("username").getAsString());
                        ps.setString(2, request.get("password").getAsString());
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            PreparedStatement actPs = conn.prepareStatement("UPDATE users SET last_active = datetime('now') WHERE id = ?");
                            actPs.setInt(1, rs.getInt("id")); actPs.executeUpdate();

                            response.addProperty("status", "SUCCESS");
                            response.addProperty("role", rs.getString("role"));
                            response.addProperty("user_id", rs.getInt("id"));
                            response.addProperty("username", request.get("username").getAsString());
                        } else {
                            response.addProperty("status", "ERROR");
                            response.addProperty("message", "Invalid credentials.");
                        }
                    }
                    else if (action.equals("REGISTER")) {
                        try {
                            PreparedStatement ps = conn.prepareStatement("INSERT INTO users (username, password, role) VALUES (?, ?, 'User')");
                            ps.setString(1, request.get("username").getAsString());
                            ps.setString(2, request.get("password").getAsString());
                            ps.executeUpdate();
                            response.addProperty("status", "SUCCESS");
                        } catch (SQLException e) {
                            response.addProperty("status", "ERROR");
                        }
                    }
                    else if (action.equals("LIST_ITEM")) {
                        // CRITICAL: We now inject the dynamic globalAuctionMinutes set by the Admin GUI!
                        String query = "INSERT INTO items (name, description, current_bid, highest_bidder, seller_id, end_time, status) VALUES (?, ?, ?, 'None', ?, datetime('now', '+" + globalAuctionMinutes + " minutes'), 'ACTIVE')";
                        PreparedStatement ps = conn.prepareStatement(query);
                        ps.setString(1, request.get("name").getAsString());
                        ps.setString(2, request.has("description") ? request.get("description").getAsString() : "No description.");
                        ps.setDouble(3, request.get("price").getAsDouble());
                        ps.setInt(4, request.get("seller_id").getAsInt());
                        ps.executeUpdate();
                        response.addProperty("status", "SUCCESS");
                    }
                    else if (action.equals("GET_ITEMS")) {
                        if (request.has("user_id")) {
                            PreparedStatement actPs = conn.prepareStatement("UPDATE users SET last_active = datetime('now') WHERE id = ?");
                            actPs.setInt(1, request.get("user_id").getAsInt()); actPs.executeUpdate();
                        }

                        Statement countStmt = conn.createStatement();
                        ResultSet countRs = countStmt.executeQuery("SELECT COUNT(*) AS online_count FROM users WHERE last_active >= datetime('now', '-5 seconds')");
                        response.addProperty("online_users", countRs.next() ? countRs.getInt("online_count") : 1);

                        Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT *, CAST(strftime('%s', end_time) - strftime('%s', 'now') AS INTEGER) AS time_left FROM items WHERE status = 'ACTIVE'");
                        JsonArray items = new JsonArray();
                        while (rs.next()) {
                            JsonObject item = new JsonObject();
                            item.addProperty("id", rs.getInt("id"));
                            item.addProperty("name", rs.getString("name"));
                            item.addProperty("description", rs.getString("description"));
                            item.addProperty("current_bid", rs.getDouble("current_bid"));
                            item.addProperty("highest_bidder", rs.getString("highest_bidder"));
                            item.addProperty("time_left", rs.getInt("time_left"));
                            items.add(item);
                        }
                        response.addProperty("status", "SUCCESS");
                        response.add("items", items);
                    }
                    else if (action.equals("BID")) {
                        int itemId = request.get("item_id").getAsInt();
                        double bidAmount = request.get("bid_amount").getAsDouble();
                        String bidderName = request.get("bidder_name").getAsString();

                        PreparedStatement checkPs = conn.prepareStatement("SELECT i.current_bid, u.username AS seller_name FROM items i JOIN users u ON i.seller_id = u.id WHERE i.id = ? AND i.status = 'ACTIVE'");
                        checkPs.setInt(1, itemId);
                        ResultSet rs = checkPs.executeQuery();

                        if (rs.next()) {
                            if (bidderName.equals(rs.getString("seller_name"))) {
                                response.addProperty("status", "ERROR");
                                response.addProperty("message", "You cannot bid on your own item!");
                            } else if (bidAmount > rs.getDouble("current_bid")) {
                                PreparedStatement updatePs = conn.prepareStatement("UPDATE items SET current_bid = ?, highest_bidder = ? WHERE id = ?");
                                updatePs.setDouble(1, bidAmount);
                                updatePs.setString(2, bidderName);
                                updatePs.setInt(3, itemId);
                                updatePs.executeUpdate();
                                response.addProperty("status", "SUCCESS");
                            } else {
                                response.addProperty("status", "ERROR");
                                response.addProperty("message", "Bid too low!");
                            }
                        }
                    }
                    else if (action.equals("GET_HISTORY")) {
                        String username = request.get("username").getAsString();
                        Statement pubStmt = conn.createStatement();
                        ResultSet pubRs = pubStmt.executeQuery("SELECT name, current_bid, highest_bidder FROM items WHERE status = 'SOLD' ORDER BY id DESC LIMIT 20");
                        JsonArray publicHistory = new JsonArray();
                        while (pubRs.next()) {
                            JsonObject item = new JsonObject();
                            item.addProperty("name", pubRs.getString("name"));
                            item.addProperty("winning_bid", pubRs.getDouble("current_bid"));
                            item.addProperty("winner", pubRs.getString("highest_bidder"));
                            publicHistory.add(item);
                        }

                        PreparedStatement privStmt = conn.prepareStatement("SELECT name, current_bid FROM items WHERE status = 'SOLD' AND highest_bidder = ? ORDER BY id DESC");
                        privStmt.setString(1, username);
                        ResultSet privRs = privStmt.executeQuery();
                        JsonArray privateHistory = new JsonArray();
                        while (privRs.next()) {
                            JsonObject item = new JsonObject();
                            item.addProperty("name", privRs.getString("name"));
                            item.addProperty("winning_bid", privRs.getDouble("current_bid"));
                            privateHistory.add(item);
                        }

                        response.addProperty("status", "SUCCESS");
                        response.add("public_history", publicHistory);
                        response.add("private_history", privateHistory);
                    }
                } catch (SQLException e) {
                    response.addProperty("status", "ERROR");
                    response.addProperty("message", "Database error: " + e.getMessage());
                }

                String responseBody = response.toString();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                os.close();
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}