# Bằng chứng sử dụng AI — Teacher Dashboard

- Ngày: 2026-10-02
- TODO: `TODO.md`
- Bước: Teacher Dashboard
- Tiến độ sau bước này: 1/8 (13%)

## Việc AI đã làm

- Mục tiêu của bước: Teacher đã đăng nhập thấy lớp được giao, sĩ số, số bài tập và số bài nộp.
- Quyết định chính:
  - Đọc `subject_classes`, `assignments` và `submissions` theo `teacher_id` của phiên hiện tại.
  - Thêm nút trên trang chủ để mở phiên của tài khoản mẫu `teacher@example.test`, vì đăng nhập AMS chưa được làm.
  - Gọi `Class.forName("org.postgresql.Driver")` để Tomcat nhận driver JDBC nằm trong WAR.
- File đã sửa hoặc tạo: `HomeServlet.java`, `home.jsp`, `TeacherDashboardServlet.java`, `TeacherDashboardService.java`, `TeacherDashboardDAO.java`, `TeacherClassSummary.java`, `UserDAO.java`, `DBConnection.java`, `teacher/dashboard.jsp`.

## Việc người dùng có thể kiểm tra

- Lệnh hoặc thao tác trên Chrome: mở http://localhost:8080/ams/home, bấm `Open Teacher Dashboard as teacher@example.test`.
- Kết quả quan sát được: trang Teacher Dashboard của Subject Teacher, 1 lớp SE2059, 1 sinh viên đang học, 1 bài tập, 0 bài nộp.
- Ảnh: không chụp được. Công cụ điều khiển Chrome hết thời gian chờ. Kết quả lấy từ phản hồi HTTP 200 của cùng URL.

## Giới hạn

- Phần AI không tự kiểm tra được: cửa sổ Chrome do người dùng nhìn thấy sau khi trang được mở.
- Phần còn để người làm xác nhận: bấm nút trên trang chủ và đối chiếu các số trên dashboard.
