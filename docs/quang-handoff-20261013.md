# Quang — Bàn giao Assignment và Teacher Submission

Ngày kiểm tra: 13/10/2026. Nhánh làm việc: `quang`; nhánh chính của repository là `master`.
Mốc đối chiếu: `87dc0ae` (origin/master trước lần bàn giao này).

## Phần đã thực hiện

| Phần | Hành vi được bàn giao |
| --- | --- |
| Create Assignment — UC33 | Chọn template, sửa tiêu đề/hướng dẫn/điểm/thời gian và nhập nội dung ngay trong form; lưu bản Draft và chuyển tới Detail. |
| Edit Assignment — UC34 | Sửa Draft bằng form dùng chung; giữ ID câu hỏi/lựa chọn còn tồn tại; kiểm tra phiên bản để tab cũ không ghi đè thay đổi mới. |
| Essay / Objective | Essay dùng Scope và Tasks; Objective dùng câu hỏi và lựa chọn. Chuyển loại giữ nội dung chưa lưu trên form, chỉ lưu nội dung của loại đang chọn. |
| Publish Assignment — UC35 | Trang xác nhận chỉ đọc; POST kiểm tra cấu hình đã lưu, điểm, câu hỏi, thời gian và quyền rồi chuyển Draft thành Published. |
| Cancel Assignment — UC36 | Trang xác nhận tác động; hủy Assignment và chuyển các lượt IN_PROGRESS thành VOID trong một transaction, giữ nguyên bài đã nộp và kết quả lịch sử. |
| Teacher View Submissions — UC37 | List tìm theo tên/email và lọc trạng thái; Detail hiển thị từng lượt làm, nội dung, kết quả đã lưu và thời gian UTC+07. |
| Bài tự luận | Đặt câu trả lời ngay dưới câu hỏi tương ứng khi dữ liệu có khóa task_order; bài dạng cũ giữ nguyên nội dung chung, không tự đoán cách tách câu. |
| Bài trắc nghiệm | Hiển thị toàn bộ lựa chọn; đáp án đúng có dấu tick và màu xanh, lựa chọn sai của học sinh có dấu X và màu đỏ, kèm nhãn chữ. Không tính lại điểm khi xem. |
| Thứ tự nội dung Assignment Detail | Sửa hiển thị task/lựa chọn theo task_order/option_order đã lưu; dữ liệu cũ thiếu hoặc có thứ tự không hợp lệ vẫn giữ nội dung với thứ tự dự phòng ổn định. |
| Dữ liệu test | Các SQL riêng cho Assignment/Submission, bài Essay có câu trả lời riêng và bài Objective 10 câu với các phiếu đúng/sai hỗn hợp. |

Luồng điều hướng sử dụng đăng nhập Teacher và Dashboard chung của Dũng để vào Assignment List/Detail, rồi View submissions. Lần bàn giao này không tạo lại Teacher Dashboard riêng.

Các POST kiểm tra CSRF và quyền Teacher hiện đang phụ trách lớp. Khi token form hết hạn, yêu cầu bị từ chối và nội dung nhập được giữ để người dùng kiểm tra rồi gửi lại. Các thay đổi cha/con được thực hiện trong transaction. Không thay đổi schema, cấu hình DB riêng hoặc code đăng nhập dùng chung.

## Phần chưa triển khai

- UC38: nhập điểm/nhận xét, lưu nháp đánh giá, hoàn tất và mở lại chấm Essay.
- UC39: Teacher công bố kết quả Essay. Các điểm và trạng thái đánh giá đang thấy trong dữ liệu demo là dữ liệu test đã lưu.
- Luồng Student làm/nộp bài và đọc kết quả. Student chỉ được xem điểm/nhận xét Essay sau khi RELEASED khi luồng này được xây dựng.

Teacher Submission hiện là chức năng chỉ đọc. Định dạng Essay `ams-essay-v1` trong `submissions.content` được mô tả ở [quang-submission-list.md](quang-submission-list.md); phía Student sau này cần lưu câu trả lời theo từng task_order, không ghép dựa trên vị trí đoạn văn.

## Kiểm chứng trước khi push

