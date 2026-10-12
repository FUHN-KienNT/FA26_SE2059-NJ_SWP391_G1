# Bàn giao UC37 - Submission List và Submission Detail

Phần của Quang bổ sung **View Student Submissions (UC37)**: Teacher xem danh sách lượt làm và mở chi tiết đã lưu. Chấm tự luận (UC38) và công bố đánh giá (UC39) là bước tiếp theo, chưa triển khai trong phần này.

## Luồng và đường dẫn

Đăng nhập Teacher → Teacher Dashboard của Dũng → Assignment List → Assignment Detail → **View submissions** → Submission List → **View** → Submission Detail.

- List: `/ams/teacher/submissions?assignmentId=<id>`.
- Detail: `/ams/teacher/submissions?mode=view&assignmentId=<id>&submissionId=<id>`.
- Quay lại List giữ Search và Status; Reset xóa bộ lọc nhưng giữ Assignment đang xem.
- Chỉ đọc bằng GET/HEAD. POST trả 405; mở trang không thay đổi bài nộp, điểm, đáp án hoặc thời gian.

## Hành vi chính

Một dòng là **một Submission attempt**, không phải một học sinh. Bài Practice có nhiều lượt sẽ hiển thị riêng từng lượt. Các trạng thái được giữ nguyên: `IN_PROGRESS`, `SUBMITTED`, `GRADED`, `RELEASED`, `EXPIRED`, `VOID`.

List hiển thị học sinh, email, lần làm, trạng thái, thời gian nộp, điểm trên điểm tối đa và liên kết View. Search mặc định trống, Status mặc định All. Search tìm theo tên/email, không phân biệt hoa thường; `%`, `_` và nội dung giống SQL được xem là ký tự thông thường. Giới hạn Search là 200 ký tự Unicode.

Thứ tự được chọn là **`submitted_at DESC NULLS LAST, submission_id DESC`**: lượt đã nộp mới nhất đứng trước, lượt chưa có thời gian nộp đứng sau. Đây là lựa chọn triển khai; SRS/SDS không quy định một thứ tự, bộ lọc hay cách phân trang cụ thể cho màn này.

Detail hiển thị học sinh, lớp, Assignment, loại bài, lần làm, trạng thái và các mốc thời gian UTC+07. Bài Essay hiển thị nội dung/link đã lưu; bài Objective đối chiếu đáp án đã lưu với câu hỏi và lựa chọn tương ứng. Điểm, nhận xét và người chấm là dữ liệu đã lưu, không tính lại điểm khi mở trang. Chỉ link HTTP(S) hợp lệ được mở; nội dung văn bản được escape trước khi hiển thị.

`NULL` score nghĩa là chưa có điểm; **0 là điểm hợp lệ**. Dữ liệu `IN_PROGRESS`, `EXPIRED`, `VOID` có thể còn nội dung chưa hoàn thành nhưng không được coi là bài đã nộp hay kết quả được chấp nhận. Teacher được xem đánh giá chưa công bố và đáp án đúng trong lớp mình; quy tắc chỉ thấy điểm Essay sau RELEASED áp dụng cho Student.

## Quyền truy cập và lịch sử

Phải đăng nhập bằng tài khoản Teacher đang ACTIVE. Quyền được kiểm tra lại từ database theo **Teacher hiện đang phụ trách lớp của Assignment**, không dựa vào classId hoặc teacherId do trình duyệt gửi lên. Bản ghi không tồn tại và bản ghi ngoài quyền đều trả 404. ID/bộ lọc không hợp lệ trả 400; lỗi tải dữ liệu trả 503 với thông báo chung và đường thử lại.

Submission phải liên kết với Enrollment thuộc đúng lớp của Assignment và Registration thuộc đúng Subject Term. Không loại bỏ bài lịch sử chỉ vì tài khoản Student, Enrollment, Registration, lớp hoặc học kỳ đã ngừng hoạt động. Assignment đã Cancelled vẫn giữ bài nộp và kết quả lịch sử trong quyền xem của Teacher hiện tại.

## Các file của phần này

