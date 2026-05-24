# 🏛️ Nền Tảng Đấu Giá Thời Gian Thực Phân Tán (BTLN4)
### *Tài Liệu Tổng Hợp Kiến Trúc Phần Mềm & Mô Tả Hệ Thống*

---

## 📖 Tổng Quan

**BTLN4** là một nền tảng quản lý đấu giá thời gian thực, hiệu năng cao, và phân tán. Ứng dụng được chia thành một microservice backend nhẹ sử dụng giao thức truyền thông dựa trên WebSocket và REST APIs, cùng một ứng dụng desktop JavaFX hiện đại, chất lượng cao theo mô hình MVC.

Các mục tiêu thiết kế chính của hệ thống bao gồm:
*   **An Toàn Đồng Thời Tuyệt Đối**: Giải quyết các xung đột đặt giá và điều chỉnh số dư đa luồng một cách liền mạch, không xảy ra race condition.
*   **Dịch Vụ Tự Động & Ủy Quyền (Proxy Bidding)**: Lập lịch nền tự động quản lý vòng đời phiên đấu giá và engine tự động đặt giá (auto-bid).
*   **Kiến Trúc Chuẩn Design Pattern**: Áp dụng triệt để các mẫu thiết kế phần mềm (MVC, Factory, Facade, Observer) giúp mã nguồn mở rộng và bảo trì dễ dàng.
*   **Trải Nghiệm Người Dùng (UX/UI) Đỉnh Cao**: Giao diện JavaFX linh hoạt, hỗ trợ chế độ sáng/tối, tích hợp hiệu ứng gợn sóng (ripple) và biểu đồ đấu giá trực quan.

---

## 🛠️ Ngăn Xếp Công Nghệ

| Thành Phần | Công Nghệ | Phiên Bản / Tính Năng |
| :--- | :--- | :--- |
| **Môi Trường Runtime** | Java SE | Phiên bản 17+ (Tối ưu cho JDK 21) |
| **Framework Backend** | Javalin | v6.1.3 (REST Endpoints & WebSockets An Toàn Luồng) |
| **Bộ Công Cụ UI Desktop** | JavaFX / FXML | Tách biệt giao diện (Controller/FXML), hỗ trợ Biểu đồ & Canvas |
| **Thành Phần UI & Biểu Tượng** | ControlsFX / Ikonli | Đồ họa vector, điều khiển nâng cao cho JavaFX |
| **Quản Lý Kết Nối & DB** | JDBC / HikariCP / PostgreSQL | Data Access Object (DAO) với Connection Pooling siêu tốc |
| **Tuần Tự Hóa & Bảo Mật** | Gson / Argon2 | Tuần tự hóa JSON payload WebSockets, băm mật khẩu an toàn bằng Argon2id |
| **Quản Lý Lưu Trữ Ảnh** | Catbox API | Upload ảnh lên Cloud ẩn danh qua `CatboxUploader` |
| **Build & CI/CD** | Apache Maven / Actions | Cấu hình build tự động, quản lý dependencies (`pom.xml`) |

---

## 📦 Cấu Trúc Dự Án & Design Patterns

Hệ thống tuân thủ chặt chẽ nguyên lý S.O.L.I.D và chia thành các package rõ ràng:

```text
BTLN4/src/main/java/com/auction/
├── client/         # Client WebSocket & HTTP gọi API lên Server (ApiClient, AuctionClient)
├── controller/     # Tầng điều khiển giao diện (Login, Dashboard, LiveBidding, Admin, v.v.)
├── exception/      # Lỗi nghiệp vụ tùy chỉnh (InvalidBidException, InvalidStatusException)
├── factory/        # [Factory Pattern] Tạo Item: ArtFactory, ElectronicsFactory, VehicleFactory
├── manager/        # Quản lý trung tâm cho Đấu giá (AuctionManager)
├── model/          # [Observer Pattern] Entities (User, Bidder, Item, Auction, AutoBid, v.v.)
├── repository/     # Lớp truy xuất dữ liệu (JdbcUserRepository, JdbcAuctionRepository, v.v.)
├── security/       # Dịch vụ bảo mật mật khẩu (PasswordHashService)
├── server/         # Khởi tạo Server, WebSocket Handler & REST API (Javalin)
├── service/        # [Facade Pattern] Logic lõi: AppFacade, ProxyBiddingEngine, UserService
└── util/           # Tiện ích: SessionManager, NavigationManager, AnimationUtil, CatboxUploader...
```

