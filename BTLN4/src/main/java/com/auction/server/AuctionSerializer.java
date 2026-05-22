package com.auction.server;

import com.auction.model.Admin;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.BidTransaction;
import com.auction.model.Seller;
import com.auction.model.User;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.LocalDateTime;

/**
 * AuctionSerializer – shared JSON serialization helpers.
 *
 * Used by both AuctionWebSocketHandler (WS broadcasts) and
 * RestApiHandler (HTTP responses) to produce identical JSON shapes,
 * so the client only needs one deserialization path regardless of
 * whether data arrives over REST or WebSocket.
 */
public final class AuctionSerializer {

    private AuctionSerializer() { }

    // ── Auction → JsonObject ──────────────────────────────────────────────────

    /**
     * Converts an Auction to a JsonObject suitable for API responses and WS
     * broadcasts. The optional {@code type} field is included when non-empty
     * (e.g. "AUCTION_CREATED"), omitted for pure REST responses.
     */
    public static JsonObject auctionToJson(String type, Auction a, boolean includeBids) {
        JsonObject o = new JsonObject();
        if (type != null && !type.isEmpty()) o.addProperty("type", type);
        o.addProperty("auctionId",        a.getId());
        o.addProperty("auctionCreatedAt", a.getCreatedAt().toString());
        o.addProperty("itemName",         a.getItem().getName());
        o.addProperty("itemId",           a.getItem().getId());
        o.addProperty("itemDesc",         a.getItem().getDescription());
        o.addProperty("itemCategory",     a.getItem().getCategory());
        o.addProperty("itemImageUrl",     a.getItem().getImageUrl());
        o.addProperty("startPrice",       a.getItem().getStartingPrice());
        o.addProperty("sellerId",         a.getSeller().getId());
        o.addProperty("sellerUsername",   a.getSeller().getUsername());
        o.addProperty("status",           a.getStatus().name());
        o.addProperty("highestBid",       a.getHighestBid());
        o.addProperty("startTime",        a.getStartTime() != null
                ? a.getStartTime().toString() : "");
        o.addProperty("endTime",          a.getEndTime().toString());

        if (includeBids) {
            JsonArray bids = new JsonArray();
            for (BidTransaction bt : a.getBidHistory()) {
                JsonObject bid = new JsonObject();
                bid.addProperty("bidId",          bt.getId());
                bid.addProperty("bidderId",       bt.getBidder().getId());
                bid.addProperty("bidderUsername", bt.getBidder().getUsername());
                bid.addProperty("amount",         bt.getAmount());
                bid.addProperty("time",           bt.getTimestamp().toString());
                bids.add(bid);
            }
            o.add("bidHistory", bids);

            JsonArray autoBidsJson = new JsonArray();
            for (com.auction.model.AutoBid ab : a.getAutoBids()) {
                JsonObject abJson = new JsonObject();
                abJson.addProperty("id", ab.getId());
                abJson.addProperty("auctionId", ab.getAuctionId());
                abJson.addProperty("bidderId", ab.getBidderId());
                abJson.addProperty("maxBid", ab.getMaxBid());
                abJson.addProperty("increment", ab.getIncrement());
                abJson.addProperty("createdAt", ab.getCreatedAt().toString());
                autoBidsJson.add(abJson);
            }
            o.add("autoBids", autoBidsJson);
        }
        return o;
    }

    /** Convenience overload with no type field (for REST responses). */
    public static JsonObject auctionToJson(Auction a, boolean includeBids) {
        return auctionToJson(null, a, includeBids);
    }

    /** Legacy overload: includes bids by default. */
    public static JsonObject auctionToJson(Auction a) {
        return auctionToJson(null, a, true);
    }

    // ── User → JsonObject ─────────────────────────────────────────────────────

    /**
     * Converts a User (any subtype) to a JsonObject for REST responses.
     * The "role" field is the discriminator the client uses to reconstruct
     * the correct subtype (Bidder / Seller / Admin).
     */
    public static JsonObject userToJson(User user) {
        JsonObject o = new JsonObject();
        o.addProperty("id",        user.getId());
        o.addProperty("username",  user.getUsername());
        o.addProperty("role",      user.getRole());        // "Bidder" | "Seller" | "Admin"
        o.addProperty("createdAt", user.getCreatedAt().toString());

        if (user instanceof Bidder bidder) {
            o.addProperty("balance",          bidder.getAccountBalance());
            o.addProperty("frozenBalance",    bidder.getFrozenBalance());
            o.addProperty("availableBalance", bidder.getAvailableBalance());
        } else if (user instanceof Seller seller) {
            o.addProperty("shopName",  seller.getShopName());
            o.addProperty("rating",    seller.getRating());
            o.addProperty("cntvoted",  seller.getCntvoted());
            o.addProperty("balance",   0.0);
        } else if (user instanceof Admin admin) {
            o.addProperty("level",   admin.getAccessLevel());
            o.addProperty("balance", 0.0);
        }
        return o;
    }

    // ── Client-side deserialization helpers ───────────────────────────────────
    // These are used by ApiClient / AppFacade on the CLIENT side.
    // Placed here so they can be shared if the project later splits into modules.

