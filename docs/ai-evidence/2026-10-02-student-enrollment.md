# Bằng chứng sử dụng AI — Student Enrollment Management

- Ngày: 2026-10-02
- TODO: `TODO.md`
- Bước: Student Enrollment Management
- Tiến độ sau bước này: 3/8 (38%)

## Việc AI đã làm

- Mục tiêu của bước: Teacher thêm sinh viên đã có tài khoản, đổi trạng thái ghi danh, và nhập CSV trên cùng màn.
- Quyết định chính:
  - Khóa dòng lớp trong một transaction trước khi kiểm tra sĩ số và ghi danh.
  - Dùng lại đăng ký môn theo kỳ nếu đã có, và từ chối khi sinh viên đang có một ghi danh active cho kỳ đó.
  - CSV chỉ ghi danh tài khoản Student đang active. Dòng trùng được tính skipped, dòng sai được tính rejected.
- File đã sửa hoặc tạo: `EnrollmentServlet.java`, `EnrollmentService.java`, `EnrollmentDAO.java`, `enrollments/detail.jsp`.

## Việc người dùng có thể kiểm tra

- Lệnh hoặc thao tác trên Chrome: từ roster bấm `Manage enrollment`. Tắt rồi bật lại Sample Student. Thêm `student@example.test`. Nhập CSV có email đó và một dòng không hợp lệ.
- Kết quả quan sát được: trạng thái đổi INACTIVE rồi trở lại ACTIVE. Thêm trùng báo đã có ghi danh active. Import báo `Added 0, skipped 1, rejected 1`.
- Ảnh: không chụp được vì công cụ điều khiển Chrome hết thời gian chờ. Các kết quả trên lấy từ phản hồi HTTP của `/ams/teacher/enrollments`.

## Giới hạn

- Phần AI không tự kiểm tra được: cửa sổ Chrome và một file CSV do người dùng tự chọn trên máy.
- Phần còn để người làm xác nhận: bấm Deactivate, Activate, rồi xem dòng thông báo import.