- `controller/TeacherSubmissionServlet.java`: route, kiểm tra phiên/role, lỗi và chế độ chỉ đọc.
- `service/TeacherSubmissionService.java`: tải snapshot nhất quán, kiểm tra Teacher và Assignment sở hữu.
- `dao/TeacherSubmissionDAO.java`: truy vấn List/Detail theo quyền, bộ lọc và thứ tự.
- `dto/TeacherSubmissionFilter.java`, `TeacherSubmissionRow.java`, `TeacherSubmissionListPage.java`, `TeacherSubmissionDetailPage.java`: dữ liệu truyền giữa các tầng.
- `util/TeacherSubmissionView.java`: hiển thị và dựng liên kết an toàn.
- `WEB-INF/views/submissions/list.jsp`, `detail.jsp`: giao diện mới; liên kết vào màn được thêm trong Assignment Detail.
- `css/submissions.css`: giao diện List/Detail mới. `css/assignment-detail.css` cho phép nhóm nút xuống dòng khi thêm View submissions.
- Các test riêng của Teacher Submission và tài liệu bàn giao này.

Tận dụng model Submission và hàm map đã có; **không sửa SubmissionDAO, schema, Dashboard chung hay cơ chế đăng nhập**. Không commit, push hoặc merge Git trong tác vụ này.

## Kiểm chứng và bản chạy

Lệnh `mvn -Dassignment.db.tests=true package -q` đã hoàn thành: **212 test, 211 đạt, 1 bỏ qua, 0 lỗi/thất bại**. Test bỏ qua là DBConnectionIntegrationTest có cờ chạy riêng. JSP đã biên dịch với 0 lỗi. Phần mới có 29 test: 15 test service/database (12 dùng PostgreSQL TEMP), 8 test servlet và 6 test hiển thị/link/đáp án.

Đã kiểm tra quyền Teacher hiện tại, bản ghi ngoài Assignment/lớp/học kỳ, chuyển Teacher phụ trách, dữ liệu lịch sử inactive, 6 trạng thái, thứ tự có NULL, bộ lọc ký tự thường, điểm NULL và 0, JSON không hợp lệ, URL an toàn, và snapshot các bảng không thay đổi sau GET. Test database chỉ dùng bảng/sequence TEMP; không nạp hay sửa bài nộp thật.

WAR mới: `G:\SWP\FA26_SE2059-NJ_SWP391_G1\target\ams.war`. Launcher tại `G:\SWP\tools\ams-submissions-20261012\start-preview.ps1` đã sao chép WAR mới và khởi chạy thành công trong terminal trên cổng **8082**, chỉ lắng nghe 127.0.0.1. Java PID **15148**, runtime `G:\SWP\tools\ams-submissions-20261012\runtime`. Đường dẫn `http://localhost:8082/ams/auth/signin` đã trả **HTTP 200**, sau khi trước đó không kết nối được. Giữ terminal chạy khi test; Ctrl+C dừng bản xem này.

Các lệnh triển khai/khởi chạy nền trước đó từng bị kiểm duyệt tự động chặn. Bản 8082 hiện chạy bằng script trực tiếp trong terminal; server 8081 cũ vẫn giữ nguyên, chưa được cập nhật. Người dùng đã xác nhận Reload hiển thị trang đăng nhập. Công cụ điều khiển trình duyệt từ chối truy cập URL localhost với thông báo giao thức không được cho phép, nên chưa kiểm tra trực tiếp Submission List/Detail trên trình duyệt. Kiểm tra HTTP và xác nhận trang đăng nhập không thay thế bước kiểm tra các màn Submission; báo cáo chưa khẳng định visual QA các màn đó đã hoàn thành.

## Dữ liệu test đã nạp ngày 12/10/2026

File `database/seed_submission_quang.sql` đã được nạp vào database local `ams_db`, lớp **IT-Q-A1 (class_id=9)** của Teacher `quang.it.a@example.test`. Dùng bốn Student/Enrollment test hiện có; không tạo hay thay đổi tài khoản, lớp, template hoặc enrollment.

| Assignment | ID | Số lượt | Trường hợp thử |
| --- | --- | --- | --- |
| [TEST-QUANG] Submission 01 - Báo cáo Web | 80 | 4 | SUBMITTED chưa chấm, GRADED điểm 16 chưa công bố, RELEASED điểm 0, IN_PROGRESS lưu tạm |
| [TEST-QUANG] Submission 02 - Luyện tập SQL | 81 | 4 | Cùng Student ba lượt RELEASED điểm 0/10/20, Student khác đang làm; 2 câu hỏi có ID và lựa chọn thực tế |
| [TEST-QUANG] Submission 03 - Bài đã hết hạn | 82 | 2 | EXPIRED chưa nộp, SUBMITTED đúng hạn còn chờ chấm |
| [TEST-QUANG] Submission 04 - Bài đã hủy | 83 | 2 | VOID giữ nội dung lưu tạm, RELEASED điểm 18 trước khi hủy |

