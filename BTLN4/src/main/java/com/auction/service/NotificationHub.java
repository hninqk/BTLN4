package com.auction.service;

import com.auction.client.AuctionClient;
import com.auction.model.*;
import com.auction.util.SessionManager;
import com.auction.util.TimeSyncManager;
import com.google.gson.*;
import javafx.application.Platform;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * NotificationHub – Singleton quản lý MỘT kết nối WebSocket dùng chung
 * dành riêng cho việc phân phối thông báo theo thời gian thực đến
 * DesktopHeaderController trên mọi màn hình.
 *
 * Kiến trúc:
 * WS Server → NotificationHub (lắng nghe) → lọc theo Role/User → Listener[]
 *
 * Thread-safety:
 * - listeners được quản lý bởi CopyOnWriteArraySet (safe across threads)
 * - Cache sử dụng ConcurrentHashMap
 * - Tất cả dispatch() đều gọi trên FX Application Thread (qua
 * Platform.runLater)
 */
public class NotificationHub {

    // ── Kiểu thông báo ───────────────────────────────────────────────────────

    public enum NotificationType {
        OUTBID, // Bidder bị vượt giá
        AUCTION_END, // Phiên kết thúc (Bidder tham gia)
        AUCTION_APPROVED, // Seller: Admin duyệt sản phẩm
        AUCTION_CANCELED // Seller: Admin hủy phiên
    }

    /** Sự kiện thông báo bất biến (record = immutable value object). */
    public record NotificationEvent(
            NotificationType type,
            String message,
            String auctionId,
            LocalDateTime timestamp) {
    }

    /**
     * Giao diện callback đăng ký nhận thông báo (thực thi bởi
     * DesktopHeaderController).
     */
    public interface Listener {
        /** Được gọi trên FX Application Thread. */
        void onNotification(NotificationEvent event);
    }

    // ── Singleton ────────────────────────────────────────────────────────────

    private static NotificationHub instance;

    public static synchronized NotificationHub getInstance() {
        if (instance == null)
            instance = new NotificationHub();
        return instance;
    }

    private NotificationHub() {
    }

    // ── Trạng thái nội bộ ────────────────────────────────────────────────────

    private AuctionClient wsClient;
    private volatile boolean connected = false;
    private final Gson gson = new Gson();

    /** Danh sách Listener (DesktopHeaderController instances). Thread-safe. */
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    /**
     * Cache metadata phiên đấu giá: auctionId → itemName.
     * Được populate từ FULL_SYNC và AUCTION_CREATED.
     */
    private final Map<String, String> itemNameCache = new ConcurrentHashMap<>();

    /**
     * Cache người bán: auctionId → sellerId.
     * Dùng để lọc thông báo APPROVED / CANCELED cho đúng Seller.
     */
    private final Map<String, String> sellerIdCache = new ConcurrentHashMap<>();

    /**
     * Tập hợp auctionId mà Bidder hiện tại đã tham gia đặt giá.
     * Dùng để lọc OUTBID và AUCTION_END.
     */
    private final Set<String> bidderParticipated = ConcurrentHashMap.newKeySet();

    // ── API Công khai ────────────────────────────────────────────────────────

    /** Đăng ký một Listener để nhận thông báo. */
    public void addListener(Listener l) {
        listeners.add(l);
    }

    /** Hủy đăng ký Listener (gọi khi controller bị cleanup). */
    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /**
     * Kết nối đến WS server nếu chưa kết nối.
     * An toàn khi gọi nhiều lần – chỉ mở một kết nối duy nhất.
     */
    public synchronized void ensureConnected() {
        if (connected)
            return;
        wsClient = new AuctionClient();
        Thread t = new Thread(() -> wsClient.connect(
                msg -> Platform.runLater(() -> handleMessage(msg)),
                err -> {
                    connected = false;
                    System.err.println("[NotificationHub] WS disconnected: " + err);
                },
                () -> {
                    connected = true;
                    sendRequestSync();
                    System.out.println("[NotificationHub] WS connected, sent REQUEST_SYNC.");
                }), "NotificationHub-WS");
        t.setDaemon(true);
        t.start();
    }

    /** Ngắt kết nối (gọi khi logout). */
    public synchronized void disconnect() {
        if (wsClient != null && connected) {
            wsClient.disconnect();
            connected = false;
        }
    }

    /**
     * Reset cache phiên (gọi sau logout để tránh rò rỉ dữ liệu session cũ).
     */
    public void reset() {
        bidderParticipated.clear();
        itemNameCache.clear();
        sellerIdCache.clear();
        System.out.println("[NotificationHub] Session cache reset.");
    }

    // ── Xử lý WS Message ─────────────────────────────────────────────────────

    private void sendRequestSync() {
        if (wsClient == null || !wsClient.isConnected())
            return;
        JsonObject req = new JsonObject();
        req.addProperty("type", "REQUEST_SYNC");
        wsClient.send(req.toString());
    }

    private void handleMessage(String raw) {
        try {
            JsonElement el = gson.fromJson(raw, JsonElement.class);
            if (!el.isJsonObject())
                return;
            JsonObject json = el.getAsJsonObject();
            String type = json.has("type") ? json.get("type").getAsString() : "";

            switch (type) {
                case "FULL_SYNC" -> onFullSync(json);
                case "AUCTION_CREATED" -> cacheAuction(json);
                case "BID_UPDATE" -> onBidUpdate(json);
                case "AUCTION_STATUS_CHANGED" -> onStatusChanged(json);
                // Các loại khác (BALANCE_UPDATE, AUTO_BID_*) không cần xử lý ở đây
            }
        } catch (Exception e) {
            System.err.println("[NotificationHub] parse error: " + e.getMessage());
        }
    }

