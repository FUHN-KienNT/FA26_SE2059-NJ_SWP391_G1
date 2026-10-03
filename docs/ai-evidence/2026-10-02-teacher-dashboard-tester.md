# Phản biện tester — Teacher Dashboard

- Ngày: 2026-10-02
- Vai: tester muốn bước này fail trước khi commit

## Câu 1

- Hỏi: Người chưa đăng nhập có mở được `/teacher/dashboard` không?
- Vì sao câu này có thể làm hỏng bước: AuthFilter có thể bỏ sót đường dẫn mới.
- Trả lời: Không. Máy chủ trả 401.
- Bằng chứng: `GET http://localhost:8080/ams/teacher/dashboard` không gửi cookie trả HTTP 401. `AuthFilter` gắn với `/teacher/*`.

## Câu 2

- Hỏi: Teacher có xem được trang Admin không?
- Vì sao câu này có thể làm hỏng bước: Phiên demo có thể được cấp quyền quá rộng.
- Trả lời: Không. Phiên teacher bị chặn ở `/admin/users`.
- Bằng chứng: cùng cookie phiên teacher, `GET /ams/admin/users` trả HTTP 403.

## Câu 3

- Hỏi: Dashboard có đếm lớp của teacher khác không?
- Vì sao câu này có thể làm hỏng bước: Câu SQL có thể bỏ điều kiện `teacher_id`.
- Trả lời: Không. Trang chỉ hiện SE2059, lớp gắn với `teacher@example.test` trong seed.
- Bằng chứng: HTML dashboard chứa `SE2059` và `1 assigned class(es)`. SQL trong `TeacherDashboardDAO` lọc `WHERE c.teacher_id = ?`.

## Kết luận tester

- Chấp nhận commit: có
- Lỗi phải sửa trước khi commit (nếu có): không
