package com.auction.service;

import com.auction.api.http.ApiClient;
import com.auction.core.model.Auction;
import com.auction.core.model.Bidder;
import com.auction.core.model.Seller;
import com.auction.core.model.User;
import com.auction.api.server.AuctionSerializer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        return bidder;
    }

    public List<Auction> getAllAuctions() {
        return fetchAuctions("/api/auctions");
    }

    public List<Auction> getPublicAuctions() {
        return fetchAuctions("/api/auctions?filter=public");
    }

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
