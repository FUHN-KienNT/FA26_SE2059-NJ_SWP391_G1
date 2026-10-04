# Dữ liệu test Teacher — Quang

Đã nạp `seed_teacher_demo_quang.sql` vào PostgreSQL local `ams_db` ngày 04/10/2026. Dữ liệu dùng tài khoản Teacher hiện có `teacher@example.test`. Mật khẩu không thay đổi.

## Kết quả trên Dashboard

Tổng ngay sau khi nạp: **4 lớp, 17 lượt ghi danh active, 25 bài tập và 16 lượt làm bài**. Dashboard hiện cộng tất cả trạng thái của Submission, bao gồm IN_PROGRESS, EXPIRED và VOID.

| Class | Capacity | Roster active | Lời mời pending còn hạn | Seats left | Assignments |
|---|---:|---:|---:|---:|---:|
| SE2059 | 30 | 1 | 0 | 29 | 13 |
| DEMO-Q-SWP-A | 20 | 8 | 2 | 10 | 7 |
| DEMO-Q-SWP-B | 10 | 8 | 2 | 0 | 5 |
| DEMO-Q-EMPTY | 25 | 0 | 0 | 25 | 0 |

Lời mời demo được gửi 1 giờ trước lúc nạp và hết hạn sau 48 giờ kể từ sent_at. Vì vậy, sau khoảng 47 giờ, Pending/Seats left sẽ thay đổi theo thời gian. Rerun không gia hạn lời mời.

## Bài tập để thử

Mở Dashboard → View assignments của lớp A hoặc B; hoặc chọn lớp trong bộ lọc Class rồi bấm Apply filters. Menu Assignments mặc định chọn lớp đầu theo class_code; DEMO-Q-EMPTY đứng đầu để thử trường hợp danh sách rỗng.

| Số bài demo | Lớp | Loại / trạng thái | Trường hợp test |
|---|---|---|---|
| 01 Báo cáo yêu cầu SRS | A | Regular / Essay / Published | Đề nhiều dòng, scope, checklist dạng object |
| 02 Thiết kế database & ERD | A | Regular / Essay / Draft | Checklist dạng chuỗi, chưa có hạn nộp |
| 03 Trắc nghiệm UML | A | Regular / Objective / Published | 3 câu, điểm 3+3+4, mỗi câu có 3 lựa chọn |
| 04 Luyện SQL 100%_cơ bản | A | Practice / Objective / Published | 4 câu, mỗi câu 2.5 điểm, 3 lượt làm, 20 phút; tìm kiếm ký tự %_ |
| 05 Java Quiz đã hủy | A | Regular / Objective / Cancelled | Nội dung cũ, 2 câu, thời điểm hủy |
| 06 Báo cáo kiến trúc quá hạn | A | Regular / Essay / Published | Hạn nộp trước thời điểm nạp 1 ngày, vẫn là Published |
| 07 Quiz chưa tới giờ mở | A | Regular / Objective / Published | Available from sau thời điểm nạp 2 ngày, 2 câu |
| 08 Báo cáo Java Web | B | Regular / Essay / Published | Đề và checklist đầy đủ |
| 09 Quiz Servlet bản nháp | B | Regular / Objective / Draft | 3 câu, thời gian chưa thiết lập |
| 10 Luyện JDBC bản nháp | B | Practice / Objective / Draft | 3 câu, 5 lượt làm, 20 phút |
| 11 Trắc nghiệm Git Workflow | B | Regular / Objective / Published | 3 câu, 30 phút |
| 12 Đề nháp chưa có câu hỏi | B | Regular / Objective / Draft | Empty state của phần Questions |

Tất cả tiêu đề mới có tiền tố `[DEMO-QUANG]`. Các đáp án đúng demo được đặt ở lựa chọn A; mỗi bộ câu hỏi hoàn chỉnh có tổng điểm 10.

Các bộ lọc trên dữ liệu mới:

| Phạm vi | Draft | Published | Cancelled | Essay | Objective | Regular | Practice |
|---|---:|---:|---:|---:|---:|---:|---:|
| Lớp A | 1 | 5 | 1 | 3 | 4 | 6 | 1 |
| Lớp B | 3 | 2 | 0 | 1 | 4 | 4 | 1 |
| Hai lớp mới | 4 | 7 | 1 | 4 | 8 | 10 | 2 |

## Các bảng được thêm dữ liệu

| Bảng | Số dòng mới |
|---|---:|
| users | 17 Student mẫu |
| subject_classes | 3 |
| class_experts | 3 |
| subject_registrations | 17 |
| class_enrollments | 17: 16 active, 1 inactive |
| student_invitations | 7: 4 pending, 1 accepted, 1 expired, 1 cancelled |
| assignment_templates | 2 |
| assignments | 12 |
| questions | 20 |
| submissions | 16 |

Submissions: SUBMITTED 5, GRADED 4, RELEASED 3, IN_PROGRESS 2, EXPIRED 1, VOID 1. Student 01 có 2 lượt SQL Practice để thử việc đếm lượt làm. Tài khoản Student là dữ liệu mẫu với Google subject giả; dùng Teacher hiện có để test ba màn hình.

## Chạy lại và kiểm tra

Script dùng một transaction, chỉ INSERT các fixture chưa tồn tại, không sửa/xóa dữ liệu cũ. Nếu fixture đã được chuyển sang lớp khác, script dừng thay vì tự đổi lại. Có thể chạy bằng psql trên database phát triển local:

```powershell
& 'G:\SQLConVoi\bin\psql.exe' -h localhost -U ams_user -d ams_db -W -v ON_ERROR_STOP=1 -f .\database\seed_teacher_demo_quang.sql
```

Đã kiểm tra dữ liệu cũ giữ nguyên, lần chạy thứ hai không tạo trùng, quan hệ registration/class/template/submission khớp, thứ tự thời gian hợp lệ, JSON hiển thị được và số liệu Dashboard/List khớp. Đã mở trực tiếp Dashboard, Assignment List và Detail của cả Essay lẫn Objective trên Tomcat.

Bản sao lưu trước khi nạp: `G:\SWP\backups\before-demo-seed-20261004-225146\ams_db.dump`.

Script chỉ thêm dữ liệu; không thay đổi schema và không cần restart Tomcat. Được đưa vào nhánh `quang` cùng hướng dẫn này để nhóm có thể tự nạp trên database phát triển. Pull/merge code không tự chạy SQL; không sử dụng script này cho database production.