Tổng cộng 4 Assignment, 2 Question và 12 Submission (ID 23-34), đủ sáu trạng thái. Link trong bài tự luận là link mẫu `https://example.com/`. Tất cả điểm/nhận xét ở các bài có nhãn TEST-QUANG là dữ liệu giả để kiểm thử màn xem, không phải kết quả học tập thật.

Seed chỉ thêm dữ liệu mới trong một transaction, có khóa chống chạy đồng thời. Các bảng chứa dữ liệu trước đó được đối chiếu số dòng và fingerprint trước khi commit; không thay đổi bản ghi cũ, bao gồm Assignment 76/77/78/79. Chạy lại batch hoàn chỉnh giữ nguyên cả ID và timestamp. Đã xác nhận TeacherSubmissionService xem được List và Detail của cả 12 lượt.

## Cập nhật cách xem câu hỏi và bài làm — 12/10/2026

- Essay: hiển thị Instructions, Scope và danh sách Questions / tasks ngay phía trên Student's essay answer. Nội dung bài làm giữ nguyên xuống dòng. Hiện dữ liệu Essay là một `submissions.content` chung; không tự chia hoặc lặp câu trả lời cho từng task vì chưa có liên kết câu trả lời theo task.
- Objective: mỗi câu hỏi nằm trong một khung riêng, hiện toàn bộ lựa chọn và đánh dấu lựa chọn của sinh viên. Đáp án đúng có ✓ và màu xanh; lựa chọn sai của sinh viên có ✗ và màu đỏ. Lựa chọn còn lại hiển thị trung tính. Có nhãn chữ để không phụ thuộc riêng vào màu sắc.
- Câu chưa trả lời vẫn hiện câu hỏi, lựa chọn và thông báo chưa chọn đáp án. Nếu dữ liệu đáp án bị lỗi hoặc không đủ để đối chiếu, hiển thị cảnh báo và tránh kết luận đúng/sai khi chưa xác định được.
- Đây là thay đổi hiển thị dành cho Teacher. Điểm đã lưu không được tính lại; không sửa nội dung bài nộp hoặc database. Chức năng chấm và công bố kết quả chưa nằm trong thay đổi này.
- Kiểm chứng lần cập nhật này: `mvn -Dassignment.db.tests=true -Dtest=TeacherSubmissionViewTest,TeacherSubmissionServletTest,TeacherSubmissionDatabaseTest,JspCompilationTest package -q` đạt **41/41 test**, không lỗi hoặc bỏ qua; JSP biên dịch thành công. Đã cập nhật WAR và khởi động lại bản local cổng 8082 lúc 21:51 ngày 12/10/2026; trang đăng nhập trả HTTP 200. Chưa kiểm tra trực quan màn mới bằng công cụ trình duyệt.

## Cập nhật ghép riêng câu hỏi tự luận và câu trả lời — 13/10/2026

Phạm vi là **Teacher → View submissions → View bài nộp của sinh viên** (`/teacher/submissions?mode=view`). Mỗi câu hỏi/task nằm trong một khung, ngay dưới là câu trả lời riêng của sinh viên cho câu đó. Instructions/Scope nằm trong phần mở rộng; không còn dồn toàn bộ câu hỏi vào một khung tách xa câu trả lời. Assignment Detail và phần trắc nghiệm không đổi trong lần cập nhật này.

`EssaySubmissionView` đọc nội dung tự luận có phiên bản trong cột TEXT `submissions.content`:

```json
{"format":"ams-essay-v1","answers":[{"task_order":1,"text":"Trả lời câu 1"},{"task_order":2,"text":"Trả lời câu 2"}]}
```

- Ghép bằng `task_order` của `assignments.task_checklist` đã cố định khi publish, không ghép theo vị trí đoạn văn. Giữ nguyên ngắt dòng trong câu trả lời.
- Câu chưa trả lời vẫn hiển thị đề và thông báo chưa có câu trả lời. Câu trả lời không xác định được task hoặc trùng khóa giữ riêng với cảnh báo; không tự gán sang câu khác.
- Nội dung dạng cũ giữ nguyên trong **Original combined answer**. Không tự tách bài cũ (ví dụ Submission 25 / Assignment 80) thành các câu trả lời được phỏng đoán.
- Không đổi schema và không dùng cột `answer` của Objective cho Essay. **Luồng Student làm/nộp bài chưa được triển khai**; khi làm luồng đó cần một ô trả lời cho mỗi task và lưu theo định dạng trên. Phần hiện tại đọc dữ liệu đã lưu và dữ liệu test.