    /**
     * Reconstruct a User subtype from JSON returned by the login / users APIs.
     * Returns null if the JSON is malformed.
     */
    public static User userFromJson(JsonObject o) {
        try {
            String id        = o.get("id").getAsString();
            String username  = o.get("username").getAsString();
            String role      = o.get("role").getAsString();
            String createdAt = o.get("createdAt").getAsString();
            LocalDateTime ts = LocalDateTime.parse(createdAt);

            return switch (role) {
                case "Bidder" -> {
                    double balance = o.has("balance")       ? o.get("balance").getAsDouble()       : 0.0;
                    double frozen  = o.has("frozenBalance") ? o.get("frozenBalance").getAsDouble() : 0.0;
                    yield new Bidder(id, ts, username, "", balance, frozen);
                }
                case "Seller" -> {
                    String shopName = o.has("shopName") ? o.get("shopName").getAsString()
                            : username + "_Shop";
                    double rating   = o.has("rating")   ? o.get("rating").getAsDouble()  : 0.0;
                    int cntvoted    = o.has("cntvoted")  ? o.get("cntvoted").getAsInt()   : 0;
                    yield new Seller(id, ts, username, "", shopName, rating, cntvoted);
                }
                case "Admin" -> {
                    int level = o.has("level") ? o.get("level").getAsInt() : 1;
                    yield new Admin(id, ts, username, "", level);
                }
                default -> null;
            };
        } catch (Exception e) {
            System.err.println("[AuctionSerializer] userFromJson error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Reconstruct an Auction from a JSON snapshot (same shape as auctionToJson).
     * Used by ApiClient / AppFacade after fetching from REST endpoints.
     */
    public static Auction auctionFromJson(JsonObject json) {
        try {
            String auctionId      = json.get("auctionId").getAsString();
            String itemName       = json.get("itemName").getAsString();
            String sellerUsername = json.get("sellerUsername").getAsString();
            String sellerId       = json.get("sellerId").getAsString();
            String statusStr      = json.get("status").getAsString();
            double highestBid     = json.get("highestBid").getAsDouble();
            String endTimeStr     = json.get("endTime").getAsString();
            String createdAtStr   = json.has("auctionCreatedAt")
                    ? json.get("auctionCreatedAt").getAsString()
                    : LocalDateTime.now().toString();

            com.auction.model.AuctionStatus status = com.auction.model.AuctionStatus.valueOf(statusStr);
            LocalDateTime endTime   = LocalDateTime.parse(endTimeStr);
            LocalDateTime createdAt = LocalDateTime.parse(createdAtStr);

            String startTimeStr = json.has("startTime") ? json.get("startTime").getAsString() : "";
            LocalDateTime startTime = (startTimeStr == null || startTimeStr.isEmpty())
                    ? null : LocalDateTime.parse(startTimeStr);

            double startPrice = json.has("startPrice")  ? json.get("startPrice").getAsDouble()  : highestBid;
            String desc       = json.has("itemDesc")     ? json.get("itemDesc").getAsString()     : "";
            String imageUrl   = json.has("itemImageUrl") ? json.get("itemImageUrl").getAsString() : "";
            String category   = json.has("itemCategory") ? json.get("itemCategory").getAsString() : "Điện tử";

            Seller seller = new Seller(sellerId, createdAt, sellerUsername, "",
                    sellerUsername + "_Shop", 0, 0);

            com.auction.model.Item item = switch (category) {
                case "Nghệ thuật" -> new com.auction.model.Art(
                        itemName, desc, startPrice, seller);
                case "Xe cộ"      -> new com.auction.model.Vehicle(
                        itemName, desc, startPrice, seller);
                default           -> new com.auction.model.Electronics(
                        itemName, desc, startPrice, seller);
            };
            item.setImageUrl(imageUrl);

            Auction a = new Auction(auctionId, createdAt, seller, item,
                    status, highestBid, startTime, endTime);

            // Inject bid history if present
            if (json.has("bidHistory")) {
                JsonArray bids = json.get("bidHistory").getAsJsonArray();
                for (int i = 0; i < bids.size(); i++) {
                    JsonObject b = bids.get(i).getAsJsonObject();
                    String bidId = b.get("bidId").getAsString();
                    double amt   = b.get("amount").getAsDouble();
                    String bName = b.get("bidderUsername").getAsString();
                    String bId   = b.get("bidderId").getAsString();
                    LocalDateTime ts = LocalDateTime.parse(b.get("time").getAsString());
                    if (com.auction.util.ServerConfig.isRemote()) {
                        ts = ts.plusHours(7);
                    }
                    Bidder dummy = new Bidder(bId, ts, bName, "", 0);
                    a.injectBid(new BidTransaction(bidId, ts, dummy, a, amt));
                }
            }

            if (json.has("autoBids")) {
                JsonArray autoBids = json.get("autoBids").getAsJsonArray();
                for (int i = 0; i < autoBids.size(); i++) {
                    JsonObject abJson = autoBids.get(i).getAsJsonObject();
                    String abId = abJson.get("id").getAsString();
                    String abAuctionId = abJson.get("auctionId").getAsString();
                    String abBidderId = abJson.get("bidderId").getAsString();
                    double maxBid = abJson.get("maxBid").getAsDouble();
                    double increment = abJson.get("increment").getAsDouble();
                    LocalDateTime ts = LocalDateTime.parse(abJson.get("createdAt").getAsString());
                    if (com.auction.util.ServerConfig.isRemote()) {
                        ts = ts.plusHours(7);
                    }
                    a.injectAutoBid(new com.auction.model.AutoBid(abId, abAuctionId, abBidderId, maxBid, increment, ts));
                }
            }

            return a;
        } catch (Exception e) {
            System.err.println("[AuctionSerializer] auctionFromJson error: " + e.getMessage());
            return null;
        }
    }
}
