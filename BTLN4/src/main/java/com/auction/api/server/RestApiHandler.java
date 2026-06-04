package com.auction.api.server;

import com.auction.core.model.Auction;
import com.auction.core.model.AuctionStatus;
import com.auction.core.model.Art;
import com.auction.core.model.Electronics;
import com.auction.core.model.Item;
import com.auction.core.model.Seller;
import com.auction.core.model.User;
import com.auction.core.model.Vehicle;
import com.auction.core.factory.ArtFactory;
import com.auction.core.factory.ElectronicsFactory;
import com.auction.core.factory.ItemFactory;
import com.auction.core.factory.VehicleFactory;
import com.auction.core.model.*;
import com.auction.service.AuctionService;
import com.auction.service.UserService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RestApiHandler – registers all HTTP REST endpoints on the Javalin instance.
 *
 * All responses are JSON. All business logic delegates to AuctionService and
 * UserService (the same singletons used by the WebSocket handler), so there
 * is exactly ONE path to the database.
 *
 * Endpoint summary:
 * POST /api/login – authenticate, returns User JSON
 * POST /api/register – register new user
 * GET /api/auctions – list auctions (?filter=public | all)
 * GET /api/auctions/{id} – single auction
 * POST /api/auctions – create auction (seller)
 * POST /api/auctions/{id}/action – admin finish/cancel
 * DELETE /api/auctions/{id} – remove auction
 * GET /api/users – list all users (admin)
 * GET /api/users/{id} – get single user
 * DELETE /api/users/{id} – delete user (admin)
 * PUT /api/users/{id} – update profile (password, shopName)
 * POST /api/users/topup – bidder top-up balance
 * GET /api/users/{id}/auctions – seller's own auctions
 */
public class RestApiHandler {

    private static final Logger log = LoggerFactory.getLogger(RestApiHandler.class);

    private final AuctionService auctionService;
    private final UserService userService;
    private final Gson gson = new Gson();

    public RestApiHandler(AuctionService auctionService, UserService userService) {
        this.auctionService = auctionService;
        this.userService = userService;
    }

