# Tính điểm

Web tính điểm cho nhóm người chơi (bài, cờ…) theo luật **zero-sum**: tổng điểm mỗi ván luôn bằng 0. Chạy ngay trên máy tính của bạn; điện thoại cùng Wi-Fi mở được và thấy điểm cập nhật theo thời gian thực.

## Tính năng

- Tạo bàn với 2–12 người chơi, đặt tên từng người.
- Mỗi ván chỉ cần nhập điểm của N−1 người, ô còn lại tự được tính = −(tổng các ô kia). Ô bỏ trống mặc định là người cuối; bỏ trống ô nào thì ô đó được tính.
- Bảng điểm: hàng **tổng điểm từng người** cố định ở trên cùng, bên dưới là điểm từng ván từ ván 1 trở đi.
- Sửa, xóa ván đã nhập; đổi tên người chơi.
- Xóa cả bàn bằng nút **Xóa** ở danh sách bàn (có hỏi xác nhận, xóa là mất hẳn). Máy nào đang xem bàn đó sẽ thấy "Bàn này đã bị xóa".
- Realtime: nhiều thiết bị cùng mở một bàn, ai nhập thì mọi người thấy ngay (SSE). Mất kết nối sẽ tự nối lại.
- Dữ liệu lưu vào file, tắt máy bật lại vẫn còn. Mỗi bàn có link riêng (`/g/<id>`).
- Giao diện tiếng Việt, dùng được trên điện thoại.

Không có đăng nhập, không đưa lên internet — đây là công cụ chạy cục bộ.

## Công nghệ

| Phần | Công nghệ |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1 (Web MVC), Maven Wrapper |
| Giao diện | React 19, TypeScript, Vite, React Router |
| Realtime | Server-Sent Events (`SseEmitter`) |
| Lưu trữ | Một file JSON (`data/games.json`) |
| Kiểm thử | JUnit + Spring Test (backend), Vitest + Testing Library (frontend) |

Giao diện được build vào thư mục `static` của Spring Boot, nên **chạy thật chỉ cần một file jar, một tiến trình, một cổng**.

## Yêu cầu

- **JDK 21**
- **Node.js** (dự án dùng Node 24) và npm
- Internet ở lần build đầu (tải Maven, Spring Boot, gói npm); sau đó chạy offline được.

Không cần cài Maven — đã có Maven Wrapper (`mvnw`).

## Chạy ứng dụng

Trên Windows, bấm đúp **`start.bat`**:

1. Nếu chưa có `backend\target\tinhdiem.jar` thì tự build (gọi `build.bat`).
2. Tự mở trình duyệt tại <http://localhost:8080>.
3. Chạy máy chủ. Đóng cửa sổ để tắt ứng dụng.

Muốn build lại (sau khi sửa code), chạy **`build.bat`**: nó build giao diện React rồi đóng gói Spring Boot thành `backend\target\tinhdiem.jar`.

> Nếu đang có `tinhdiem.jar` cũ, `start.bat` sẽ không build lại. Hãy chạy `build.bat` sau mỗi lần sửa code.

### Mở trên điện thoại

Khi khởi động, console in ra địa chỉ LAN, ví dụ `http://192.168.1.10:8080`. Mở địa chỉ đó trên điện thoại cùng Wi-Fi.

Nếu điện thoại không vào được: lần đầu chạy Windows có thể hiện hộp thoại tường lửa — chọn cho phép Java truy cập **mạng riêng**.

### Tùy chỉnh

| Muốn đổi | Cách làm |
| --- | --- |
| Cổng (mặc định `8080`) | Đặt biến môi trường `PORT`, ví dụ `set PORT=9000` rồi chạy `start.bat` |
| Vị trí file dữ liệu (mặc định `data/games.json`) | `java -jar backend\target\tinhdiem.jar --tinhdiem.data-file=D:\duongdan\games.json` |

## Dữ liệu

- Lưu ở `data/games.json` (tính từ thư mục chạy `start.bat`). Sao lưu bằng cách chép file này.
- Mỗi lần thay đổi, ứng dụng ghi file tạm rồi đổi tên nguyên tử, nên tắt đột ngột giữa lúc ghi không làm hỏng dữ liệu.
- Nếu file bị hỏng, ứng dụng **từ chối khởi động** và báo rõ đường dẫn file, **không tự ghi đè**. Hãy sửa hoặc đổi tên file đó rồi chạy lại.

