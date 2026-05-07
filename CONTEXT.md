
## ] Tổng quan Bài tập lớn

Xây dựng hệ thống đấu giá trực tuyến (Online Auction System) theo kiến trúc
Client–Server, sử dụng Java, JavaFX và mô hình MVC.

### Các chức năng bắt buộc

1. Quản lý người dùng (Bidder / Seller / Admin)
2. Quản lý sản phẩm đấu giá (CRUD)
3. Tham gia đấu giá (đặt giá, kiểm tra hợp lệ, cập nhật realtime)
4. Kết thúc phiên đấu giá (tự động đóng, xác định người thắng)
5. Xử lý lỗi & ngoại lệ
6. Giao diện GUI (JavaFX)
7. Thiết kế OOP (kế thừa, đa hình, trừu tượng, đóng gói)
8. Design Patterns (Singleton, Factory, Observer)
9. Kiến trúc Client–Server + MVC
10. Xử lý đấu giá đồng thời (concurrency)
11. Unit Test (JUnit), CI/CD (GitHub Actions)

### Chức năng nâng cao (tuỳ chọn, tối đa +1.5đ)

- Auto-Bidding (đấu giá tự động)
- Anti-sniping (gia hạn phiên đấu giá)
- Bid History Visualization (biểu đồ giá realtime)


## ] Nội dung giảng dạy & Nội dung tự học

```
 Phân biệt nội dung trên lớp và tự học
```
```
Khoá học có 10 bài giảng (tuần 1–10). Từ tuần 11 trở đi không còn bài giảng.
Các nội dung sau không được dạy trên lớp mà sinh viên phải tự học:
```
- Lập trình mạng (Socket, Client–Server)
- Giao diện người dùng (JavaFX, SceneBuilder, MVC)
- Lưu trữ dữ liệu (Serialization, File I/O)
⇒ Bắt đầu tự học các nội dung này ngay từ bây giờ

## ] Lộ trình thực hiện theo tuần

```
 Tuần 6 – Khởi động, Thiết kế OOP & Bắt đầu JavaFX
```
```
Bài giảng tuần này: Mẫu thiết kế và nguyên lý thiết kế.
Công việc cụ thể:
```
1. Đọc kỹ đề bài, thảo luận nhóm về scope và phân công.
2. Tạo GitHub repository, thiết lập nhánh main và dev.
3. Thiết kế sơ đồ lớp (class diagram):
    - User (abstract) → Bidder, Seller, Admin
    - Item (abstract) → Electronics, Art, Vehicle
    - Auction, Bid
4. Code các lớp cơ bản, áp dụng OOP (encapsulation, inheritance,
    polymorphism).
5. Triển khai Singleton (AuctionManager) và Factory Method (tạo Item).
6. [TỰ HỌC] Cài đặt JavaFX + SceneBuilder, chạy thử ứng dụng Hello World.

```
_ Tài liệu học
```
-  Design Patterns: https://www.youtube.com/watch?v=
    mE3qTp1TEbg&list=PLlsmxlJgn1HJpa28yHzkBmUY-Ty71ZUGc
-  Refactoring Guru: https://refactoring.guru/design-patterns
JavaFX – Học theo thứ tự sau:
1. Cài đặt JavaFX: https://openjfx.io/openjfx-docs/
2. Video setup IntelliJ: https://www.youtube.com/watch?v=
Ope4icw6bVk
3. Playlist cơ bản (các component): https://www.youtube.com/watch?
v=_7OM-cMYWbQ&list=PLZPZq0r_RZOM-8vJA3NQFZB7JroDcMwev
4. Setup SceneBuilder + IntelliJ: https://www.youtube.com/watch?v=
IZCwawKILsk
5. Controller trong SceneBuilder: https://www.youtube.com/watch?v=
0wQ68q7PD9w&list=PLfu_Bpi_zcDNYL6171Op3S1ABtuyFV7Nr&index=
16


 Tuần 7 – Đa luồng, Observer & Phát triển GUI

Bài giảng tuần này: Lập trình đa luồng và song song.
Công việc cụ thể:

1. Triển khai Observer Pattern để notify khi có bid mới.
2. Code logic nghiệp vụ: tạo phiên đấu giá, đặt giá, kiểm tra hợp lệ.
3. Viết logic chuyển trạng thái: OPEN → RUNNING → FINISHED →
    PAID/CANCELED.
4. Xử lý đấu giá đồng thời (concurrent bidding) – tránh lost update, race
    condition.
5. Sử dụng synchronized, ReentrantLock cho các thao tác quan trọng.
6. [TỰ HỌC] Tiếp tục học JavaFX: xây dựng các màn hình cơ bản (Login,
    Danh sách).

```
_ Tài liệu học
```
-  Race Conditions: https://www.youtube.com/watch?v=
    RMR75VzYoos

 Tuần 8 – Kiểm thử, Ngoại lệ & GUI nâng cao

