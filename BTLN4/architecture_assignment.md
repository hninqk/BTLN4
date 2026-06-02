# Java Application Architecture Assignment

To divide the workload evenly and logically, the source code has been categorized into 4 architectural modules based on their functional boundaries. Due to the inherent nature of JavaFX applications, the UI module contains the most code by line count, while the DAO module is concise. The circular overlap assignment strategy ensures that every team member understands the boundaries and interactions between adjacent layers.

## 1. Module Division (100% Code Coverage)

### Module A: Client UI (JavaFX)
**Focus:** User interface, user interactions, view routing, and local UI state.
- **Controllers:** `AuctionDetailController`, `DashboardController`, `AdminManagementController`, `UserProfileController`, `BidHistoryController`, `DesktopHeaderController`, `SellerManagementController`, `LiveBiddingController`, `AuctionListController`, `SidebarController`, `SplashController`, `LoginController`, `RegisterController`, `LogoutController`
- **UI Utils:** `NavigationManager`, `ImageLoaderUtil`, `AuctionChartHelper`, `AnimationUtil`, `AlertHelper`
- **Application Entry:** `Main`, `Launcher`

### Module B: Network & API Endpoints (Javalin)
**Focus:** Web server setup, REST APIs, WebSockets, and Client-Server networking.
- **Server/Endpoints:** `ServerMain`, `RestApiHandler`, `AuctionWebSocketHandler`, `AuctionSerializer`, `SecurityHeaderFilter`
- **Configuration:** `AppConfig`, `ServerConfig`
- **Client Networking:** `ApiClient`, `AuctionClient`

### Module C: Services & Models
**Focus:** Core business logic, data models, factories, algorithms, and security.
- **Services:** `AuctionService`, `BiddingService`, `AppFacade`, `ProxyBiddingEngine`, `UserService`, `AuctionWebSocketService`, `PasswordHashService`, `AuctionManager`
- **Models:** `Auction`, `Bidder`, `Item`, `AutoBid`, `BidTransaction`, `Art`, `User`, `Entity`, `Seller`, `Electronics`, `Vehicle`, `Admin`, `AuctionStatus`
- **Factories:** `VehicleFactory`, `ArtFactory`, `ElectronicsFactory`, `ItemFactory`
- **Business Utils & Exceptions:** `NotificationManager`, `CatboxUploader`, `HotItemCache`, `SessionManager`, `CurrencyUtil`, `CacheManager`, `TimeSyncManager`, `BidLadderUtil`, `DataReceiver`, `Subject`, `Observer`, `InvalidStatusException`, `InvalidBidException`

### Module D: Database Access (DAO)
**Focus:** Database connections, SQL queries, mapping ResultSets to Objects.
- **Repositories:** `JdbcAuctionRepository`, `JdbcUserRepository`, `JdbcItemRepository`, `JdbcBidRepository`, `JdbcAutoBidRepository`, `BidRepository`
- **Core DB:** `DatabaseConnection`
- **Global Config:** `module-info.java`

---

## 2. Circular Overlap Assignment & Reading Guide

By overlapping modules, no single person is isolated. Every layer's integration point is reviewed by two different people from different perspectives.

### 👤 Person 1: Reads Module D (Database) & Module C (Services)
**Role:** Backend Core Engineer
**Reading Guide & Interaction:**
Your focus is the "Brain and Memory" of the system. You will learn how raw data in the database (Module D) is translated into complex business rules (Module C). 
- **How to read:** Start by reading `DatabaseConnection` and the `Jdbc*Repository` classes in Module D to understand how data is persisted. Then, move to the Services in Module C (like `UserService` and `BiddingService`) to see how they call these repositories.
- **Interaction to focus on:** Observe how Services orchestrate multiple Repository calls into a single transaction (e.g., placing a bid requires updating the `Auction` entity and inserting a `BidTransaction`).

### 👤 Person 2: Reads Module C (Services) & Module B (Network)
**Role:** Backend API Engineer
**Reading Guide & Interaction:**
Your focus is how the application's internal business logic is exposed to the outside world.
- **How to read:** Start with the Models and Services in Module C to understand what the system can do. Then, read `RestApiHandler` and `AuctionWebSocketHandler` in Module B.
- **Interaction to focus on:** Pay close attention to how HTTP POST requests or WebSocket JSON payloads (Module B) are parsed and fed into method calls in `AuctionService` or `BiddingService` (Module C), and how exceptions from the services (`InvalidBidException`) are translated back into HTTP 400 errors or WS error messages.

### 👤 Person 3: Reads Module B (Network) & Module A (Client UI)
**Role:** Frontend Integration Engineer
**Reading Guide & Interaction:**
Your focus is the bridge between the user's screen and the server. Module A contains the most code, but since you are also reading Module B, you will perfectly understand the data pipeline.
- **How to read:** Read `ApiClient` and `AuctionClient` in Module B to understand how the Java application sends requests. Then, read the Controllers in Module A (like `LoginController`, `LiveBiddingController`) to see how UI buttons trigger these network calls.
- **Interaction to focus on:** Focus on asynchronous UI updates. See how WebSocket messages received in Module B trigger `Platform.runLater()` updates to JavaFX components in Module A (e.g., updating the live price chart or the bidding feed).

### 👤 Person 4: Reads Module A (Client UI) & Module D (Database)
**Role:** Full-Stack Flow Engineer
**Reading Guide & Interaction:**
Your combination is unique. You are looking at the two absolute endpoints of the application: what the user sees (UI) and where the data physically rests (Disk/DB), skipping the middleman.
- **How to read:** Start by familiarizing yourself with the UI screens in Module A (like `DashboardController` and `AuctionDetailController`). Ask yourself: "Where does this data come from?". Then jump straight to Module D (`JdbcAuctionRepository`) to see the exact SQL queries used to fetch that data.
- **Interaction to focus on:** You act as the system optimizer. By knowing exactly what data the UI needs (e.g., Heatmap stats in Module A) and how the database is structured (Module D), you can identify if the UI is making inefficient requests (N+1 query problems) or if the database lacks indexes for the views the UI relies on most heavily.
