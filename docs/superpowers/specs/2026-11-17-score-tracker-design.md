# Web tính điểm (zero-sum) — thiết kế ngắn

Ngày: 2026-11-17 · Trạng thái: đã duyệt; **cập nhật cùng ngày:** người dùng yêu cầu đổi công nghệ sang **Java (backend) + React (giao diện)**, thay cho server Node thuần.

## Mục đích
Công cụ tính điểm cho nhóm người chơi, mỗi ván tổng điểm luôn bằng 0. Người dùng nhập điểm N−1 người, ô còn lại tự được tính. Chạy trên máy tính của người dùng; các thiết bị khác trong cùng mạng Wi-Fi mở được và thấy thay đổi theo thời gian thực. Mở lại sau đó dữ liệu vẫn còn.

## Mục tiêu
- Nhập số người chơi (2–12) và tên từng người khi tạo bàn.
- Mỗi ván: bỏ trống một ô, ô đó tự điền = −(tổng các ô còn lại) theo thời gian thực. Ô bỏ trống mặc định là người cuối, người dùng bỏ trống ô nào thì ô đó được tính.
- Giao diện: hàng **tổng điểm từng người cố định ở trên cùng**; bên dưới là điểm từng ván theo thứ tự từ ván 1 trở đi; hàng nhập ván mới ở cuối.
- Sửa và xóa ván đã nhập. Đổi tên người chơi.
- Realtime: nhiều thiết bị cùng mở một bàn, ai nhập thì mọi người thấy ngay.
- Dữ liệu lưu bền trong file trên máy; tắt server bật lại vẫn còn.
- Trang chủ liệt kê các bàn đã tạo và cho tạo bàn mới. Mỗi bàn có link riêng.
- Giao diện tiếng Việt, dùng được trên điện thoại.

## Không làm (non-goals)
- Không đăng nhập, không phân quyền.
- Không đưa lên internet, không hosting.
- Không thống kê nâng cao, không xuất file.

## Điều không được xảy ra (anti-goals)
- Không lưu được ván có tổng khác 0.
- Mất dữ liệu khi server tắt giữa lúc ghi file.
- Hai thiết bị nhập cùng lúc làm hỏng dữ liệu.

## Ràng buộc
- Công nghệ do người dùng chỉ định: **Java** cho backend, **React** cho giao diện.
- Máy có JDK 21 và Node v24.19; **chưa có Maven/Gradle** (dùng Maven Wrapper). Lần build đầu cần internet để tải Maven, Spring Boot và các gói npm; sau đó chạy offline được.
- Windows: lần đầu chạy có thể hiện hộp thoại tường lửa, cần cho phép mạng riêng để điện thoại truy cập.

## Các cách đã cân nhắc
1. **Spring Boot (Java 21) + React (Vite, TypeScript)**, build thành **một file jar** (đã chọn): Spring Boot lo API, SSE và phục vụ luôn giao diện React đã build; chạy bằng `java -jar`. Một tiến trình, một cổng.
2. Java thuần (`com.sun.net.httpserver`) + React: không cần Spring nhưng JDK không có sẵn JSON và phải tự viết nhiều phần hạ tầng. Loại.
3. Hai tiến trình riêng (Spring :8080 + Vite :5173): chỉ dùng khi phát triển (Vite proxy `/api` sang Spring), không dùng khi chạy thật.
4. Firebase / Supabase / VPS và file HTML + `localStorage`: đã loại ở vòng trước (chạy local, cần realtime giữa thiết bị).

Người dùng chỉ chọn ngôn ngữ/thư viện (Java, React); phần còn lại (Spring Boot, Maven, Vite, TypeScript, một jar) là **quyết định của tôi**.

## Thiết kế
- **Chạy:** `start.bat` (chạy `java -jar` file đã build) hoặc `build.bat` để build lại. Cổng mặc định 8080 (đổi bằng `PORT`), lắng nghe mọi địa chỉ mạng; khi khởi động in ra địa chỉ trên máy và địa chỉ LAN để mở trên điện thoại.
- **Dữ liệu:** `data/games.json` (cạnh nơi chạy jar), ghi nguyên tử (ghi file tạm rồi `ATOMIC_MOVE`). Mỗi bàn gồm: id ngẫu nhiên, tên bàn, danh sách người chơi, danh sách ván (mỗi ván là mảng điểm theo thứ tự người chơi). File hỏng → ứng dụng từ chối khởi động, báo rõ, không ghi đè.
- **Realtime:** mỗi bàn có một luồng SSE (`SseEmitter`). Mọi thay đổi (thêm/sửa/xóa ván, đổi tên) gửi lên bằng HTTP REST, server ghi file rồi phát lại toàn bộ trạng thái bàn cho mọi client đang mở. Server là nguồn đúng duy nhất; mọi thao tác được khóa tuần tự nên hai thiết bị nhập cùng lúc không làm hỏng dữ liệu.
- **Kiểm tra:** server (Java) kiểm tra điểm là số nguyên và tổng mỗi ván bằng 0 trước khi lưu. Giao diện (TypeScript) có bản luật riêng để tự điền và báo lỗi tức thì; hai bản dùng chung một bộ ca kiểm tra giống nhau.
- **Giao diện:** React + TypeScript (Vite), hai trang: danh sách/tạo bàn và bàn chơi (`/g/:id`). Build ra thư mục `static` của Spring Boot. Nhập số âm phải dễ trên điện thoại.

## Để người xây quyết
- Cấu trúc file, đặt tên, kiểu dáng chi tiết, màu sắc.
- Cách xử lý khi mất kết nối SSE (tự nối lại).
- Điểm: số nguyên (quyết định của tôi), không thập phân.