### Các Mẫu Thiết Kế (Design Patterns) Nổi Bật:
1.  **MVC (Model-View-Controller)**: Toàn bộ ứng dụng JavaFX được chia tách Model (`com.auction.model`), View (`.fxml` và `main.css`), và Controller (`com.auction.controller`).
2.  **Factory Method**: Package `com.auction.factory` giúp khởi tạo các loại sản phẩm khác nhau (`Art`, `Electronics`, `Vehicle`) từ giao diện `ItemFactory`.
3.  **Facade**: `AppFacade` (trong `service`) cung cấp một giao diện thống nhất cấp cao giúp các Controller dễ dàng tương tác với các hệ thống con phức tạp.
4.  **Observer**: Được triển khai trong Model (`Subject`, `Observer`) để các thực thể UI tự động phản hồi lại khi có thay đổi trạng thái đấu giá hoặc giá thầu.
5.  **Singleton**: Áp dụng trong cấu hình kết nối DB (`DatabaseConnection`) và quản lý bộ nhớ đệm (`CacheManager`, `SessionManager`).

---

## 💎 Tính Năng Cốt Lõi

### 1. Hệ Thống Đặt Giá Thời Gian Thực & Cơ Chế Đóng Băng Tài Khoản 🔒
Giải quyết tính toàn vẹn tài chính trong môi trường đa luồng qua cơ chế **Đóng Băng Quỹ (Fund Freezing)** tại `AuctionService` & `BiddingService`:
*   **Luồng Phân Bổ Quỹ**:
    1. Người dùng đặt giá, hệ thống kiểm tra `availableBalance` qua `JdbcUserRepository`.
    2. Nếu hợp lệ, tiền chuyển từ `availableBalance` sang `frozenBalance`.
    3. **Hoàn Trả Khi Bị Vượt Giá (Outbid)**: Nếu người dùng khác vượt giá, số tiền đang bị đóng băng của người cũ được tự động hoàn lại (`unfreezeFunds`) theo thời gian thực qua WebSockets.
*   **An Toàn Đa Luồng**: Sử dụng `ReentrantLock` (khóa đồng thời) cho mỗi `userId` khi xử lý giao dịch.

### 2. Engine Ủy Quyền Đặt Giá (Proxy Bidding / Auto-Bid) 🤖
Do `ProxyBiddingEngine` và `JdbcAutoBidRepository` đảm nhiệm, cho phép người dùng cấu hình giá tối đa (`maxBid`) và bước giá:
*   Hệ thống tự động thay mặt người dùng đua giá nếu có người chơi khác tham gia, luôn duy trì giá thấp nhất có thể để chiến thắng.
*   Tự động phát hiện và giải quyết xung đột khi có nhiều Auto-Bids trong cùng một phiên.

### 3. Bảo Vệ Chống Snipe (Anti-Snipe / Thời Gian Vàng) ⏱️
Chống lại các bot "bắn tỉa" giây cuối bằng cách tự động gia hạn thời gian (`endTime`):
*   Tính năng tích hợp trong `AuctionManager`: Nếu một giá hợp lệ được đặt trong vòng **60 giây** cuối, thời gian kết thúc tự động cộng thêm **3 phút**, đảm bảo tính công bằng tuyệt đối.

