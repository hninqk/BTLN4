---
title: "\U0001F3DB️ Hệ Thống Đấu Giá Thời Gian Thực Phân Tán (BTLN4)"

---

# 🏛️ Hệ Thống Đấu Giá Thời Gian Thực Phân Tán (BTLN4)
### *Dự án Bài tập lớn môn Lập trình nâng cao (LTNC)*
---
## 1. Mô tả ngắn gọn bài toán và phạm vi hệ thống
**Bài toán:** Xây dựng một nền tảng quản lý đấu giá trực tuyến theo thời gian thực, hiệu năng cao. Hệ thống cho phép người dùng tham gia các phiên đấu giá với trải nghiệm mượt mà, đồng thời đảm bảo tính toán tài chính chính xác và có độ bảo mật cao.
**Phạm vi hệ thống:**
*   **Người bán (Seller):** Đăng tải sản phẩm, quản lý phiên đấu giá, theo dõi doanh thu của mình.
*   **Người mua (Bidder):** Tìm kiếm sản phẩm, đặt giá trực tiếp, sử dụng công cụ tự động đặt giá (Auto-bid), quản lý ví tiền.
*   **Quản trị viên (Admin):** Duyệt các phiên đấu giá chờ, quản lý toàn bộ người dùng và hệ thống.
*   **Hệ thống chung:** Xử lý tranh chấp đặt giá (Auto-Bidding), tự động gia hạn thời gian (Anti-sniping), đồng bộ dữ liệu theo thời gian thực qua WebSockets.
---
## 2. Công nghệ sử dụng, môi trường chạy và yêu cầu cài đặt
**Công nghệ sử dụng:**
*   **Ngôn ngữ:** Java 17+ (Tối ưu cho JDK 21).
*   **Backend:** Javalin (REST API & WebSockets).
*   **Frontend:** JavaFX (Mô hình kiến trúc MVC).
*   **Cơ sở dữ liệu:** PostgreSQL (Lưu trữ trên Render).
*   **Quản lý kết nối:** HikariCP Connection Pooling.
*   **Bảo mật:** Argon2id (Hashing mật khẩu).
*   **Quản lý dự án:** Maven.
  
**Môi trường chạy:**
*   **Hệ điều hành:** Đa nền tảng (Windows, macOS, Linux).
*   **Kết nối mạng:** Yêu cầu kết nối Internet liên tục để sử dụng Cloud Database và API upload ảnh.

**Yêu cầu cài đặt:**
*   **Java Development Kit (JDK):** Yêu cầu phiên bản 17 trở lên.
*   **Apache Maven:** Đã được cài đặt và thiết lập biến môi trường (Lệnh `mvn` hoạt động trên Terminal).
---
## 3. Cấu trúc thư mục hoặc các module chính
Dự án được tổ chức theo kiến trúc phân lớp (Hierarchical Packaging) để đảm bảo tính module hóa và dễ duy trì:
```text
BTLN4/src/main/java/com/auction/
├── client/         # Các lớp kết nối Client tới Server
├── server/         # Server-side Handlers (REST API, WebSockets)
├── core/           # Logic nghiệp vụ lõi (Model, Exceptions, Factory, Utils)
├── infra/          # Lớp hạ tầng (Database Connection, Repositories)
├── service/        # Tầng dịch vụ trung gian (Auction, User, Bidding Services)
└── ui/             # Giao diện ứng dụng JavaFX (Controllers, UI Utilities)
BTLN4/src/main/resources/com/auction/
├── styles/         # Các tệp CSS định dạng giao diện
├── components/     # Các thành phần UI tái sử dụng (Sidebar, Header)
└── [FXML files]    # Khai báo cấu trúc các màn hình (Login, Dashboard,...)
```
---
## 4. Câu lệnh dòng lệnh để chạy chương trình
Các câu lệnh sau được thực thi thông qua **Maven**, đảm bảo hoạt động tương đương trên mọi hệ điều hành (Windows, Linux, macOS).
*Lưu ý: Bạn phải mở Terminal (hoặc Command Prompt / PowerShell) và điều hướng vào thư mục chứa file `pom.xml` (Ví dụ: `BTLN4/BTLN4/`) trước khi chạy.*
**Câu lệnh build dự án (tùy chọn):**
```bash
mvn clean install -Dcheckstyle.skip=true
```
**Câu lệnh chạy Server (Backend):**
*(Lưu ý: Server hiện đã được deploy lên nền tảng Render. Việc chạy Server ở local là không bắt buộc).*
```bash
mvn exec:java -Pserver -Dcheckstyle.skip=true
```
**Câu lệnh chạy Client (Giao diện người dùng):**
```bash
mvn javafx:run -Dcheckstyle.skip=true
```
---
## 5. Hướng dẫn chạy Server/Client theo thứ tự cụ thể
Hệ thống hoạt động theo mô hình Client-Server. Do Server Backend **đã được deploy trực tuyến (lên nền tảng Render)**, bạn có thể lựa chọn 1 trong 2 cách chạy dưới đây:
### Cách 1: Chỉ chạy Client kết nối tới Cloud Server (Nhanh nhất)
Vì Server đã có sẵn trên Render, bạn chỉ cần khởi động Client:
1. Mở cửa sổ Terminal, chạy câu lệnh chạy Client (như ở **mục 4**). 
2. Giao diện Desktop JavaFX sẽ hiện ra và tự động kết nối với Server trên mạng.
### Cách 2: Chạy cả Server và Client ở Local (Dành cho việc dev)
Nếu bạn muốn chạy hoàn toàn trên máy tính cá nhân, bạn **bắt buộc** tuân thủ đúng trình tự sau:

1.  **Bước 1: Cấu hình biến môi trường Database (Nếu dùng Cloud PostgreSQL)**
    Nếu hệ thống yêu cầu kết nối tới PostgreSQL, bạn cần set biến `DATABASE_URL` trong Terminal.
    *   **Linux/macOS:** `export DATABASE_URL=postgresql://...`
    *   **Windows (PowerShell):** `$env:DATABASE_URL="postgresql://..."`
    *(Ghi chú: Nếu hệ thống đang cấu hình dùng file SQLite cục bộ thì có thể bỏ qua bước này).*
2.  **Bước 2: Khởi động Server**
    Mở một cửa sổ Terminal, chạy câu lệnh chạy Server (ở mục 4). Chờ đến khi dòng log báo hiệu Server đã khởi động thành công (Mặc định thường chạy ở cổng 7000). 
    *Lưu ý: Không được đóng cửa sổ Terminal này trong suốt quá trình chạy ứng dụng.*
3.  **Bước 3: Khởi động Client**
        Mở một cửa sổ Terminal **mới**, chạy câu lệnh chạy Client (ở mục 4).
    *(Mẹo: Bạn có thể mở thêm nhiều cửa sổ Terminal mới và chạy nhiều Client cùng lúc để đăng nhập nhiều tài khoản test tính năng realtime).*
---
## 6. Danh sách chức năng đã hoàn thành
1.  **Hệ thống Real-time:** Cập nhật giá thầu, trạng thái phiên đấu giá và biểu đồ dữ liệu lập tức trên tất cả Client mà không cần làm mới giao diện.
2.  **Quản lý ví & Đóng băng quỹ (Fund Freezing):** Tự động đóng băng khoản tiền của Bidder khi đặt giá, và hoàn trả lại số tiền đóng băng ngay khi có người khác trả giá cao hơn (Outbid).
3.  **Proxy Bidding (Auto-bid):** Tự động đặt giá thay người dùng theo giới hạn tối đa và bước giá quy định.
4.  **Anti-Snipe:** Tự động gia hạn thêm thời gian của phiên đấu giá nếu có hành vi đặt thầu ở những phút chót.
5.  **Role-based Access:** Phân quyền truy cập và điều hướng giao diện hoàn chỉnh giữa 3 role: Admin, Seller và Bidder.
6.  **Quản lý sản phẩm:** Seller có khả năng tạo phiên đấu giá mới với hình ảnh trực quan tải lên hệ thống qua Catbox API.
7.  **Lịch sử đấu giá chi tiết:** Ghi nhận lại và minh họa toàn bộ các lượt thầu của những người dùng tham gia phiên đấu giá.
8.  **Bảo mật cao cấp:** Toàn bộ mật khẩu của hệ thống được mã hóa (hashing) thông qua thuật toán Argon2 trước khi lưu vào cơ sở dữ liệu.
---
## 7. Link báo cáo PDF và video demo
*   **Link Báo cáo PDF:** [Xem PDF tại đây](https://drive.google.com/file/d/1bXqnF1x5wxit4_d2BMeziASmufik9veq/view?usp=sharing)
*   **Link Video Demo:** [Xem video demo tại đây](https://www.youtube.com/watch?v=GlpOLVRsXp8)
---
*Dự án được thực hiện bởi Nhóm BTLN4 - Lớp Lập trình nâng cao.*

**Thành viên nhóm:**
- Khổng Quang Minh
- Nguyễn Hữu Nguyên 
- Nguyễn Tuấn Hưng
- Lê Anh Trí
