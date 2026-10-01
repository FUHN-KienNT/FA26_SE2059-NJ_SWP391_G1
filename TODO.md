<!-- progress -->
Tiến độ: 3/8 bước lớn (38%)
Cập nhật: 2026-10-02
Bước vừa xong: Student Enrollment Management
<!-- /progress -->

Nguồn: bảng phân công DungNM cập nhật 2026-10-02. Iter2 làm luồng giảng viên trước: Dashboard, danh sách lớp kèm roster, rồi quản lý ghi danh. Import nằm trong màn ghi danh. Các bước còn lại giữ nguyên cho đợt sau.

## Iter2 — luồng giảng viên

- [x] Teacher Dashboard
  - Màn hình. UC14 View Assigned Classes; UC15 View Assignments; UC23 View Student Submissions. SRS I.5.1 Teacher Dashboard.
  - Tổng quan lớp được giao, sĩ số, bài tập và bài nộp. Có lối sang danh sách lớp.

- [x] Assigned Class List / Class Roster
  - Màn hình. UC14 View Assigned Classes; UC16 View Class Students. SRS II.7.2.1.
  - File sẵn: `EnrollmentServlet` (`/teacher/enrollments`), `enrollments/list.jsp`.
  - Teacher xem lớp đang được giao, sĩ số, sinh viên đã vào lớp và lời mời còn hiệu lực. Từ roster mở màn ghi danh.

- [x] Student Enrollment Management
  - Màn hình. UC16 Add Student to Class; UC18 Update Student Enrollment. SRS II.7.2.2.
  - File sẵn: `enrollments/detail.jsp`, `EnrollmentService`, `EnrollmentDAO`.
  - Teacher thêm sinh viên đã có tài khoản và đổi trạng thái ghi danh khi còn chỗ.
  - [x] Import Students to Class
    - Chức năng trên cùng màn, không phải màn riêng. UC17. SRS II.7.2.2 / Import Students.
    - Đọc CSV, ghi danh sinh viên đã có tài khoản, bỏ qua dòng trùng, báo dòng không hợp lệ.

- [ ] Additional Class Assignment Offer
  - Màn hình. UC37 Respond to Additional Class Assignment Offer. SRS I.5.1 Additional Class Assignment Offer.
  - Chưa làm trong đợt này. Teacher xem và nhận hoặc từ chối lời mời dạy lớp bổ sung.

## Iter3

- [ ] Subject Class Detail / Management
  - Màn hình. UC11 Manage Subject Classes. SRS II.7.1.2 / 7.1.1 Subject Class Management.
  - File sẵn: `SubjectClassServlet` (`/expert/classes`), `subject-classes/detail.jsp`.
  - Expert xem và cập nhật lớp trong phạm vi được giao.

- [ ] Student Invitation / Acceptance
  - Màn hình. SRS II.7.2.2 / Invitation Flow.
  - Người được mời xác nhận email qua Google Sign-In rồi được ghi danh.

- [ ] Additional Class Request
  - Màn hình. UC36 Request Additional Class. SRS I.5.1.1 Additional Class Request.
  - Teacher tạo một yêu cầu lớp bổ sung cho Overflow Batch chưa xử lý.

- [ ] Additional Class Request Review
  - Màn hình. UC36 Review Additional Class Request. SRS I.5.1.1 Additional Class Request Review.
  - Primary Expert đang active xem, duyệt hoặc từ chối yêu cầu. Nếu môn có hai Primary Expert, lần duyệt hoàn tất trước có hiệu lực.