- JDK 17, Maven 3.9.16, PostgreSQL local hoạt động.
- Lượt đầu: `mvn -Dassignment.db.tests=true clean package -q` build thành công, 238 test đạt và 1 test kết nối bị bỏ qua vì yêu cầu biến môi trường. Chạy riêng test kết nối với cấu hình local: 1/1 đạt.
- Sau đó sửa thứ tự nội dung Assignment Detail, bổ sung 4 test hồi quy và chạy lại `mvn -Dassignment.db.tests=true clean package -q` với biến môi trường DB local: **243/243 test đạt, 0 thất bại, 0 lỗi, 0 bỏ qua; JSP biên dịch 0 lỗi; WAR build thành công**.
- Các test ghi database của phần Assignment/Submission sử dụng bảng và sequence TEMP, không nạp lại dữ liệu demo hay sửa bài nộp thật.
- `git diff --cached --check` không báo lỗi khoảng trắng hoặc marker xung đột.
- Rà soát thủ công quyền truy cập, CSRF, khóa/rollback, xung đột phiên bản, escape nội dung và link bài nộp; chưa thấy lỗi cản trở bàn giao. Định dạng Java ở một số phương thức còn khá ngắn/gộp dòng, có thể làm rõ ở lần cải thiện sau.
- Đối chiếu yêu cầu dựa trên tài liệu bàn giao trong repository và các thay đổi người dùng đã chốt. Bản trích xuất SRS/SDS tại đường dẫn cũ không còn trên máy trong lượt review này.
- Lần này không chạy lại kiểm thử trực quan trên trình duyệt, không triển khai WAR hoặc khởi động lại server. Các bằng chứng UI trước đó nằm trong tài liệu từng chức năng.

## Dữ liệu demo và lưu ý cho nhóm

SQL demo không chạy tự động khi build hoặc deploy. Không cần migration database cho các chức năng bàn giao này.

| File | Mục đích / điều kiện |
| --- | --- |
| `database/seed_integration_quang.sql` | Tạo fixture test cho tài khoản, lớp và Assignment trong local ams_db; cần dữ liệu nền phù hợp. Xem hướng dẫn fixture trước khi chạy. |
| `database/seed_submission_quang.sql` | Thêm các trạng thái Submission trên fixture có sẵn. Batch này kiểm tra số dòng cố định, có thể từ chối chạy lại sau khi thêm các phiếu bổ sung. |
| `database/seed_essay_answers_quang.sql` | Bài tự luận có câu trả lời riêng, câu chưa trả lời và bài dạng cũ. |
| `database/seed_objective_mixed_answers_quang.sql` | Thêm phiếu trắc nghiệm đúng/sai trên các Assignment demo đã có. |
| `database/seed_objective_10_questions_quang.sql` | Thêm bài 10 câu và ba phiếu điểm 7/10, 5/10, 3/10. |

Một số seed bổ sung ràng buộc ID/context của database demo trên máy Quang; cần đọc các điều kiện đầu file trước khi dùng ở máy khác. Không bỏ các kiểm tra này để chạy trên dữ liệu không khớp. ID minh họa trong tài liệu là ID local, không phải ID chung cho mọi môi trường. Tài khoản/mật khẩu và nội dung có nhãn TEST/IT trong fixture chỉ dành cho phát triển.

Không commit `application.properties`, thông tin kết nối riêng, WAR, log, hoặc thư mục runtime. Chỉ push source, test, SQL demo và tài liệu. Các ghi chú cũ như "chưa commit/push" trong tài liệu chức năng mô tả thời điểm kiểm tra trước lần bàn giao này.

## Tài liệu chi tiết và đồng bộ SRS/SDS

- [Create Assignment](quang-create-assignment.md)
- [Edit Assignment](quang-edit-assignment.md)
- [Publish / Cancel](quang-assignment-lifecycle.md)
- [Teacher Submission và định dạng câu trả lời](quang-submission-list.md)
- [Điều hướng Teacher](quang-assignment-navigation.md)
- [Fixture kiểm thử](quang-integration-fixtures.md)

Cần đồng bộ tài liệu nhóm theo yêu cầu đã chốt: Scope/Tasks chỉ dành cho Essay; Create nhập được toàn bộ nội dung trước khi lưu Draft; đúng mã UC33 Create, UC34 Edit, UC35 Publish, UC36 Cancel và UC37 View Student Submissions. UC38/UC39 là công việc tiếp theo, chưa được tính là hoàn thành trong lần bàn giao này.
