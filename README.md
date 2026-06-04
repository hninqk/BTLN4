# 🏛️ Hệ Thống Đấu Giá Thời Gian Thực Theo Kiến Trúc Client - Server
### *Dự án Bài tập lớn môn Lập trình nâng cao*
---
## 1. Mô tả ngắn gọn bài toán và phạm vi hệ thống
**Bài toán:** Xây dựng một nền tảng quản lý đấu giá trực tuyến theo thời gian thực, hiệu năng cao. Hệ thống cho phép người dùng tham gia các phiên đấu giá với trải nghiệm mượt mà, đồng thời đảm bảo tính toán tài chính chính xác và có độ bảo mật cao.

**Phạm vi hệ thống:**
*   **Người bán (Seller):** Đăng tải sản phẩm, quản lý phiên đấu giá, theo dõi doanh thu của mình.
*   **Người mua (Bidder):** Tìm kiếm sản phẩm, đặt giá trực tiếp, thiết lập cấu hình đấu giá tự động, quản lý ví tiền.
*   **Quản trị viên (Admin):** Duyệt các phiên đấu giá chờ, quản lý toàn bộ người dùng và hệ thống.
*   **Hệ thống chung:** Vận hành bộ máy đấu giá tự động, tự động gia hạn thời gian (Anti-sniping), đồng bộ dữ liệu theo thời gian thực qua WebSockets.
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
## 4. Hướng dẫn cài đặt và chạy chương trình

