# 🔨 Hệ Thống Đấu Giá Trực Tuyến (Online Auction System)

Một ứng dụng đấu giá trực tuyến theo thời gian thực (real-time) với đầy đủ các tính năng dành cho Quản trị viên (Admin), Người bán (Seller) và Người đấu giá (Bidder). Dự án được xây dựng với kiến trúc Client-Server, giao diện đồ hoạ hiện đại bằng JavaFX, kết nối cơ sở dữ liệu PostgreSQL và tích hợp công nghệ WebSocket để cập nhật trạng thái phiên đấu giá tức thời.

---

## 👥 Danh sách thành viên nhóm

*(Danh sách sẽ được cập nhật sau)*

1. Nguyễn Tuấn Hưng - 25021812
2. Khổng Quang Minh - 25021871
3. Nguyễn Hữu Nguyên - 25021918
4. Lê Anh Trí - 25022027

---

## 🌟 Tính năng nổi bật (Core Features)

### 1. Phân quyền đa người dùng (Role-based Access)
- **Bidder (Người mua):** Nạp tiền, tham gia đấu giá, thiết lập đấu giá tự động (Auto-bid), theo dõi các phiên đang diễn ra, xem lịch sử giao dịch và số dư khả dụng.
- **Seller (Người bán):** Đăng tải tài sản đấu giá (với hình ảnh, mô tả, giá khởi điểm), theo dõi tiến độ các phiên đấu giá của mình, nhận tiền khi phiên đấu giá kết thúc thành công.
- **Admin (Quản trị viên):** Duyệt các phiên đấu giá mới (Pending -> Open), bắt buộc bắt đầu (Force Start) hoặc kết thúc/hủy phiên đấu giá khi cần thiết, quản lý toàn bộ người dùng và theo dõi thống kê hệ thống.

### 2. Đấu giá thời gian thực (Real-time Bidding qua WebSocket)
- Cập nhật giá thầu cao nhất, số lượng lượt đặt giá và trạng thái phiên đấu giá ngay lập tức đến tất cả các client đang kết nối mà không cần tải lại trang.
- Thông báo (push notifications) lập tức khi có người đặt giá cao hơn.

### 3. Đấu giá tự động (Auto-Bidding)
- Cho phép Bidder thiết lập mức giá tối đa (Max Bid) và bước giá (Increment).
- Hệ thống tự động tranh giá (Bidding War) thay mặt người dùng mỗi khi có người khác đặt giá cao hơn, cho đến khi đạt ngưỡng Max Bid.

### 4. Cơ chế Đóng băng Số dư (Fund Freezing Mechanism)
- Khi Bidder đặt giá, số tiền tương ứng sẽ bị "đóng băng" (Frozen Balance) trong tài khoản và trừ đi khỏi "Số dư khả dụng" (Available Balance).
- Nếu bị người khác trả giá cao hơn (Outbid), hệ thống lập tức "rã đông" (Unfreeze) số tiền đó, trả lại số dư khả dụng cho Bidder.
- Khi phiên đấu giá kết thúc, tiền mới chính thức bị trừ khỏi tài khoản người chiến thắng và chuyển cho Seller.

### 5. Giao diện (UI/UX) Hiện đại và Sống động
- Được phát triển bằng **JavaFX** kết hợp CSS mang phong cách Glassmorphism.
- Hỗ trợ chế độ Sáng/Tối (Light/Dark mode) với các bảng màu được tối ưu (curated color palettes).
- Hiệu ứng hoạt ảnh (Animations) mượt mà như Ripple Effect cho nút bấm, Wave Background cho trang đăng nhập, và các hiệu ứng hover chuyển cảnh.
- Xử lý bất đồng bộ (Asynchronous Background Tasks) không làm đơ giao diện khi gọi API hoặc tải dữ liệu.

---

## 🏗️ Kiến trúc & Công nghệ sử dụng (Tech Stack)

### Ngôn ngữ & Framework
- **Java 17+:** Ngôn ngữ lập trình chính.
- **JavaFX:** Xây dựng giao diện người dùng (GUI) đa nền tảng.
- **Maven:** Quản lý thư viện và quá trình build dự án.

