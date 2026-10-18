# Cập nhật Iteration 2 — NamDH

## Cập nhật database hiện có

Chạy `database/migrations/003-student-email-verification.sql` trên database AMS trước khi chạy ứng dụng mới. Script bổ sung `users.email_verified` và bảng `student_email_verifications`, giữ nguyên dữ liệu hiện có. Student cũ được coi là đã xác nhận; Student tạo mới qua Google phải xác nhận email trước khi vào AMS.

Không chạy lại `schema.sql` hoặc `seed.sql` trên database đã có dữ liệu. Các migration Assignment khác trong repo vẫn cần được áp dụng theo phiên bản database của nhóm.

## Cấu hình cục bộ

Điền cấu hình database, Google OAuth và SMTP trong `src/main/resources/application.properties` theo file `.example`. File cấu hình thật được Git ignore. Không commit mật khẩu hoặc App Password.

`app.baseUrl` phải là địa chỉ người nhận email truy cập được. Khi chạy trên máy local có thể dùng `http://localhost:8080/ams`; khi triển khai dùng địa chỉ HTTPS của hệ thống. Cấu hình SMTP hiện sử dụng STARTTLS, mặc định cổng 587.

Email Student có liên kết dùng một lần, hết hạn sau 24 giờ; gửi lại cách nhau ít nhất 60 giây. Nếu SMTP lỗi, tài khoản vẫn chờ xác nhận và có thể gửi lại. Google vẫn là phương thức đăng nhập Student. Email tài khoản nội bộ dùng chức năng gửi mật khẩu ban đầu đã có.

## Chạy dự án

Yêu cầu Java 17, Maven, PostgreSQL đang chạy và Tomcat 10.1 đã cài. Đặt `CATALINA_HOME` nếu Tomcat không nằm trong `%USERPROFILE%\tools\apache-tomcat-10.1.60`.

```powershell
.\start.cmd
```

Lệnh chạy test, build vào `.local/run-build`, triển khai WAR rồi khởi động lại Tomcat và kiểm tra trang đăng nhập. Không reset database hoặc tự nạp seed. File `.local` không được push.

## Thay đổi chức năng

- User Detail có hai cột, radio trạng thái, tìm kiếm môn đủ điều kiện giảng dạy cho Teacher và danh sách tất cả môn cùng trạng thái phân công cho Expert. Quản lý phân công đi qua màn của môn, có cảnh báo thay đổi chưa lưu và đường quay lại tài khoản.
- Template hỗ trợ xem, tạo, sửa và đổi trạng thái. Primary Expert được tạo và đổi trạng thái; Supporting Expert chỉ sửa nội dung; Teacher chỉ xem mẫu hoạt động trong môn có lớp phụ trách.
- Điểm tối đa của Template là số nguyên 1–100; nội dung và trạng thái lưu cùng transaction. Mẫu thay đổi không ghi đè các Assignment đã tạo.
- Template List có bộ lọc, nút tạo, cột thao tác và phân trang 10 dòng/trang. Query và cập nhật database ở DAO; service giữ transaction, phân quyền và nghiệp vụ.
- JSP/JSPF được xuống dòng, căn lề; lỗi ứng dụng hiển thị thông báo AMS thay cho trang lỗi mặc định Tomcat.
