# Phản biện tester — Student Enrollment Management

- Ngày: 2026-10-02
- Vai: tester muốn bước này fail trước khi commit

## Câu 1

- Hỏi: Thêm lại sinh viên đang ACTIVE có tạo ghi danh thứ hai không?
- Vì sao câu này có thể làm hỏng bước: Unique index chỉ chặn ở database; màn hình có thể hiện lỗi 500.
- Trả lời: Không tạo thêm. Màn hình hiện câu đã có ghi danh active cho kỳ học.
- Bằng chứng: POST `action=addStudent` với `student@example.test` trả trang quản lý và chứa `already has an active enrollment`. Roster sau đó vẫn là một dòng Sample Student.

## Câu 2

- Hỏi: Tắt ghi danh rồi bật lại có mất sinh viên hoặc kẹt ở INACTIVE không?
- Vì sao câu này có thể làm hỏng bước: Cập nhật trạng thái có thể quên xóa `deactivated_at` hoặc không kiểm tra sĩ số.
- Trả lời: Không. Trạng thái về ACTIVE.
- Bằng chứng: POST `status=INACTIVE` làm HTML chứa `INACTIVE`. POST `status=ACTIVE` ngay sau đó làm HTML chứa `ACTIVE` cho cùng enrollment 1.

## Câu 3

- Hỏi: CSV có email đã học và một dòng không phải email thì có ghi nhận sai thành công không?
- Vì sao câu này có thể làm hỏng bước: Import có thể đếm mọi dòng là added.
- Trả lời: Không. Kết quả là thêm 0, bỏ qua 1, từ chối 1.
- Bằng chứng: POST multipart `importStudents` rồi mở lại màn quản lý, thông báo `Import finished. Added 0, skipped 1, rejected 1.`

## Kết luận tester

- Chấp nhận commit: có
- Lỗi phải sửa trước khi commit (nếu có): không