## Luật và giới hạn

Server kiểm tra lại mọi thứ trước khi lưu (không tin giao diện):

- Điểm là **số nguyên** (không thập phân), trong khoảng −999.999.999 đến 999.999.999.
- **Tổng điểm mỗi ván phải bằng 0**, nếu không sẽ bị từ chối.
- Số người chơi: 2–12. Tên người chơi: không rỗng, tối đa 30 ký tự. Tên bàn: tối đa 40 ký tự (để trống sẽ tự đặt "Bàn N").
- Đổi tên người chơi thì được, đổi số người chơi của bàn đã tạo thì không.

Giao diện (`frontend/src/rules.ts`) có bản luật riêng để tự điền và báo lỗi tức thì; hai bản dùng chung một bộ ca kiểm tra.

## API

Tất cả nằm dưới `/api/games`. Lỗi trả về dạng `{"error": "thông báo tiếng Việt"}` với mã 400 (dữ liệu sai), 404 (không có bàn/ván) hoặc 500 (không ghi được file).

| Method | Đường dẫn | Mô tả |
| --- | --- | --- |
| `GET` | `/api/games` | Danh sách bàn (mới cập nhật lên đầu) |
| `POST` | `/api/games` | Tạo bàn: `{"name": "...", "players": ["A", "B", ...]}` |
| `GET` | `/api/games/{id}` | Chi tiết một bàn |
| `DELETE` | `/api/games/{id}` | Xóa cả bàn (trả `204`) |
| `POST` | `/api/games/{id}/rounds` | Thêm ván: `{"scores": [10, -4, -6]}` |
| `PUT` | `/api/games/{id}/rounds/{roundId}` | Sửa ván |
| `DELETE` | `/api/games/{id}/rounds/{roundId}` | Xóa ván |
| `PUT` | `/api/games/{id}/players` | Đổi tên người chơi: `{"players": [...]}` |
| `GET` | `/api/games/{id}/events` | Luồng SSE: gửi toàn bộ trạng thái bàn (`game`) mỗi khi có thay đổi; khi bàn bị xóa gửi `deleted` rồi đóng luồng |

Mọi thao tác ghi được khóa tuần tự trong `GameStore`: ghi file xong mới đổi trạng thái trong bộ nhớ và phát cho các client, nên hai thiết bị nhập cùng lúc không làm hỏng dữ liệu.

## Phát triển

Chạy hai tiến trình để có hot reload giao diện:

```bash
# Terminal 1 — backend (cổng 8080)
cd backend
.\mvnw.cmd spring-boot:run

# Terminal 2 — giao diện (cổng 5173, tự proxy /api sang 8080)
cd frontend
npm install
npm run dev
```

Mở <http://localhost:5173>.

Chạy kiểm thử:

```bash
# Backend
cd backend
.\mvnw.cmd test

# Frontend
cd frontend
npm test        # Vitest
npm run lint    # Oxlint
```

## Cấu trúc thư mục

```
appTinhDiem/
├── start.bat            # Chạy ứng dụng (tự build nếu chưa có jar)
├── build.bat            # Build giao diện + đóng gói jar
├── backend/             # Spring Boot
│   └── src/main/java/com/tinhdiem/
│       ├── model/       #   Game, Round, GameSummary
│       ├── rules/       #   Rules: luật tính điểm và kiểm tra đầu vào
│       ├── store/       #   GameStore: giữ dữ liệu trong bộ nhớ, ghi file JSON
│       └── web/         #   GameController (REST), SseHub (realtime), SpaController, ...
├── frontend/            # React + TypeScript (Vite)
│   └── src/
│       ├── pages/       #   HomePage (danh sách/tạo bàn), GamePage (bàn chơi)
│       ├── components/  #   ScoreTable
│       └── rules.ts     #   Luật phía giao diện
├── data/games.json      # Dữ liệu (tạo tự động)
└── docs/superpowers/    # Tài liệu thiết kế và kế hoạch thực hiện
```

`backend/src/main/resources/static/` là kết quả build của giao diện, được tạo lại mỗi lần `npm run build`; đừng sửa tay ở đó.

Chi tiết quyết định thiết kế: [docs/superpowers/specs/2026-11-17-score-tracker-design.md](docs/superpowers/specs/2026-11-17-score-tracker-design.md).