### 4. Truyền Thông Dữ Liệu Thời Gian Thực & API 🌐
Sử dụng `AuctionWebSocketHandler` và `RestApiHandler` trên Javalin:
*   **WebSocket Payload (Client <-> Server)**: Các lệnh `PLACE_BID`, `REGISTER_AUTO_BID`, `BID_UPDATE`, `BALANCE_UPDATE` tuần tự hóa bằng Gson (`AuctionSerializer`).
*   Khả năng cập nhật biểu đồ lịch sử đấu giá realtime (`AuctionChartHelper`) trên giao diện người dùng.

### 5. Tối Ưu Hiệu Năng: Caching & Tiện Ích ⚡
*   **Cache RAM**: `CacheManager` & `HotItemCache` lưu trữ nhanh danh sách phiên đấu giá hiện hành, giảm tải truy xuất DB (PostgreSQL).
*   **Image Caching**: `ImageLoaderUtil` tải ảnh bất đồng bộ từ Cloud về local cache, ngăn UI thread bị "đóng băng".
*   **Đồng Bộ Thời Gian**: `TimeSyncManager` đồng bộ chuẩn xác đồng hồ Client với Server.

### 6. Trải Nghiệm Giao Diện Người Dùng (UI/UX) Cao Cấp 🎨
*   **Quản Lý Điều Hướng Chuyên Nghiệp**: `NavigationManager` điều phối chuyển đổi FXML.
*   **Hiệu Ứng Nền Mượt Mà**: `AnimationUtil` vẽ các làn sóng Canvas uyển chuyển.
*   **Thống Kê Trực Quan**: Biểu đồ đường thời gian thực (`AuctionChartHelper`) minh họa lịch sử giá của phiên đấu giá.
*   **Quản Lý Đăng Xuất & Bộ Nhớ**: Độ trễ ngắt kết nối an toàn giải phóng tài nguyên mạng, ngăn memory leak rò rỉ bộ nhớ.

### 7. Bảo Mật Tối Đa Với Thuật Toán Argon2id 🛡️
Hệ thống sử dụng thuật toán băm mật khẩu **Argon2id** (thuật toán bảo mật hiện đại nhất, chiến thắng Password Hashing Competition) thông qua `PasswordHashService` để bảo vệ tài khoản người dùng:
*   **Chống Tấn Công Brute-Force & Rainbow Tables**: Tự động sinh ngẫu nhiên Salt (muối) cho mỗi tài khoản, bảo vệ mật khẩu khỏi các cuộc tấn công dò mật khẩu và từ điển.
*   **Kháng Tấn Công Bằng GPU/ASIC**: Cấu hình yêu cầu bộ nhớ cao (memory-hard) và chi phí tính toán của Argon2id làm cho việc bẻ khóa hàng loạt trở nên bất khả thi đối với tin tặc.
*   **Xác Thực An Toàn**: Mật khẩu không bao giờ lưu trữ dưới dạng văn bản thô (plaintext), mọi quá trình đăng nhập và xác minh đều thông qua việc so khớp mã băm an toàn tuyệt đối.

---

## 🗄️ Sơ Đồ Lớp Mô Hình Hệ Thống