Đã thêm và chạy `database/seed_essay_answers_quang.sql`: Assignment **84**, tên `[TEST-QUANG] Essay - Câu hỏi và trả lời riêng`, lớp IT-Q-A1 của `quang.it.a@example.test`:

| Submission | Dữ liệu kiểm tra |
| --- | --- |
| 35 | SUBMITTED, đủ hai câu trả lời riêng, có nhiều đoạn văn |
| 36 | IN_PROGRESS, đã trả lời câu 1 và chưa trả lời câu 2 |
| 37 | SUBMITTED, nội dung dạng cũ lưu chung |

Seed chỉ thêm mới; fingerprint tất cả bảng hiện có xác nhận dữ liệu cũ không đổi. Chạy lại xác nhận toàn bộ dòng không đổi. Kiểm tra parser với dữ liệu thật xác nhận các câu được ghép đúng và không ghép nhầm bài dạng cũ.

Kiểm chứng: **57/57 test đạt**, gồm EssaySubmissionViewTest (16), TeacherSubmissionViewTest (17), TeacherSubmissionServletTest (8), TeacherSubmissionDatabaseTest (15), JspCompilationTest (1); không bỏ qua/lỗi/thất bại. WAR mới đã triển khai và máy chủ 8082 khởi động lại ngày 13/10/2026. Chưa có kiểm tra trực quan bằng công cụ trình duyệt. Không commit/push/merge.

## Dữ liệu Objective đúng/sai và bài 10 câu — 13/10/2026

Đã chạy `database/seed_objective_mixed_answers_quang.sql` và `database/seed_objective_10_questions_quang.sql` trong cùng transaction trên local `ams_db`: thêm **1 Assignment, 10 Question, 9 Submission**. Không thay đổi dữ liệu cũ hoặc code ứng dụng.

| Assignment | Submission mới | Kết quả |
| --- | --- | --- |
| 49 — Quiz Alpha, 3 câu | 38 / 39 | 2 đúng + 1 sai = 7/10; 1 đúng + 2 sai = 3/10 |
| 50 — Quiz Beta, 1 câu | 40 / 41 | Một phiếu đúng 10/10, một phiếu sai 0/10 |
| 81 — Luyện tập SQL, 2 câu | 42 / 43 | Mỗi phiếu 1 đúng + 1 sai = 10/20; chọn đúng ở hai câu khác nhau |
| **85 — [TEST-QUANG] Objective - 10 câu SQL và Web** | **44 / 45 / 46** | **7 đúng + 3 sai; 5 đúng + 5 sai; 3 đúng + 7 sai** |

Bài 85 thuộc lớp IT-Q-A1 của Teacher `quang.it.a@example.test`, Regular/Objective/Published, 10 câu × 1 điểm, mỗi câu có 4 lựa chọn A–D, thời gian 30 phút. Cả ba phiếu đều RELEASED; `graded_by` NULL vì kết quả Objective tự động. Dữ liệu là giả lập để kiểm thử, không phải thao tác làm bài thật qua Student UI.

Đã xác minh reference Question/option, số câu/lựa chọn, điểm từng câu và tổng điểm, thời gian nộp/chấm/công bố, quyền đọc qua TeacherSubmissionService và hiển thị qua TeacherSubmissionView. Fingerprint toàn bộ bản ghi có trước xác nhận không thay đổi. Chạy lại hai seed thêm **0 dòng**, không sửa dòng nào. Không cần build hoặc restart; tải lại trang để thấy dữ liệu mới. Seed tổng cũ `seed_submission_quang.sql` kiểm tra số lượng cố định nên có thể báo lỗi cấu trúc sau khi các lượt mới đã được thêm; dùng hai seed bổ sung này để kiểm tra/chạy lại dữ liệu mới.

## Nguồn và điểm cần đồng bộ tài liệu

SRS: trang PDF 19 (Submission), 25-26 (DBR44-61), 35 (UC37-39), 42-43 (quyền), 47 (Screen Inventory). SDS: trang PDF 25-26 (submissions).

Use-case inventory xác định **UC36 Cancel Assignment, UC37 View Student Submissions**. Một số mô tả màn Assignment trong SRS đang ghi Cancel là UC37; cần sửa tham chiếu đó khi cập nhật tài liệu nhóm. SRS/SDS hiện chưa có bảng mô tả chi tiết bộ lọc/cột của Submission List, vì vậy cần bổ sung các lựa chọn triển khai nêu trên.
