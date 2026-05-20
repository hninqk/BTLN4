package com.auction.service;

import com.auction.client.ApiClient;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.server.AuctionSerializer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AppFacade – the single entry-point all Controllers use.
 *
 * Architecture (NEW – 3-Tier):
 *   Controllers → AppFacade → ApiClient (HTTP) → Javalin Server → SQLite
 *
 * Key rules enforced here:
 *   • Controllers MUST NOT import ApiClient, AuctionService, UserService, or
 *     any repository class directly.
 *   • All methods in this class are BLOCKING – they perform HTTP calls and
 *     must be called from a background thread (javafx.concurrent.Task or
 *     CompletableFuture).  Platform.runLater() must be used to push results
 *     back to the FX thread.
 *   • The Server machine is the ONLY entity that ever touches SQLite.
 *     No database driver is needed in the Client runtime.
 */
public final class AppFacade {

    private static AppFacade instance;

    private final ApiClient api = ApiClient.getInstance();

    private AppFacade() { }

    public static AppFacade getInstance() {
        if (instance == null) {
            instance = new AppFacade();
        }
        return instance;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Auth
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Authenticate a user against the server.
     * Returns an empty Optional on wrong credentials or network error.
     */
    public Optional<User> login(String username, String password) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("username", username);
            body.addProperty("password", password);
            String raw  = api.postSync("/api/login", body);
            JsonObject json;
            try {
                json = api.parseObject(raw);
            } catch (Exception parseEx) {
                throw new RuntimeException("Server trả về dữ liệu không hợp lệ: " + raw);
            }
            if (api.isError(json)) {
                throw new RuntimeException(json.get("error").getAsString());
            }
            return Optional.ofNullable(AuctionSerializer.userFromJson(json));
        } catch (RuntimeException e) {
            System.err.println("[AppFacade] login failed: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("[AppFacade] login failed: " + e.getMessage());
            throw new RuntimeException("Lỗi kết nối server: " + e.getMessage());
        }
    }

    /**
     * Register a new user on the server.
     * Returns the created User, or empty on conflict / network error.
     */
    public Optional<User> register(String username, String password, String role) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("username", username);
            body.addProperty("password", password);
            body.addProperty("role",     role);
            String raw  = api.postSync("/api/register", body);
            JsonObject json;
            try {
                json = api.parseObject(raw);
            } catch (Exception parseEx) {
                throw new RuntimeException("Server trả về dữ liệu không hợp lệ: " + raw);
            }
            if (api.isError(json)) {
                throw new RuntimeException(json.get("error").getAsString());
            }
            return Optional.ofNullable(AuctionSerializer.userFromJson(json));
        } catch (RuntimeException e) {
            System.err.println("[AppFacade] register failed: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("[AppFacade] register failed: " + e.getMessage());
            throw new RuntimeException("Lỗi kết nối server: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Users
    // ──────────────────────────────────────────────────────────────────────────

    public List<User> getAllUsers() {
        try {
            String   raw  = api.getSync("/api/users");
            JsonArray arr = api.parseArray(raw);
            List<User> list = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                User u = AuctionSerializer.userFromJson(arr.get(i).getAsJsonObject());
                if (u != null) list.add(u);
            }
            return list;
        } catch (Exception e) {
            System.err.println("[AppFacade] getAllUsers failed: " + e.getMessage());
            return List.of();
        }
    }

    public Optional<User> findUserByUsername(String username) {
        // The server doesn't expose a /by-username endpoint; search from the list
        return getAllUsers().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    public Optional<User> findUserById(String id) {
        try {
            String raw = api.getSync("/api/users/" + id);
            JsonObject json = api.parseObject(raw);
            if (api.isError(json)) return Optional.empty();
            return Optional.ofNullable(AuctionSerializer.userFromJson(json));
        } catch (Exception e) {
            System.err.println("[AppFacade] findUserById failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    public boolean deleteUser(String userId) {
        try {
            String raw = api.deleteSync("/api/users/" + userId);
            JsonObject json = api.parseObject(raw);
            return !api.isError(json);
        } catch (Exception e) {
            System.err.println("[AppFacade] deleteUser failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Persist profile changes (password, shopName) back to the server.
     */
    public void saveUser(User user) {
        try {
            JsonObject body = new JsonObject();
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                body.addProperty("password", user.getPassword());
            }
            if (user instanceof Seller seller) {
                body.addProperty("shopName", seller.getShopName());
            }
            api.putSync("/api/users/" + user.getId(), body);
        } catch (Exception e) {
            System.err.println("[AppFacade] saveUser failed: " + e.getMessage());
        }
    }

    /**
     * Top-up a bidder's balance on the server.
     * Returns the refreshed Bidder object (with updated balance) or the
     * original if the call fails.
     */
    public Bidder topupBalance(Bidder bidder, double amount) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("userId", bidder.getId());
            body.addProperty("amount", amount);
            String raw = api.postSync("/api/users/topup", body);
            JsonObject json = api.parseObject(raw);
            if (!api.isError(json)) {
                User updated = AuctionSerializer.userFromJson(json);
                if (updated instanceof Bidder b) return b;
            }
        } catch (Exception e) {
            System.err.println("[AppFacade] topupBalance failed: " + e.getMessage());
        }
        return bidder; // fallback: return unchanged bidder
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Auctions — read
    // ──────────────────────────────────────────────────────────────────────────

    /** All auctions (Admin view). */
    public List<Auction> getAllAuctions() {
        return fetchAuctions("/api/auctions");
    }

    /** Only OPEN + RUNNING — what bidders see. */
    public List<Auction> getPublicAuctions() {
        return fetchAuctions("/api/auctions?filter=public");
    }

    /** All auctions for a specific seller (including PENDING). */
    public List<Auction> getAuctionsBySeller(Seller seller) {
        return fetchAuctions("/api/users/" + seller.getId() + "/auctions");
    }

    public Optional<Auction> findAuctionById(String id) {
        try {
            String raw = api.getSync("/api/auctions/" + id);
            JsonObject json = api.parseObject(raw);
            if (api.isError(json)) return Optional.empty();
            return Optional.ofNullable(AuctionSerializer.auctionFromJson(json));
        } catch (Exception e) {
            System.err.println("[AppFacade] findAuctionById failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Auctions — write
    // (Note: live bidding still goes through WebSocket, not REST)
    // ──────────────────────────────────────────────────────────────────────────

    public boolean removeAuction(String auctionId) {
        try {
            String raw = api.deleteSync("/api/auctions/" + auctionId);
            JsonObject json = api.parseObject(raw);
            return !api.isError(json);
        } catch (Exception e) {
            System.err.println("[AppFacade] removeAuction failed: " + e.getMessage());
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────────────

    private List<Auction> fetchAuctions(String path) {
        try {
            String raw  = api.getSync(path);
            JsonArray arr = api.parseArray(raw);
            List<Auction> list = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                Auction a = AuctionSerializer.auctionFromJson(arr.get(i).getAsJsonObject());
                if (a != null) list.add(a);
            }
            return list;
        } catch (Exception e) {
            System.err.println("[AppFacade] fetchAuctions(" + path + ") failed: " + e.getMessage());
            return List.of();
        }
    }
}