### Cơ sở dữ liệu (Database)
- **PostgreSQL:** Lưu trữ dữ liệu chính (có thể triển khai trên Render hoặc cloud khác).
- **HikariCP:** Connection Pooling để tối ưu hóa hiệu suất truy vấn cơ sở dữ liệu với độ trễ thấp.
- **JDBC:** Giao tiếp trực tiếp với cơ sở dữ liệu qua các SQL Queries tối ưu.

### Mạng & Giao tiếp (Networking/Security)
- **Java-WebSocket:** Sử dụng chuẩn WebSocket (ws/wss) cho kết nối hai chiều thời gian thực giữa client và server.
- **JSON / Gson:** Serialize và Deserialize dữ liệu dạng JSON cho các API và Message qua WebSocket.
- **Argon2id:** Mã hóa mật khẩu bảo mật cao chống lại các cuộc tấn công Brute-force và Rainbow table.

### Design Patterns Áp dụng
- **MVC (Model - View - Controller):** Tách biệt rõ ràng dữ liệu (Model), giao diện (View - FXML) và logic xử lý (Controller).
- **Singleton Pattern:** Quản lý các Service, SessionManager, NavigationManager, Connection Pool để đảm bảo chỉ có duy nhất một instance hoạt động.
- **Factory Method Pattern:** Sử dụng để tạo ra các loại tài sản khác nhau một cách linh hoạt (`Art`, `Electronics`, `Vehicle`).
- **Facade Pattern:** Cung cấp interface đơn giản (`AppFacade`) để tầng UI giao tiếp với các Service nghiệp vụ phức tạp ở dưới.
- **Observer Pattern:** Sử dụng gián tiếp qua cơ chế lắng nghe sự kiện của WebSocket và JavaFX Properties.

---

## 📂 Cấu trúc thư mục dự án (Project Structure)

```text
src/
├── main/
│   ├── java/com/auction/
│   │   ├── client/       # Lớp kết nối WebSocket Client
│   │   ├── controller/   # Điều khiển giao diện JavaFX (Xử lý sự kiện UI)
│   │   ├── exception/    # Quản lý các ngoại lệ (Custom Exceptions)
│   │   ├── factory/      # Abstract Factory cho tạo lập Item
│   │   ├── model/        # Các thực thể dữ liệu (User, Auction, Bid, Item)
│   │   ├── repository/   # Lớp truy cập dữ liệu (JDBC DAO)
│   │   ├── security/     # Dịch vụ mã hoá (Argon2)
│   │   ├── server/       # Khởi chạy ứng dụng Server và WebSocket Handler
│   │   ├── service/      # Business logic (Đấu giá, Đăng nhập, Auto-bid)
│   │   └── util/         # Công cụ hỗ trợ (TimeSync, DB Connection, Animation, Routing)
│   └── resources/com/auction/
│       ├── components/   # Các phần nhỏ giao diện FXML có thể tái sử dụng (Sidebar, Topbar)
│       ├── styles/       # CSS cho giao diện
│       └── *.fxml        # Các trang giao diện chính (Login, Dashboard, Admin, v.v.)
```

---

## 🛠 Hướng dẫn Cài đặt & Chạy dự án

### 1. Yêu cầu hệ thống
- JDK 17 hoặc mới hơn.
- Maven 3.6+.
- PostgreSQL 12+ (nếu muốn cài DB local, mặc định app đang nối tới DB Render qua biến môi trường hoặc connection string).

### 2. Biên dịch & Khởi chạy (Build & Run)
Mở Terminal tại thư mục gốc của dự án (nơi chứa file `pom.xml`):

```bash
# Xóa bản build cũ, tải thư viện và biên dịch lại code
mvn clean compile

# Chạy ứng dụng Client/Server
mvn exec:java -Dexec.mainClass="com.auction.Main"
```

*Lưu ý:* Ứng dụng tích hợp tự động đồng bộ (Seed) dữ liệu mẫu (các người dùng test như `admin`, `alice`, `bob`, `carol`, `dave` cùng các mặt hàng đấu giá) nếu Database đang trống.

---

## 🔮 Hướng phát triển trong tương lai
- Bổ sung Cổng thanh toán (Payment Gateway) mô phỏng thật.
- Báo cáo và xuất file (Export to PDF/Excel) cho Admin.
- Nâng cấp bảo mật bằng xác thực 2 bước (2FA) và JWT cho REST/WebSocket.

---
*Báo cáo môn học - Hệ thống đấu giá trực tuyến*