```mermaid
classDiagram
%% Thực thể Cốt Lõi
class Entity {
  <<abstract>>
  -String id
  -LocalDateTime createdAt
  +getId()
  +getCreatedAt()
}

class User {
  -String username
  -String email
  -String password
}

class Bidder {
  -double accountBalance
  -double frozenBalance
  +getAvailableBalance()
}

class Seller {
  -String shopName
  -double rating
}

class Admin {
  -int accessLevel
}

User --|> Entity
Bidder --|> User
Seller --|> User
Admin --|> User

%% Sản phẩm (Kế thừa Item & Factory Pattern)
class Item {
  <<abstract>>
  -String description
  -double startingPrice
  -String imageUrl
}
class Electronics {
}

class Art {
  -String artistName
  -int yearCreated
}

class Vehicle {
  -double mileage
  -int year
}

Electronics --|> Item
Art --|> Item
Vehicle --|> Item

%% Đấu giá và Giao Dịch
class Auction {
  -String auctionId
  -Seller seller
  -Item item
  -LocalDateTime startTime
  -LocalDateTime endTime
  -AuctionStatus status
  -BidTransaction highestBid
}

class BidTransaction {
  -String transactionId
  -Bidder bidder
  -double bidAmount
  -LocalDateTime timestamp
}

class AutoBid {
  -String autoBidId
  -String auctionId
  -String bidderId
  -double maxBid
  -double increment
}

class AuctionStatus {
  <<enumeration>>
  PENDING
  OPEN
  RUNNING
  CLOSED
  CANCELED
}
Auction o-- Item : has
Auction o-- Seller : createdBy
Auction o-- BidTransaction : hasHighest
Auction --> AuctionStatus : currentStatus
BidTransaction --> Bidder : placedBy
```

```mermaid
flowchart TD
  subgraph Client["Client (JavaFX)"]
    U[User] --> UI[UI Components]
    UI --> WSClient[WebSocket client]
    UI --> RESTClient[REST client]
  end

  subgraph Server["Server (Javalin)"]
    WS_Srv[WebSocket]
    REST_Srv[REST API]
    AM[AuctionManager]
    Proxy[ProxyBiddingEngine]
    Scheduler[Auto-finish Scheduler]
    DB[(Postgres DB)]
  end

  WSClient --> WS_Srv
  RESTClient --> REST_Srv
  WS_Srv --> AM
  REST_Srv --> AM
  AM --> DB
  AM --> Freeze[Freeze funds]
  Freeze --> DB
  AM --> Broadcast[BROADCAST]
  Broadcast --> WSClient
  AM --> Proxy
  Proxy --> AM
  AM --> AntiSnipe[Anti-Snipe]
  AntiSnipe --> AM
  Scheduler --> AM
  AM --> Finalize[Finalize payments]
  Finalize --> DB

  classDef infra fill:#f9f,stroke:#333,stroke-width:1px;
  class DB,Scheduler infra;
```

```mermaid
sequenceDiagram
    participant Client
    participant UI as "Desktop UI"
    participant WS as "WebSocket"
    participant Server
    participant Manager as "AuctionManager"
    participant Auto as "ProxyBiddingEngine"
    participant DB as "Database"

    Client->>UI: Chọn phiên và nhập giá
    UI->>WS: PLACE_BID(bidAmount)
    WS->>Server: Nhận payload PLACE_BID
    Server->>Manager: processBid(bid)
    Manager->>DB: SELECT availableBalance FOR UPDATE
    DB-->>Manager: availableBalance
    Manager->>DB: freezeFunds(bidAmount)
    DB-->>Manager: frozen confirmed
    Manager-->>Server: bid accepted
    Server->>WS: BROADCAST BID_UPDATE(highestBid)
    alt Auto-bid applicable
        Server->>Auto: evaluateAutoBids(auctionId)
        Auto->>Server: placeAutoBid(...)
        Server->>WS: BROADCAST BID_UPDATE
    end
    alt Bid in last 60s
        Manager->>Manager: extendAuctionEnd(+3m)
        Server->>WS: BROADCAST TIME_EXTENDED(newEndTime)
    end
    Manager->>DB: finalizeAuction(winner)
    DB-->>Server: confirmed
    Server->>WS: BROADCAST AUCTION_CLOSED(winner)
```

---

## **Demo Nhanh (Quick demo)**

Mục tiêu demo: minh hoạ một phiên đấu giá cơ bản — khởi động server, chạy client, tạo phiên, đặt giá tay và kích hoạt auto-bid / anti-snipe.

- **Chế độ nhanh (kết nối tới máy chủ công khai)**
  - Nếu bạn không muốn chạy server cục bộ, chạy `mvn clean javafx:run` để khởi động giao diện khách; theo mặc định client sẽ kết nối tới dịch vụ công khai (Render). Điều này thuận tiện để xem UI và lịch sử phiên đã có sẵn.