    // ── FULL_SYNC: Khởi tạo cache metadata + dấu vết tham gia của Bidder ────

    private void onFullSync(JsonObject json) {
        if (!json.has("auctions"))
            return;
        User me = SessionManager.getInstance().getCurrentUser();
        JsonArray arr = json.get("auctions").getAsJsonArray();

        for (int i = 0; i < arr.size(); i++) {
            JsonObject auctionJson = arr.get(i).getAsJsonObject();
            cacheAuction(auctionJson);

            // Bidder: đánh dấu các phiên mà user đã đặt giá trước đây
            if (me instanceof Bidder && auctionJson.has("bidHistory")) {
                String auctionId = safeGet(auctionJson, "auctionId");
                if (auctionId == null)
                    continue;
                JsonArray bids = auctionJson.get("bidHistory").getAsJsonArray();
                for (int j = 0; j < bids.size(); j++) {
                    JsonObject b = bids.get(j).getAsJsonObject();
                    String bidderId = safeGet(b, "bidderId");
                    if (me.getId().equals(bidderId)) {
                        bidderParticipated.add(auctionId);
                        break;
                    }
                }
            }
        }
    }

    // ── AUCTION_CREATED / cache helper ────────────────────────────────────────

    private void cacheAuction(JsonObject a) {
        String id = safeGet(a, "auctionId");
        if (id == null)
            return;
        String name = safeGet(a, "itemName");
        String sid = safeGet(a, "sellerId");
        if (name != null)
            itemNameCache.put(id, name);
        if (sid != null)
            sellerIdCache.put(id, sid);
    }

    // ── BID_UPDATE ────────────────────────────────────────────────────────────

    private void onBidUpdate(JsonObject json) {
        User me = SessionManager.getInstance().getCurrentUser();
        if (me == null || !(me instanceof Bidder))
            return;

        String auctionId = safeGet(json, "auctionId");
        String bidderId = safeGet(json, "bidderId");
        if (auctionId == null)
            return;

        if (me.getId().equals(bidderId)) {
            // Chính mình vừa đặt giá → đánh dấu tham gia
            bidderParticipated.add(auctionId);
        } else if (bidderParticipated.contains(auctionId)) {
            // Người khác vừa vượt giá mình
            String itemName = itemNameCache.getOrDefault(auctionId, "phiên đấu giá này");
            String msg = "[Tin đấu giá] Một người dùng khác đã đặt mức giá cao hơn tại phiên đấu giá sản phẩm "
                    + itemName + " mà bạn đang tham gia!";
            dispatch(new NotificationEvent(
                    NotificationType.OUTBID, msg, auctionId, TimeSyncManager.getNow()));
        }
    }

    // ── AUCTION_STATUS_CHANGED ────────────────────────────────────────────────

    private void onStatusChanged(JsonObject json) {
        User me = SessionManager.getInstance().getCurrentUser();
        if (me == null)
            return;

        String auctionId = safeGet(json, "auctionId");
        String newStatusStr = safeGet(json, "newStatus");
        if (auctionId == null || newStatusStr == null)
            return;

        String itemName = itemNameCache.getOrDefault(auctionId, "sản phẩm");

        switch (newStatusStr) {

            case "CLOSED" -> {
                // Bidder tham gia phiên này → thông báo kết thúc
                if (me instanceof Bidder && bidderParticipated.contains(auctionId)) {
                    String msg = "[Kết thúc] Phiên đấu giá sản phẩm " + itemName
                            + " bạn tham gia đã chính thức khép lại. Hãy kiểm tra kết quả ngay!";
                    dispatch(new NotificationEvent(
                            NotificationType.AUCTION_END, msg, auctionId, TimeSyncManager.getNow()));
                }
            }

            case "OPEN" -> {
                // Admin vừa duyệt PENDING → OPEN: thông báo cho đúng Seller
                String sellerId = sellerIdCache.get(auctionId);
                if (me instanceof Seller && me.getId().equals(sellerId)) {
                    String msg = "[Phê duyệt] Chúc mừng! Sản phẩm \"" + itemName
                            + "\" của bạn đã được ban quản trị phê duyệt và chính thức bắt đầu phiên đấu giá.";
                    dispatch(new NotificationEvent(
                            NotificationType.AUCTION_APPROVED, msg, auctionId, TimeSyncManager.getNow()));
                }
            }

            case "CANCELED" -> {
                // Admin hủy phiên → thông báo cho đúng Seller
                String sellerId = sellerIdCache.get(auctionId);
                if (me instanceof Seller && me.getId().equals(sellerId)) {
                    String msg = "[Hủy phiên] Cảnh báo: Phiên đấu giá sản phẩm \"" + itemName
                            + "\" của bạn đã bị ban quản trị hủy bỏ.";
                    dispatch(new NotificationEvent(
                            NotificationType.AUCTION_CANCELED, msg, auctionId, TimeSyncManager.getNow()));
                }
            }
        }
    }

    // ── Dispatch ─────────────────────────────────────────────────────────────

    /** Gửi sự kiện đến tất cả Listener đã đăng ký (luôn trên FX thread). */
    private void dispatch(NotificationEvent event) {
        // Platform.runLater đã được gọi bên ngoài trong handleMessage()
        for (Listener l : listeners) {
            l.onNotification(event);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String safeGet(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull())
            return null;
        return json.get(key).getAsString();
    }
}