### Yêu cầu cài đặt
Cài đặt các phần mềm sau trước khi chạy dự án:
1. **Java Development Kit (JDK) 17**: [Tải JDK 17](https://adoptium.net/) (Cần set biến môi trường `JAVA_HOME`).
2. **Apache Maven**: [Tải Maven](https://maven.apache.org/install.html) (Cần set biến môi trường `PATH` cho `mvn`).
3. **Git**: [Tải Git](https://git-scm.com/) để clone mã nguồn.

### Cách mở Terminal (Command Line)
- **Windows**: Nhấn `Win + R`, gõ `cmd` hoặc `powershell` rồi nhấn Enter (hoặc mở Git Bash nếu đã cài Git).
- **macOS**: Nhấn `Cmd + Space`, gõ `Terminal` rồi nhấn Enter.
- **Linux**: Nhấn `Ctrl + Alt + T` để mở Terminal.

### Các bước thực hiện
**Bước 1: Clone mã nguồn về máy**
Mở Terminal và chạy lệnh sau:
```bash
git clone https://github.com/hninqk/BTLN4.git
cd BTLN4/BTLN4
```
*(Lưu ý: Đảm bảo bạn đang ở thư mục chứa file `pom.xml` trước khi chạy các lệnh Maven bên dưới).*

**Bước 2: Build dự án (tùy chọn nhưng khuyến khích)**
```bash
mvn clean install -Dcheckstyle.skip=true
```

**Bước 3: Chạy Client (Giao diện người dùng)**
*(Ứng dụng sẽ tự động kết nối với Server đã được deploy sẵn trên Render)*
```bash
mvn javafx:run -Dcheckstyle.skip=true
```

**Bước 4: Chạy Server tại Local (Không bắt buộc)**
*(Lưu ý: Chạy Server ở Local nếu muốn phát triển Backend. Để chạy được Server ở Local, bạn **bắt buộc** phải tạo file `.env` chứa biến `JDBC_DATABASE_URL` trỏ tới một PostgreSQL Database).*
```bash
mvn exec:java -Pserver -Dcheckstyle.skip=true
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
## 6. Danh sách chức năng nổi bật
1.  **Hệ thống Đồng bộ Real-time:** Cập nhật liên tục giá thầu, trạng thái đấu giá ngay lập tức trên tất cả các Client thông qua WebSockets mà không cần tải lại giao diện.
2.  **Quản lý Ví điện tử:** Tích hợp ví tiền nội bộ cho mỗi tài khoản, hỗ trợ nạp tiền, kiểm tra số dư và theo dõi chi tiết lịch sử giao dịch trực quan.
3.  **Đóng băng quỹ thông minh:** Tự động tạm giữ khoản tiền tương ứng của người mua khi đặt lệnh, và hoàn trả lập tức khi có người khác trả giá cao hơn, đảm bảo an toàn thanh toán cho hệ thống.
4.  **Đấu giá ủy quyền:** Hỗ trợ đấu giá rảnh tay bằng cách cho phép thiết lập mức giá tối đa; hệ thống sẽ tự động tính toán và đặt thầu từng bước cạnh tranh thay mặt người dùng.
5.  **Anti-Sniping:** Cơ chế tự động gia hạn thêm thời gian của phiên đấu giá nếu phát hiện có người dùng cố tình đặt thầu ở những giây cuối cùng.
6.  **Hệ thống Thông báo:** Gửi các thông báo real-time tới người dùng cho các sự kiện quan trọng như: bị vượt giá thầu, kết thúc phiên đấu giá, kết quả nạp tiền, v.v.
7.  **Biểu đồ Biến động giá:** Cung cấp biểu đồ đường tự động cập nhật theo thời gian thực để hiển thị trực quan diễn biến cạnh tranh và xu hướng giá của từng sản phẩm.
8.  **Thống kê Dashboard:** Cung cấp giao diện phân tích dữ liệu chuyên sâu cho Admin và Seller, sử dụng Heatmap để theo dõi mức độ tương tác theo khung giờ và Pie chart để phân bổ tỷ trọng doanh thu, phân loại trạng thái sản phẩm.
9.  **Lưu trữ & Tối ưu Cơ sở dữ liệu:** Toàn bộ dữ liệu được quản lý tập trung trên Cloud PostgreSQL kết hợp với cơ chế HikariCP Connection Pooling, mang lại khả năng truy xuất nhanh, chịu tải tốt và đảm bảo tính nhất quán (ACID).
10. **Tìm kiếm & Lọc thông minh:** Công cụ tìm kiếm mạnh mẽ cho phép phân loại và tra cứu các phiên đấu giá theo từ khóa, danh mục, hoặc trạng thái (đang diễn ra, sắp tới, đã kết thúc).
11. **Phân quyền & Kiểm duyệt:** Hệ thống được phân quyền chặt chẽ với 3 vai trò (Admin, Seller, Bidder).
12. **Quản lý Sản phẩm Đa phương tiện:** Seller dễ dàng tạo và chỉnh sửa các phiên đấu giá, hỗ trợ tính năng upload hình ảnh trực tiếp lên hệ thống lưu trữ qua Catbox API.
13. **Bảo mật & An toàn Dữ liệu:** Mật khẩu được băm thông qua thuật toán chuẩn bảo mật cao Argon2id trước khi lưu vào Database.
14. **Tải dữ liệu bất đồng bộ:** Toàn bộ dữ liệu lớn (danh sách phiên đấu giá, lịch sử giá, hình ảnh sản phẩm) được tải bất đồng bộ qua luồng nền bằng `BackgroundTaskRunner` giúp giao diện UI JavaFX luôn phản hồi mượt mà, không bị hiện tượng đơ/đóng băng ứng dụng.
15. **Bộ nhớ đệm & Tải ảnh lười:** Tối ưu hóa việc tải ảnh sản phẩm thông qua cơ chế bất đồng bộ và lưu trữ tạm thời giúp tăng tốc độ tải dữ liệu.
---
## 7. Link báo cáo PDF và video demo
*   **Link Báo cáo PDF:** [Xem PDF tại đây](https://drive.google.com/file/d/1bXqnF1x5wxit4_d2BMeziASmufik9veq/view?usp=sharing)
*   **Link Video Demo:** [Xem video demo tại đây](https://www.youtube.com/watch?v=GlpOLVRsXp8)
---
*Dự án được thực hiện bởi Nhóm 4 - Lớp UET.CS2043_14.*

**Thành viên nhóm:**
- Khổng Quang Minh
- Nguyễn Hữu Nguyên 
- Nguyễn Tuấn Hưng
- Lê Anh Trí