- **Chế độ cục bộ (end-to-end, toàn bộ chạy trên máy của bạn)**
  1. Mở Terminal A — khởi động server:

```bash
mvn exec:java -Pserver -Dcheckstyle.skip=true
```

  Khi server khởi động, bạn sẽ thấy thông báo tương tự: "WebSocket + REST server running on port <port>" (mặc định `AppConfig.port()` = 10000).

 2. (Tùy chọn) Nếu bạn muốn client kết nối tới server cục bộ, chỉnh `RENDER_SERVER_URL` trong
     `BTLN4/BTLN4/src/main/java/com/auction/util/AppConfig.java` thành `http://localhost:10000` rồi build/run client.

 3. Mở Terminal B — khởi động client JavaFX:

```bash
mvn clean javafx:run
```

 4. Trong UI: đăng ký 2 tài khoản (seller + bidder), seller tạo một auction (hoặc tạo qua REST API), rồi bidder đặt giá.

- **Tương tác REST (ví dụ tạo auction bằng curl)**

```bash
curl -X POST http://localhost:10000/api/auctions \
  -H "Content-Type: application/json" \
  -d '{"sellerId":"seller-1","itemName":"Vintage Vase","startPrice":100.0,"endTime":"2026-05-24T21:00:00"}'
```

- **Sample WebSocket messages (gửi tới `ws://localhost:10000/auction`)**

PLACE_BID example:
```json
{
  "type": "PLACE_BID",
  "auctionId": "AUCTION_ID",
  "bidderId": "BIDDER_ID",
  "bidderUsername": "alice",
  "amount": 150.0
}
```

REGISTER_AUTO_BID example:
```json
{
  "type": "REGISTER_AUTO_BID",
  "auctionId": "AUCTION_ID",
  "bidderId": "BIDDER_ID",
  "maxBid": 300.0,
  "increment": 5.0
}
```

REQUEST_SYNC example (client asks server for full snapshot):
```json
{ "type": "REQUEST_SYNC" }
```

- **Quan sát:** server sẽ broadcast `BID_UPDATE`, `BALANCE_UPDATE`, `AUCTION_STATUS_CHANGED`, `AUTO_BID_LOG`, `AUTO_BID_ACK`, `AUTO_BID_DEACTIVATED`.

- **Ghi chú:**
  - WebSocket endpoint server-side: `/auction` (ví dụ: `ws://localhost:10000/auction`).
  - Mặc định project dùng `RENDER_SERVER_URL` hướng tới https://btln4.onrender.com — chỉnh `AppConfig` nếu bạn muốn đổi target cho client.

## 🚀 Hướng Dẫn Khởi Động & Vận Hành

### Yêu Cầu Môi Trường
*   **Java Development Kit (JDK)**: Phiên bản **17** trở lên (Khuyến nghị JDK 21).
*   **Apache Maven**: Phiên bản 3.8.x hoặc mới hơn.

### Các Lệnh Thực Thi (Maven)

#### 1. Khởi Động Máy Chủ (Server / Headless)
Bật máy chủ backend quản lý WebSockets, REST API và tiến trình nền (port mặc định 7000):
```bash
mvn exec:java -Pserver -Dcheckstyle.skip=true
```

#### 2. Khởi Động Ứng Dụng Khách (Desktop UI)
Khởi chạy giao diện JavaFX kết nối với máy chủ đấu giá:
```bash
mvn clean javafx:run
```

#### 3. Build & Đóng Gói (Production)
Biên dịch dự án và tạo file thực thi JAR:
```bash
mvn clean package
```

*(Lưu ý: Mọi chi tiết về cấu trúc cơ sở dữ liệu `jdbc` và tùy biến cổng mạng có thể chỉnh sửa trong file `AppConfig` và `ServerConfig`)*