    /** Register all routes on the provided Javalin app. */
    public void register(Javalin app) {
        app.before(ctx -> {
            log.info("HTTP {} {}", ctx.method(), ctx.path());
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type");
        });
        app.after(ctx -> log.info("HTTP {} {} -> {}", ctx.method(), ctx.path(), ctx.status()));
        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unhandled REST error {} {}", ctx.method(), ctx.path(), e);
            ctx.status(500).contentType("application/json").result(errorJson("Internal server error"));
        });
        app.options("/*", ctx -> ctx.status(204)); // CORS pre-flight

        // Health check
        app.get("/api/health", ctx -> ctx.status(200).result("OK"));

        // Server Time for Client Synchronization
        app.get("/api/time/current", ctx -> ctx.status(200).contentType("application/json")
                .result("{\"serverTime\":" + System.currentTimeMillis() + "}"));

        // ── Auth ──────────────────────────────────────────────────────────────
        app.post("/api/login", this::handleLogin);
        app.post("/api/register", this::handleRegister);

        // ── Auctions ──────────────────────────────────────────────────────────
        app.get("/api/auctions", this::handleGetAuctions);
        app.get("/api/auctions/{id}", this::handleGetAuctionById);
        app.post("/api/auctions", this::handleCreateAuction);
        app.post("/api/auctions/{id}/action", this::handleAdminAction);
        app.delete("/api/auctions/{id}", this::handleDeleteAuction);

        // ── Users ─────────────────────────────────────────────────────────────
        app.get("/api/users", this::handleGetAllUsers);
        app.get("/api/users/{id}", this::handleGetUserById);
        app.delete("/api/users/{id}", this::handleDeleteUser);
        app.put("/api/users/{id}", this::handleUpdateUser);
        app.post("/api/users/topup", this::handleTopup);
        app.get("/api/users/{id}/auctions", this::handleGetSellerAuctions);

        System.out.println("[Server] REST API endpoints registered.");
    }

    // =========================================================================
    // AUTH
    // =========================================================================

    private void handleLogin(Context ctx) {
        try {
            JsonObject body = gson.fromJson(ctx.body(), JsonObject.class);
            String username = body.get("username").getAsString();
            String password = body.get("password").getAsString();

            Optional<User> result = userService.login(username, password);
            if (result.isPresent()) {
                ctx.status(200).contentType("application/json")
                        .result(AuctionSerializer.userToJson(result.get()).toString());
            } else {
                ctx.status(401).contentType("application/json")
                        .result("{\"error\":\"Tên đăng nhập hoặc mật khẩu không chính xác.\"}");
            }
        } catch (Exception e) {
            ctx.status(400).contentType("application/json").result(errorJson("Login failed: " + e.getMessage()));
        }
    }

    private void handleRegister(Context ctx) {
        try {
            JsonObject body = gson.fromJson(ctx.body(), JsonObject.class);
            String username = body.get("username").getAsString();
            String password = body.get("password").getAsString();
            String role = body.has("role") ? body.get("role").getAsString() : "Bidder";

            boolean ok = userService.register(username, password, role);
            if (ok) {
                // Return the newly created user so the client can store the session
                Optional<User> created = userService.findByUsername(username);
                if (created.isPresent()) {
                    ctx.status(201).contentType("application/json")
                            .result(AuctionSerializer.userToJson(created.get()).toString());
                } else {
                    ctx.status(201).contentType("application/json").result("{\"message\":\"Đăng ký thành công.\"}");
                }
            } else {
                ctx.status(409).contentType("application/json").result("{\"error\":\"Tên đăng nhập đã tồn tại.\"}");
            }
        } catch (Exception e) {
            ctx.status(400).contentType("application/json").result(errorJson("Register failed: " + e.getMessage()));
        }
    }

    // =========================================================================
    // AUCTIONS
    // =========================================================================

    private void handleGetAuctions(Context ctx) {
        try {
            String filter = ctx.queryParam("filter");
            List<Auction> auctions = "public".equalsIgnoreCase(filter)
                    ? auctionService.getPublicAuctions()
                    : auctionService.getAllAuctions();

            JsonArray arr = new JsonArray();
            for (Auction a : auctions)
                arr.add(AuctionSerializer.auctionToJson(a, true));
            ctx.status(200).contentType("application/json").result(arr.toString());
        } catch (Exception e) {
            ctx.status(500).contentType("application/json")
                    .result(errorJson("Failed to load auctions: " + e.getMessage()));
        }
    }

    private void handleGetAuctionById(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            auctionService.findById(id)
                    .ifPresentOrElse(
                            a -> ctx.status(200).contentType("application/json")
                                    .result(AuctionSerializer.auctionToJson(a, true).toString()),
                            () -> ctx.status(404).contentType("application/json")
                                    .result("{\"error\":\"Auction not found.\"}"));
        } catch (Exception e) {
            ctx.status(500).contentType("application/json").result(errorJson(e.getMessage()));
        }
    }

    private void handleCreateAuction(Context ctx) {
        try {
            JsonObject body = gson.fromJson(ctx.body(), JsonObject.class);
            String sellerId = body.get("sellerId").getAsString();
            String itemName = body.get("itemName").getAsString();
            String category = body.has("category") ? body.get("category").getAsString() : "Điện tử";
            String desc = body.has("description") ? body.get("description").getAsString() : "";
            String imageUrl = body.has("imageUrl") ? body.get("imageUrl").getAsString() : "";
            double startPrice = body.get("startPrice").getAsDouble();
            LocalDateTime startTime = LocalDateTime.parse(body.get("startTime").getAsString());
            LocalDateTime endTime = LocalDateTime.parse(body.get("endTime").getAsString());

            Seller seller = (Seller) userService.findById(sellerId)
                    .filter(u -> u instanceof Seller)
                    .orElseThrow(() -> new Exception("Seller not found: " + sellerId));

            String artistName = body.has("artistName") ? body.get("artistName").getAsString() : "Unknown";
            int warrantyMonths = body.has("warrantyMonths") ? body.get("warrantyMonths").getAsInt() : 12;
            String brand = body.has("brand") ? body.get("brand").getAsString() : "Unknown Brand";

            ItemFactory factory = switch (category) {
                case "Nghệ thuật" -> new ArtFactory(artistName);
                case "Xe cộ" -> new VehicleFactory(brand);
                default -> new ElectronicsFactory(warrantyMonths);
            };
            
            Item item = factory.createItem(itemName, desc, startPrice, seller);
            item.setImageUrl(imageUrl);

            Auction auction = auctionService.createAuction(seller, item, startTime, endTime);
            ctx.status(201).contentType("application/json")
                    .result(AuctionSerializer.auctionToJson(auction, true).toString());
        } catch (Exception e) {
            ctx.status(400).contentType("application/json")
                    .result(errorJson("Create auction failed: " + e.getMessage()));
        }
    }

    private void handleAdminAction(Context ctx) {
        try {
            String auctionId = ctx.pathParam("id");
            JsonObject body = gson.fromJson(ctx.body(), JsonObject.class);
            String action = body.get("action").getAsString();

            Auction auction = auctionService.findById(auctionId)
                    .orElseThrow(() -> new Exception("Auction not found: " + auctionId));

            switch (action) {
                case "approve", "start" -> throw new Exception(
                        "Admin không còn quyền bắt đầu phiên. Phiên sẽ tự bắt đầu theo thời gian Seller đã đặt.");
                case "finish" -> auctionService.finishAuction(auction);
                case "cancel" -> auctionService.cancelAuction(auction);
                default -> throw new Exception("Unknown action: " + action);
            }

            Auction fresh = auctionService.findById(auctionId).orElse(auction);
            ctx.status(200).contentType("application/json")
                    .result(AuctionSerializer.auctionToJson(fresh, true).toString());
        } catch (Exception e) {
            ctx.status(400).contentType("application/json").result(errorJson("Admin action failed: " + e.getMessage()));
        }
    }

    private void handleDeleteAuction(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            boolean removed = auctionService.removeAuction(id);
            if (removed) {
                ctx.status(200).contentType("application/json").result("{\"message\":\"Auction removed.\"}");
            } else {
                ctx.status(404).contentType("application/json").result("{\"error\":\"Auction not found.\"}");
            }
        } catch (Exception e) {
            ctx.status(500).contentType("application/json").result(errorJson(e.getMessage()));
        }
    }

    // =========================================================================
    // USERS
    // =========================================================================

    private void handleGetAllUsers(Context ctx) {
        try {
            JsonArray arr = new JsonArray();
            for (User u : userService.getAllUsers())
                arr.add(AuctionSerializer.userToJson(u));
            ctx.status(200).contentType("application/json").result(arr.toString());
        } catch (Exception e) {
            ctx.status(500).contentType("application/json").result(errorJson(e.getMessage()));
        }
    }

    private void handleGetUserById(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            userService.findById(id)
                    .ifPresentOrElse(
                            u -> ctx.status(200).contentType("application/json")
                                    .result(AuctionSerializer.userToJson(u).toString()),
                            () -> ctx.status(404).contentType("application/json")
                                    .result("{\"error\":\"User not found.\"}"));
        } catch (Exception e) {
            ctx.status(500).contentType("application/json").result(errorJson(e.getMessage()));
        }
    }

    private void handleDeleteUser(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            boolean removed = userService.deleteUser(id);
            if (removed) {
                ctx.status(200).contentType("application/json").result("{\"message\":\"User deleted.\"}");
            } else {
                ctx.status(404).contentType("application/json").result("{\"error\":\"User not found.\"}");
            }
        } catch (Exception e) {
            ctx.status(500).contentType("application/json").result(errorJson(e.getMessage()));
        }
    }

    private void handleUpdateUser(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            JsonObject body = gson.fromJson(ctx.body(), JsonObject.class);

            User user = userService.findById(id)
                    .orElseThrow(() -> new Exception("User not found: " + id));

            if (body.has("password") && !body.get("password").getAsString().isEmpty()) {
                user.setPassword(body.get("password").getAsString());
            }
            if (user instanceof Seller seller && body.has("shopName")) {
                seller.setShopName(body.get("shopName").getAsString());
            }

            userService.saveUser(user);
            ctx.status(200).contentType("application/json").result(AuctionSerializer.userToJson(user).toString());
        } catch (Exception e) {
            ctx.status(400).contentType("application/json").result(errorJson("Update failed: " + e.getMessage()));
        }
    }

    private void handleTopup(Context ctx) {
        try {
            JsonObject body = gson.fromJson(ctx.body(), JsonObject.class);
            String userId = body.get("userId").getAsString();
            double amount = body.get("amount").getAsDouble();
            if (amount <= 0)
                throw new Exception("Amount must be positive.");

            com.auction.core.model.Bidder bidder = (com.auction.core.model.Bidder) userService.findById(userId)
                    .filter(u -> u instanceof com.auction.core.model.Bidder)
                    .orElseThrow(() -> new Exception("Bidder not found: " + userId));

            bidder.AddBalance(amount);
            userService.saveUser(bidder);
            ctx.status(200).contentType("application/json").result(AuctionSerializer.userToJson(bidder).toString());
        } catch (Exception e) {
            ctx.status(400).contentType("application/json").result(errorJson("Topup failed: " + e.getMessage()));
        }
    }

    private void handleGetSellerAuctions(Context ctx) {
        try {
            String sellerId = ctx.pathParam("id");
            Seller seller = (Seller) userService.findById(sellerId)
                    .filter(u -> u instanceof Seller)
                    .orElseThrow(() -> new Exception("Seller not found: " + sellerId));

            JsonArray arr = new JsonArray();
            for (Auction a : auctionService.getAuctionsBySeller(seller)) {
                arr.add(AuctionSerializer.auctionToJson(a, false));
            }
            ctx.status(200).contentType("application/json").result(arr.toString());
        } catch (Exception e) {
            ctx.status(400).contentType("application/json").result(errorJson(e.getMessage()));
        }
    }

    // =========================================================================
    // HELPER
    // =========================================================================

    private String errorJson(String msg) {
        log.warn("Returning REST error: {}", msg);
        return "{\"error\":\"" + (msg != null ? msg.replace("\"", "'") : "Unknown error") + "\"}";
    }
}
