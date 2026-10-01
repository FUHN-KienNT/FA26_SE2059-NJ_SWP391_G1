# Bằng chứng sử dụng AI — Assigned Class List / Class Roster

- Ngày: 2026-10-02
- TODO: `TODO.md`
- Bước: Assigned Class List / Class Roster
- Tiến độ sau bước này: 2/8 (25%)

## Việc AI đã làm

- Mục tiêu của bước: Teacher xem các lớp được giao và, khi chọn một lớp, thấy sinh viên cùng lời mời.
- Quyết định chính:
  - Giữ một màn cho danh sách và roster. `classId` mở roster của lớp đó.
  - Chỉ lấy lớp có `teacher_id` trùng phiên hiện tại.
  - Lời mời hiện riêng khỏi danh sách sinh viên đã ghi danh.
- File đã sửa hoặc tạo: `EnrollmentServlet.java`, `EnrollmentDAO.java`, `EnrollmentService.java`, `enrollments/list.jsp`.

## Việc người dùng có thể kiểm tra

- Lệnh hoặc thao tác trên Chrome: từ dashboard bấm `Assigned classes`, rồi bấm mã lớp `SE2059`.
- Kết quả quan sát được: bảng lớp có SE2059, SWP391, FALL2026. Roster có Sample Student, trạng thái ACTIVE. Mục lời mời trống.
- Ảnh: không chụp được vì công cụ điều khiển Chrome hết thời gian chờ. Kết quả lấy từ HTTP 200 của `/ams/teacher/enrollments` và `/ams/teacher/enrollments?classId=1`.

## Giới hạn

- Phần AI không tự kiểm tra được: hình ảnh cửa sổ Chrome.
- Phần còn để người làm xác nhận: bấm SE2059 và đọc dòng Sample Student.
