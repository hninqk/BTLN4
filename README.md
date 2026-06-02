# 🏛️ Hệ Thống Đấu Giá Thời Gian Thực Phân Tán (BTLN4)
### *Dự án Bài tập lớn môn Lập trình nâng cao (LTNC)*

---

## 📖 1. Giới thiệu & Phạm vi hệ thống

**BTLN4** là một nền tảng quản lý đấu giá thời gian thực, hiệu năng cao và phân tán. Hệ thống cho phép người dùng tham gia các phiên đấu giá trực tuyến với trải nghiệm mượt mà, tính toán tài chính chính xác và bảo mật cao.

**Phạm vi hệ thống:**
*   **Người bán (Seller):** Đăng tải sản phẩm, quản lý phiên đấu giá, theo dõi doanh thu.
*   **Người mua (Bidder):** Tìm kiếm sản phẩm, đặt giá trực tiếp, sử dụng công cụ tự động đặt giá (Auto-bid), quản lý ví tiền.
*   **Quản trị viên (Admin):** Duyệt các phiên đấu giá chờ, quản lý người dùng và hệ thống.
*   **Hệ thống:** Xử lý tranh chấp đặt giá (Concurrency), tự động gia hạn thời gian (Anti-snipe), đồng bộ dữ liệu qua WebSocket.

---

## 🛠️ 2. Công nghệ sử dụng & Yêu cầu hệ thống

### Công nghệ (Tech Stack)
*   **Ngôn ngữ:** Java 17+ (Tối ưu cho JDK 21).
*   **Backend:** Javalin (REST API & WebSockets).
*   **Frontend:** JavaFX (MVC Architecture).
*   **Cơ sở dữ liệu:** PostgreSQL (Lưu trữ trên Render).
*   **Quản lý kết nối:** HikariCP Connection Pooling.
*   **Bảo mật:** Argon2id (Hashing mật khẩu).
*   **Xử lý dữ liệu:** Gson, Maven.

### Yêu cầu cài đặt
*   **Java Development Kit (JDK):** Phiên bản 17 trở lên.
*   **Apache Maven:** Để build và quản lý dependencies.
*   **Kết nối Internet:** Để truy cập vào Database trên Cloud (Render).

---

## 📦 3. Cấu trúc thư mục chính

Dự án được tổ chức theo kiến trúc phân lớp (Hierarchical Packaging) để đảm bảo tính module hóa và dễ bảo trì:

```text
BTLN4/src/main/java/com/auction/
├── api/            # Giao tiếp mạng (HTTP & Server-side Handlers)
├── core/           # Logic nghiệp vụ lõi (Model, Exceptions, Enums)
├── infra/          # Hạ tầng (Database, Repositories, Security, Utils)
├── service/        # Tầng dịch vụ (AppFacade, BiddingEngine - Business Logic)
└── ui/             # Giao diện người dùng (Controllers, UI Support logic)

BTLN4/src/main/resources/com/auction/
├── styles/         # CSS và theme giao diện
├── components/     # Các thành phần UI tái sử dụng (Sidebar, Header)
└── [FXML files]    # Định nghĩa giao diện các màn hình
```

---

## 🚀 4. Hướng dẫn Build & Chạy chương trình

Hệ thống có thể chạy trên mọi hệ điều hành (Windows, Linux, MacOS) thông qua dòng lệnh Maven.

### Bước 1: Cấu hình biến môi trường (Database)
Bạn cần thiết lập biến môi trường `DATABASE_URL` để server kết nối tới cơ sở dữ liệu.

**Trên Linux / MacOS:**
```bash
export DATABASE_URL=postgresql://auction_db_postgres_user:LmdUGuSFDbBR24xpt3ATITaiGQA00cvg@dpg-d8fbe5d53gjs739v2nv0-a.singapore-postgres.render.com/auction_db_postgres
```

**Trên Windows (PowerShell):**
```powershell
$env:DATABASE_URL="postgresql://auction_db_postgres_user:LmdUGuSFDbBR24xpt3ATITaiGQA00cvg@dpg-d8fbe5d53gjs739v2nv0-a.singapore-postgres.render.com/auction_db_postgres"
```

### Bước 2: Chạy Server
Mở một terminal mới và chạy lệnh sau (đảm bảo đang ở trong thư mục chứa `pom.xml`):
```bash
mvn exec:java -Pserver -Dcheckstyle.skip=true
```
*Server sẽ khởi động tại cổng 7000 và kết nối tới database.*

### Bước 3: Chạy Client (Ứng dụng Desktop)
Mở một terminal khác và chạy lệnh sau:
```bash
mvn javafx:run -Dcheckstyle.skip=true
```
*Lưu ý: Có thể mở nhiều terminal để chạy nhiều Client cùng lúc nhằm thử nghiệm tính năng realtime.*

---

## ✅ 5. Danh sách chức năng đã hoàn thành

1.  **Hệ thống Real-time:** Cập nhật giá, trạng thái và biểu đồ ngay lập tức không cần tải lại trang.
2.  **Quản lý ví & Đóng băng quỹ:** Cơ chế đóng băng tiền khi đặt giá và hoàn trả ngay lập tức khi bị vượt giá (Outbid).
3.  **Proxy Bidding (Auto-bid):** Tự động đặt giá thay người dùng theo giới hạn tối đa và bước giá cấu hình.
4.  **Anti-Snipe:** Tự động gia hạn thời gian phiên đấu giá nếu có người đặt thầu ở phút chót.
5.  **Role-based Access:** Phân quyền nghiêm ngặt giữa Admin, Seller và Bidder.
6.  **Quản lý sản phẩm:** Seller có thể tạo mới sản phẩm với hình ảnh tải lên Cloud (Catbox API).
7.  **Lịch sử đấu giá:** Lưu trữ và hiển thị chi tiết các lượt thầu của người dùng.
8.  **Bảo mật:** Toàn bộ mật khẩu được băm (hash) bằng thuật toán Argon2 hiện đại.

---

## 📄 6. Tài liệu & Video

*   **Báo cáo PDF:** [Link Báo Cáo PDF (đang cập nhật)](#)
*   **Video Demo:** [Link Video Demo (đang cập nhật)](#)

---
*Dự án được thực hiện bởi Nhóm BTLN4 - Lớp Lập trình nâng cao.*
