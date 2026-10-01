# Hướng dẫn thiết lập PostgreSQL cho AMS

Tài liệu này hướng dẫn tạo database phát triển AMS trên Windows sau khi clone hoặc pull source code.

## 1. Yêu cầu

- PostgreSQL 17 hoặc 18 đã được cài và service đang chạy.
- Thành phần PostgreSQL command-line tools, bao gồm `psql.exe`.
- Biết mật khẩu của tài khoản quản trị PostgreSQL, mặc định là `postgres`.
- pgAdmin 4 là tùy chọn; script tự động không yêu cầu pgAdmin.

Database phát triển mặc định:

| Cấu hình | Giá trị |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `ams_db` |
| Application user | `ams_user` |

Mật khẩu của `ams_user` là mật khẩu local do từng thành viên tự đặt. Không gửi mật khẩu lên Git.

## 2. Cách nhanh: chạy script tự động

Mở PowerShell hoặc Command Prompt tại thư mục gốc của project rồi chạy:

```powershell
database\setup-local-db.cmd
```

Script sẽ lần lượt:

1. Tìm `psql.exe` trong `PATH` hoặc thư mục cài PostgreSQL thông dụng.
2. Yêu cầu nhập mật khẩu PostgreSQL admin.
3. Yêu cầu tạo mật khẩu local cho `ams_user`.
4. Tạo hoặc cập nhật role `ams_user`.
5. Tạo database `ams_db` với owner là `ams_user`.
6. Chạy `database/schema.sql`.
7. Chạy các file trong `database/migrations` theo thứ tự tên, rồi `database/seed.sql`.
8. Xác nhận database có 18 bảng nghiệp vụ AMS và 3 bảng audit triển khai.

Mật khẩu được nhập ở chế độ ẩn và không được ghi vào source code hoặc repository.

### PostgreSQL được cài ở đường dẫn khác

Nếu script không tìm thấy `psql.exe`, truyền đường dẫn cụ thể:

```powershell
database\setup-local-db.cmd -PsqlPath "C:\Program Files\PostgreSQL\18\bin\psql.exe"
```

### Database đã có dữ liệu

Script mặc định dừng nếu `ams_db` đã có bảng để tránh xóa dữ liệu ngoài ý muốn.

Chỉ khi chắc chắn có thể xóa toàn bộ database local, chạy:

```powershell
database\setup-local-db.cmd -Reset
```

Script sẽ yêu cầu nhập lại chính xác `ams_db` trước khi drop và tạo lại database.

## 3. Cấu hình kết nối cho ứng dụng

Sau khi script hoàn tất, cấu hình các biến môi trường:

```text
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ams_db
DB_USER=ams_user
DB_PASSWORD=<mật khẩu local đã nhập khi chạy script>
```

Thiết lập cho phiên PowerShell hiện tại:

```powershell
$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "ams_db"
$env:DB_USER = "ams_user"
$env:DB_PASSWORD = "<mật khẩu local>"
```

Hoặc copy file:

```text
src/main/resources/application.properties.example
```

thành:

```text
src/main/resources/application.properties
```

Sau đó điền mật khẩu local vào file mới. `application.properties` đã được `.gitignore` loại trừ và không được commit.

## 4. Kiểm tra ứng dụng

Chạy:

```powershell
mvn clean test
mvn clean package
```

Integration test sẽ kiểm tra:

- Mở kết nối JDBC.
- Chạy `SELECT 1`.
- Đọc dữ liệu seed từ bảng `users`.
- Đóng connection đúng cách.

Nếu build thành công, WAR được tạo tại:

```text
target/ams.war
```

## 5. Thiết lập thủ công bằng pgAdmin

Nếu không sử dụng script, mở Query Tool trên database `postgres` bằng tài khoản quản trị và chạy:

```sql
CREATE USER ams_user WITH PASSWORD '<mật khẩu local>';
CREATE DATABASE ams_db OWNER ams_user;
```

Nếu role đã tồn tại:

```sql
ALTER USER ams_user WITH PASSWORD '<mật khẩu local>';
```

Sau đó kết nối vào `ams_db` và chạy theo đúng thứ tự:

1. `database/schema.sql`
2. `database/migrations/001-namdh-iter2-audit.sql`
3. `database/migrations/002-responsibility-evidence.sql`
4. `database/seed.sql`

`schema.sql` tạo 18 bảng nghiệp vụ. Hai migration thêm 3 bảng audit và trigger lưu lịch sử phân công cho Iter2 của NamDH. Với database đã có dữ liệu, chỉ chạy hai migration theo thứ tự; không chạy lại schema/seed hoặc dùng `-Reset`. Các migration không tái tạo được lịch sử phân công trước lúc cài đặt.

## 6. Kiểm tra database trong pgAdmin

Trong Object Explorer, refresh theo đường dẫn:

```text
Servers
└── PostgreSQL
    └── Databases
        └── ams_db
            └── Schemas
                └── public
                    └── Tables
```

Có thể chạy câu SQL sau để kiểm tra số bảng:

```sql
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE';
```

Kết quả phải là:

```text
21
```

## 7. Lỗi thường gặp

### `Database cannot be empty`

Trong cửa sổ Create Database của pgAdmin, nhập `ams_db` vào ô **Database**. Git pull không tự tạo database PostgreSQL trên máy local.

### `psql.exe was not found`

Cài PostgreSQL command-line tools hoặc truyền `-PsqlPath` như hướng dẫn ở trên.

### `password authentication failed`

Kiểm tra lại mật khẩu PostgreSQL admin khi chạy script và mật khẩu `ams_user` trong cấu hình ứng dụng.

### `database "ams_db" does not exist`

Chạy `database\setup-local-db.cmd` hoặc tạo `ams_db` thủ công. Đồng thời kiểm tra `DB_NAME=ams_db`.

### Database đã chứa bảng

Script dừng để bảo vệ dữ liệu. Nếu đây chỉ là database local có thể xóa, chạy lại với `-Reset`.

### Maven test bị skip phần database

Đảm bảo đủ năm biến `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` trong cùng terminal dùng để chạy Maven.