Bài giảng tuần này: Kiểm thử và Tái cấu trúc mã nguồn.
Công việc cụ thể:

1. Tạo custom exceptions: InvalidBidException, AuctionClosedException,
    AuthenticationException.
2. Xử lý ngoại lệ cho: đặt giá thấp hơn hiện tại, đấu giá khi phiên đóng, lỗi dữ
    liệu.
3. Viết unit test (JUnit) cho logic đấu giá: đặt giá hợp lệ/không hợp lệ, kết thúc
    phiên.
4. Refactor code: loại bỏ code smells, áp dụng SOLID.
5. [TỰ HỌC] Hoàn thiện GUI JavaFX – áp dụng MVC, tách logic khỏi
    Controller, dùng FXML.

```
_ Tài liệu học
```
- [ JUnit 5 User Guide: https://docs.junit.org/5.5.0/
    user-guide/
MVC trong JavaFX:
- § Repo mẫu MVC: https://github.com/ZacharyDavidSaunders/
InventoryManagementSystem
-  Tài liệu MVC: https://www.pragmaticcoding.ca/javafx/MVC_
In_JavaFX
-  Video MVC: https://www.youtube.com/watch?v=Mu3VNzwpFII


 Tuần 9 – Tích hợp, CI/CD & Lập trình mạng

Bài giảng tuần này: Tích hợp và triển khai.
Công việc cụ thể:

1. Cấu hình Maven: quản lý dependencies, build tự động.
2. Tích hợp Checkstyle vào Maven để enforce coding convention.
3. Thiết lập GitHub Actions: tự động build + chạy test khi push.
4. [TỰ HỌC] Bắt đầu triển khai Client–Server bằng Java Socket.
5. [TỰ HỌC] Triển khai Serialization để lưu/tải dữ liệu.

```
_ Tài liệu học
```
-  CI/CD: https://www.youtube.com/watch?v=UTb3nNbH7M
-  Checkstyle + Maven: https://medium.com/@sruthiganesh/
    integrating-checkstyle-in-java-projects-with-maven-b1ac2cafd
-  Serialization: https://www.youtube.com/watch?v=DfbFTVNfkeI
-  Serialization: https://www.geeksforgeeks.org/java/
    serialization-and-deserialization-in-java/
Lập trình mạng – Học theo thứ tự:
1. Cơ bản Socket: https://www.youtube.com/watch?v=plh_cIEQ1Jo
2. Tài liệu Baeldung: https://www.baeldung.com/
a-guide-to-java-sockets
3. Gửi Serialized Objects: https://www.youtube.com/watch?v=
1up-oHjCcis
4. App nhắn tin realtime (JavaFX + Socket): https://www.youtube.
com/watch?v=_1nqY-DKP9A

 Tuần 10 – Hướng dẫn tự học nâng cao & Ôn tập (Buổi giảng cuối)

Bài giảng tuần này: Hướng dẫn tự học các nội dung nâng cao và Ôn tập.
Công việc cụ thể:

1. [TỰ HỌC] Hoàn thiện kiến trúc Client–Server: Server xử lý nhiều client
    đồng thời.
2. [TỰ HỌC] Tích hợp Observer Pattern qua Socket để realtime update.
3. Hoàn thiện các màn hình JavaFX chính:
    - Đăng nhập / Đăng ký
    - Danh sách phiên đấu giá
    - Chi tiết sản phẩm
    - Màn hình đấu giá trực tiếp (realtime bidding)
    - Quản lý sản phẩm (Seller)
4. Bổ sung thêm unit test, đạt code coverage ≥ 60%.

 Tuần 11–12 – Tích hợp toàn bộ hệ thống

Không còn bài giảng – tập trung hoàn toàn vào dự án.
Công việc cụ thể:


1. Tích hợp toàn bộ: GUI ↔ Logic ↔ Network ↔ Data.
2. Kiểm thử end-to-end: chạy Server+ nhiều Client đồng thời.
3. Fix bugs, xử lý edge cases.
4. Hoàn thiện Serialization / lưu trữ dữ liệu.
5. Đảm bảo CI/CD chạy xanh trên GitHub.

```
 Tuần 13–14 – Hoàn thiện & Chức năng nâng cao
```
```
Công việc cụ thể:
```
1. Polish giao diện, cải thiện UX.
2. Kiểm thử kỹ lưỡng toàn bộ hệ thống.
3. (Nếu kịp) Triển khai chức năng nâng cao:
    - Auto-Bidding (maxBid, increment, PriorityQueue)
    - Anti-sniping (gia hạn khi có bid cuối)
    - Biểu đồ giá realtime (LineChart JavaFX)
4. Viết README.md đầy đủ trên GitHub (hướng dẫn cài đặt, chạy).

```
_ Tài liệu học
```
-  Realtime LineChart: https://www.youtube.com/watch?v=
    HWfZPiPu1sI

```
 Tuần 15 – Trình bày & Chấm điểm
```
```
Công việc:
```
1. Chuẩn bị slide trình bày (kiến trúc, demo, đóng góp).
2. Mỗi thành viên phải giải thích được mọi phần code.
3. Demo trực tiếp hệ thống (Server + nhiều Client).
4. Phân chia điểm theo đóng góp thực tế.

## ] Bảng tổng hợp lộ trình

```
Tuần Bài giảng trên lớp Việc cần làm cho BTL Tự học
6 Mẫu thiết kế &
Nguyên lý thiết kế
```
```
Khởi tạo dự án, thiết kế
OOP, Singleton, Factory
```
```
JavaFX +
SceneBuilder
7 Đa luồng & Song
song
```
```
Observer, logic đấu giá,
concurrency
```
```
JavaFX: màn hình cơ
bản
8 Kiểm thử & Tái cấu
trúc
```
```
Custom exceptions, JUnit,
refactor SOLID
```
```
MVC trong JavaFX
```
```
9 Tích hợp & Triển
khai
```
```
Maven, Checkstyle,
GitHub Actions
```
```
Socket, Serialization
```
```
10 Hướng dẫn tự học &
Ôn tập (buổi cuối)
```
```
Client–Server, hoàn thiện
GUI
```
```
Networking, lưu trữ
```
```
11–12 Không còn bài giảng Tích hợp toàn bộ, e2e
testing, fix bugs
```
```
Tổng hợp
```

```
13–14 Không còn bài giảng Polish UI, chức năng nâng
cao
```
```
Tổng hợp
```
```
15 Trình bày Demo & Chấm điểm –
```
## ] Dự án tham khảo trên GitHub

1. Auction System (Multi-client, JavaFX):
    https://github.com/nlintas/Auction-System-in-Java
2. Socket Auction (Kotlin + JavaFX):
    https://github.com/gangulwar/socket-programming-auction-system
3. Auction + MySQL Database:
    https://github.com/Prasanna-icefire/AuctionSystem
4. Auction + Google Gson + Azure:
    https://github.com/AqibMughal1/Auction-System-JavaFX

## ] Thang điểm tóm tắt

```
Nội dung Điểm Mức
Thiết kế lớp và cây kế thừa 0.5 Bắt buộc
Áp dụng OOP (Encapsulation, Inheritance,
Polymorphism, Abstraction)
```
```
1.0 Bắt buộc
```
```
Design Patterns phù hợp 1.0 Bắt buộc
Quản lý người dùng, sản phẩm 1.0 Bắt buộc
Chức năng đấu giá 1.0 Bắt buộc
Xử lý lỗi & ngoại lệ 1.0 Bắt buộc
Xử lý đấu giá đồng thời (concurrency) 1.0 Bắt buộc
Realtime update (Observer/Socket) 0.5 Bắt buộc
Kiến trúc Client–Server 0.5 Bắt buộc
MVC (JavaFX + FXML, Controller-Model-
DAO)
```
```
0.5 Bắt buộc
```
```
Maven/Gradle, coding convention 0.5 Bắt buộc
Unit Test (JUnit) 0.5 Bắt buộc
CI/CD (GitHub Actions) 0.5 Bắt buộc
Auto-Bidding 0.5 Tuỳ chọn
Anti-sniping 0.5 Tuỳ chọn
Bid History Visualization 0.5 Tuỳ chọn
Tổng 10+
```

## ] Lời khuyên

```
 Mẹo
```
1. Bắt đầu học JavaFX ngay từ tuần này – đây là nội dung tự học, không
    chờ bài giảng.
2. Lập trình mạng cũng là tự học – bắt đầu đọc tài liệu Socket từ tuần 9
    để kịp tích hợp.
3. Commit thường xuyên: mỗi ngày hoặc mỗi tính năng hoàn thành. Dùng
    Conventional Commits.
4. Mỗi người phải code: không để 1 người làm hết. Phân công theo module
    (Server, Client GUI, Logic, Test).
5. Chạy song song: trong khi 1–2 người làm backend logic, người khác học và
    làm GUI.
6. Đọc code tham khảo: clone các repo mẫu, chạy thử, đọc hiểu cấu trúc
    trước khi tự viết.
7. Review code lẫn nhau: dùng Pull Request trên GitHub để review trước
    khi merge.
. Lưu ý: Nội dung tự học rất quan trọng

```
Ba nội dung JavaFX, Lập trình mạng (Socket) và Lưu trữ dữ liệu
(Serialization) đều không được dạy trên lớp nhưng là yêu cầu bắt buộc của
bài tập lớn. Hãy chủ động tự học ngay từ bây giờ
```
```
Chúc các bạn hoàn thành tốt Bài tập lớn!
```

