# Phản biện tester — Assigned Class List / Class Roster

- Ngày: 2026-10-02
- Vai: tester muốn bước này fail trước khi commit

## Câu 1

- Hỏi: Teacher có xem roster của lớp không được giao, ví dụ `classId=999`, không?
- Vì sao câu này có thể làm hỏng bước: Tham số lớp có thể bỏ qua quyền sở hữu.
- Trả lời: Không. Yêu cầu bị từ chối.
- Bằng chứng: `GET /ams/teacher/enrollments?classId=999` với phiên teacher trả HTTP 400. `EnrollmentServlet` chỉ nhận lớp có trong danh sách của teacher đó.

## Câu 2

- Hỏi: Sinh viên đã ghi danh có bị hiện nhầm vào bảng lời mời không?
- Vì sao câu này có thể làm hỏng bước: Hai bảng dùng chung một danh sách sẽ làm sai sĩ số.
- Trả lời: Không. Sample Student nằm ở roster. Bảng lời mời không có dòng.
- Bằng chứng: HTML của `/ams/teacher/enrollments?classId=1` có `Sample Student` và câu `No invitation is stored for this class.`

## Câu 3

- Hỏi: Danh sách lớp của teacher khác có lọt vào trang này không?
- Vì sao câu này có thể làm hỏng bước: Seed có expert và admin; truy vấn thiếu `teacher_id` sẽ hiện lớp của họ.
- Trả lời: Không. Chỉ SE2059, lớp mà seed gán cho teacher@example.test.
- Bằng chứng: `TeacherDashboardDAO` dùng `WHERE c.teacher_id = ?`. Trang danh sách chỉ có một dòng SE2059.

## Kết luận tester

- Chấp nhận commit: có
- Lỗi phải sửa trước khi commit (nếu có): không
