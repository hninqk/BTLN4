package com.auction.app;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;

public class AuctionServer {
    // Port 8080 is the standard port for local web development
    private static final int PORT = 8080;

    // --- 1. SERVER STARTUP ---
    public static void main(String[] args) {
        // Step 1: Make sure our SQLite database files and tables actually exist
        DatabaseManager.initializeDB();

        try {
            // Step 2: Create the server listener on port 8080 (0.0.0.0 means "listen to all incoming networks")
            HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);

            // Step 3: Tell the server who handles the actual traffic.
            // "/" means EVERY request to this server goes to our AuctionHandler class.
            server.createContext("/", new AuctionHandler());

            // Step 4: Use a Thread Pool. This allows the server to handle multiple users clicking things at the exact same time!
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();

            System.out.println("HTTP Web Server started on port " + PORT + ". Ready for ngrok traffic!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- 2. THE TRAFFIC COP (HTTP HANDLER) ---
    // Every single time the Client app sends a request, this method runs.
    static class AuctionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            // We only accept "POST" requests (requests that bring a "package" of JSON data)
            if ("POST".equals(exchange.getRequestMethod())) {

                // --- 2a. UNPACKING THE REQUEST ---
                // Open the package the client sent and read the text inside
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                // Convert that text into a smart JSON object so we can grab specific fields easily
                JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();

                // What does the client want to do? (e.g., "LOGIN", "BID", "GET_ITEMS")
                String action = request.get("action").getAsString();

                // Prepare a blank JSON object to send BACK to the client when we are done
                JsonObject response = new JsonObject();

                // --- 3. DATABASE ACTIONS ---
                // Open a connection to SQLite. The try-with-resources (try(...)) ensures it closes automatically.
                try (Connection conn = DatabaseManager.getConnection()) {

                    // -- ACTION: LOGIN --
                    if (action.equals("LOGIN")) {
                        // '?' protects against SQL Injection hackers!
                        PreparedStatement ps = conn.prepareStatement("SELECT id, role FROM users WHERE username = ? AND password = ?");
                        ps.setString(1, request.get("username").getAsString());
                        ps.setString(2, request.get("password").getAsString());
                        ResultSet rs = ps.executeQuery();

                        if (rs.next()) {
                            // Update last_active so the server knows this user is online right now
                            PreparedStatement actPs = conn.prepareStatement("UPDATE users SET last_active = datetime('now') WHERE id = ?");
                            actPs.setInt(1, rs.getInt("id"));
                            actPs.executeUpdate();

                            response.addProperty("status", "SUCCESS");
                            response.addProperty("role", rs.getString("role"));
                            response.addProperty("user_id", rs.getInt("id"));
                            response.addProperty("username", request.get("username").getAsString());
                        } else {
                            response.addProperty("status", "ERROR");
                            response.addProperty("message", "Invalid credentials.");
                        }
                    }

                    // -- ACTION: REGISTER --
                    else if (action.equals("REGISTER")) {
                        String newUsername = request.get("username").getAsString();
                        String newPassword = request.get("password").getAsString();
                        try {
                            PreparedStatement ps = conn.prepareStatement("INSERT INTO users (username, password, role) VALUES (?, ?, 'User')");
                            ps.setString(1, newUsername);
                            ps.setString(2, newPassword);
                            ps.executeUpdate();
                            response.addProperty("status", "SUCCESS");
                            response.addProperty("message", "Account created successfully!");
                        } catch (SQLException e) {
                            // If the username is already taken, SQLite throws a UNIQUE constraint error. Catch it cleanly!
                            if (e.getMessage().contains("UNIQUE constraint failed")) {
                                response.addProperty("status", "ERROR");
                                response.addProperty("message", "Username already exists. Pick another one.");
                            } else {
                                response.addProperty("status", "ERROR");
                                response.addProperty("message", "Database error: " + e.getMessage());
                            }
                        }
                    }

                    // -- ACTION: LIST NEW ITEM --
                    else if (action.equals("LIST_ITEM")) {
                        PreparedStatement ps = conn.prepareStatement("INSERT INTO items (name, description, current_bid, highest_bidder, seller_id, end_time, status) VALUES (?, ?, ?, 'None', ?, datetime('now', '+3 minutes'), 'ACTIVE')");
                        ps.setString(1, request.get("name").getAsString());
                        ps.setString(2, request.has("description") ? request.get("description").getAsString() : "No description.");
                        ps.setDouble(3, request.get("price").getAsDouble());
                        ps.setInt(4, request.get("seller_id").getAsInt());
                        ps.executeUpdate();
                        response.addProperty("status", "SUCCESS");
                    }

                    // -- ACTION: REFRESH DASHBOARD (Runs every 1 second per client) --
                    else if (action.equals("GET_ITEMS")) {
                        // 1. Mark this specific user as "Online" right now
                        if (request.has("user_id")) {
                            PreparedStatement actPs = conn.prepareStatement("UPDATE users SET last_active = datetime('now') WHERE id = ?");
                            actPs.setInt(1, request.get("user_id").getAsInt());
                            actPs.executeUpdate();
                        }

                        // 2. Count how many total users have checked in within the last 5 seconds
                        Statement countStmt = conn.createStatement();
                        ResultSet countRs = countStmt.executeQuery("SELECT COUNT(*) AS online_count FROM users WHERE last_active >= datetime('now', '-5 seconds')");
                        int onlineUsers = countRs.next() ? countRs.getInt("online_count") : 1;
                        response.addProperty("online_users", onlineUsers);

                        // 3. Clean up the database: If an item's timer hit 0, mark it as SOLD
                        Statement expireStmt = conn.createStatement();
                        expireStmt.executeUpdate("UPDATE items SET status = 'SOLD' WHERE end_time <= datetime('now') AND status = 'ACTIVE'");

                        // 4. Gather all currently ACTIVE items to send to the client
                        // CAST(strftime...) calculates exact seconds remaining between now and the end_time
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
                            items.add(item); // Add the item card to our array
                        }
                        response.addProperty("status", "SUCCESS");
                        response.add("items", items);
                    }

                    // -- ACTION: PLACE A BID --
                    else if (action.equals("BID")) {
                        int itemId = request.get("item_id").getAsInt();
                        double bidAmount = request.get("bid_amount").getAsDouble();
                        String bidderName = request.get("bidder_name").getAsString();

                        // First, check the item's current state and figure out who is selling it
                        PreparedStatement checkPs = conn.prepareStatement(
                                "SELECT i.current_bid, u.username AS seller_name " +
                                        "FROM items i JOIN users u ON i.seller_id = u.id " +
                                        "WHERE i.id = ? AND i.status = 'ACTIVE'"
                        );
                        checkPs.setInt(1, itemId);
                        ResultSet rs = checkPs.executeQuery();

                        if (rs.next()) {
                            String sellerName = rs.getString("seller_name");

                            // Rule 1: You can't bid on your own stuff
                            if (bidderName.equals(sellerName)) {
                                response.addProperty("status", "ERROR");
                                response.addProperty("message", "You cannot bid on your own item!");
                            }
                            // Rule 2: Bid must be higher than current price
                            else if (bidAmount > rs.getDouble("current_bid")) {
                                PreparedStatement updatePs = conn.prepareStatement("UPDATE items SET current_bid = ?, highest_bidder = ? WHERE id = ?");
                                updatePs.setDouble(1, bidAmount);
                                updatePs.setString(2, bidderName);
                                updatePs.setInt(3, itemId);
                                updatePs.executeUpdate();
                                response.addProperty("status", "SUCCESS");
                            }
                            // Rule 3: Bid was too low
                            else {
                                response.addProperty("status", "ERROR");
                                response.addProperty("message", "Bid too low!");
                            }
                        } else {
                            response.addProperty("status", "ERROR");
                            response.addProperty("message", "Item not found or already sold!");
                        }
                    }

                    // -- ACTION: GET HISTORY --
                    else if (action.equals("GET_HISTORY")) {
                        String username = request.get("username").getAsString();

                        // Query 1: Get the last 20 globally sold items for the "Public Sales" list
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

                        // Query 2: Get all sold items where THIS specific user was the highest bidder ("My Wins")
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

                    // -- ACTION: ADMIN DELETE --
                    else if (action.equals("DELETE_ITEM")) {
                        PreparedStatement ps = conn.prepareStatement("DELETE FROM items WHERE id = ?");
                        ps.setInt(1, request.get("item_id").getAsInt());
                        ps.executeUpdate();
                        response.addProperty("status", "SUCCESS");
                    }

                    // Fallback for typos in the action name
                    else {
                        response.addProperty("status", "ERROR");
                        response.addProperty("message", "Unknown action requested: " + action);
                    }
                } catch (SQLException e) {
                    // If the database crashes for any reason, catch it and tell the client safely.
                    response.addProperty("status", "ERROR");
                    response.addProperty("message", "Database error: " + e.getMessage());
                }

                // --- 4. PACKAGING AND SENDING THE RESPONSE ---
                // Turn our JSON response object into a flat string of text
                String responseBody = response.toString();

                // Tell the client "Hey, expect a JSON package!"
                exchange.getResponseHeaders().set("Content-Type", "application/json");

                // Send the HTTP 200 (Success) code and tell it how heavy the package is
                exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);

                // Open the out-bound pipe and shove the text down it
                OutputStream os = exchange.getResponseBody();
                os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                os.close();

            } else {
                // If a client tries to use GET instead of POST, reject them with a 405 error (Method Not Allowed)
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
}