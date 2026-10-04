-- Local development fixtures for Quang: Dashboard, Assignment List and Assignment Detail.
-- Uses teacher@example.test and its existing active Subject/Term. No passwords are changed.
-- Adds tagged records only. Re-running skips existing fixtures; no UPDATE/DELETE/DROP.
-- Pending invitations expire 48 hours after sent_at (47 hours after the first run).
BEGIN;
DO $seed$
DECLARE
    v_teacher BIGINT;
    v_subject_term BIGINT;
    v_subject BIGINT;
    v_expert BIGINT;
    v_class BIGINT;
    v_user BIGINT;
    v_registration BIGINT;
    v_assignment BIGINT;
    v_enrollment BIGINT;
    v_template BIGINT;
    v_now TIMESTAMPTZ := CURRENT_TIMESTAMP;
    v_started TIMESTAMPTZ;
    v_deadline TIMESTAMPTZ;
    v_answer JSONB;
    v_email TEXT;
    v_code TEXT;
    f RECORD;
    v_question_fixture RECORD;
    v_assignment_row RECORD;
    n INTEGER;
BEGIN
    IF current_database() <> 'ams_db' THEN
        RAISE EXCEPTION 'Run this development seed against local ams_db only';
    END IF;
    PERFORM pg_advisory_xact_lock(3912059, 20261004);
    SELECT user_id INTO v_teacher FROM users
    WHERE lower(btrim(email)) = 'teacher@example.test' AND role = 'TEACHER' AND account_status = 'ACTIVE';
    IF v_teacher IS NULL THEN RAISE EXCEPTION 'Active teacher@example.test is required'; END IF;
    SELECT st.subject_term_id, st.subject_id INTO v_subject_term, v_subject
    FROM subject_classes c JOIN subject_terms st USING(subject_term_id)
    JOIN subjects s USING(subject_id) JOIN academic_terms t USING(term_id)
    JOIN teacher_subjects ts ON ts.subject_id = s.subject_id AND ts.teacher_id = v_teacher AND ts.is_active
    WHERE c.teacher_id = v_teacher AND c.status = 'ACTIVE' AND s.status = 'ACTIVE' AND t.is_active
      AND c.class_code NOT LIKE 'DEMO-Q-%'
    ORDER BY t.is_current DESC, c.class_id LIMIT 1;
    IF v_subject_term IS NULL THEN RAISE EXCEPTION 'An existing eligible active class is required'; END IF;
    SELECT se.expert_id INTO v_expert FROM subject_experts se JOIN users u ON u.user_id = se.expert_id
    WHERE se.subject_id = v_subject AND se.is_active AND u.role = 'EXPERT' AND u.account_status = 'ACTIVE'
    ORDER BY (se.responsibility_type = 'PRIMARY') DESC, se.expert_id LIMIT 1;
    IF v_expert IS NULL THEN RAISE EXCEPTION 'An active Subject Expert is required'; END IF;

    FOR f IN SELECT * FROM (VALUES
        ('DEMO-Q-SWP-A', '[DEMO-QUANG] Phân tích và thiết kế hệ thống', 20),
        ('DEMO-Q-SWP-B', '[DEMO-QUANG] Java Web và Git', 10),
        ('DEMO-Q-EMPTY', '[DEMO-QUANG] Lớp chưa có bài tập', 25)
    ) AS fixture(code, name, capacity) LOOP
        IF EXISTS (SELECT 1 FROM subject_classes WHERE class_code = f.code
                   AND (teacher_id IS DISTINCT FROM v_teacher OR subject_term_id <> v_subject_term)) THEN
            RAISE EXCEPTION 'Fixture class code % already belongs to another context', f.code;
        END IF;
        INSERT INTO subject_classes(subject_term_id, teacher_id, class_code, class_name, capacity, registration_enabled, status)
        SELECT v_subject_term, v_teacher, f.code, f.name, f.capacity, FALSE, 'ACTIVE'
        WHERE NOT EXISTS (SELECT 1 FROM subject_classes WHERE class_code = f.code);
        SELECT class_id INTO v_class FROM subject_classes WHERE class_code = f.code;
        INSERT INTO class_experts(class_id, subject_id, expert_id, assigned_at)
        SELECT v_class, v_subject, v_expert, v_now - INTERVAL '15 days'
        WHERE NOT EXISTS (SELECT 1 FROM class_experts WHERE class_id = v_class AND expert_id = v_expert);
    END LOOP;

    -- 16 active placements (A:8, B:8), plus one historical inactive placement in B.
    FOR n IN 1..17 LOOP
        v_email := 'quang.demo.student' || lpad(n::TEXT, 2, '0') || '@example.test';
        IF EXISTS (SELECT 1 FROM users WHERE lower(btrim(email)) = v_email
                   AND (role <> 'STUDENT' OR google_subject IS DISTINCT FROM 'quang-demo-google-' || n)) THEN
            RAISE EXCEPTION 'Fixture student email collision: %', v_email;
        END IF;
        INSERT INTO users(full_name, email, role, account_status, google_subject, created_at, updated_at)
        SELECT '[DEMO-QUANG] Sinh viên ' || lpad(n::TEXT, 2, '0'), v_email, 'STUDENT', 'ACTIVE',
               'quang-demo-google-' || n, v_now - INTERVAL '30 days', v_now - INTERVAL '30 days'
        WHERE NOT EXISTS (SELECT 1 FROM users WHERE lower(btrim(email)) = v_email);
        SELECT user_id INTO v_user FROM users WHERE lower(btrim(email)) = v_email;
        INSERT INTO subject_registrations(student_id, subject_term_id, registered_at)
        SELECT v_user, v_subject_term, v_now - INTERVAL '15 days'
        WHERE NOT EXISTS (SELECT 1 FROM subject_registrations WHERE student_id = v_user AND subject_term_id = v_subject_term);
        SELECT registration_id INTO v_registration FROM subject_registrations
        WHERE student_id = v_user AND subject_term_id = v_subject_term;
        v_code := CASE WHEN n <= 8 THEN 'DEMO-Q-SWP-A' ELSE 'DEMO-Q-SWP-B' END;
        SELECT class_id INTO v_class FROM subject_classes WHERE class_code = v_code;
        IF n <= 16 AND EXISTS (SELECT 1 FROM class_enrollments WHERE registration_id = v_registration
                              AND status = 'ACTIVE' AND class_id <> v_class) THEN
            RAISE EXCEPTION 'Fixture student % has been moved; preserve the existing placement', n;
        END IF;
        INSERT INTO class_enrollments(registration_id, class_id, status, enrollment_method, enrolled_at, deactivated_at)
        SELECT v_registration, v_class, CASE WHEN n = 17 THEN 'INACTIVE' ELSE 'ACTIVE' END,
               CASE WHEN n = 1 THEN 'INVITATION' WHEN n % 2 = 0 THEN 'IMPORT' ELSE 'MANUAL' END,
               CASE WHEN n = 1 THEN v_now - INTERVAL '5 hours' ELSE v_now - INTERVAL '14 days' END,
               CASE WHEN n = 17 THEN v_now - INTERVAL '2 days' ELSE NULL END
        WHERE NOT EXISTS (SELECT 1 FROM class_enrollments WHERE registration_id = v_registration AND class_id = v_class);
    END LOOP;

    -- Two pending invitations per populated class, one expired and one cancelled history row.
    FOR f IN SELECT * FROM (VALUES
        ('DEMO-Q-SWP-A', 'a01', 'PENDING'), ('DEMO-Q-SWP-A', 'a02', 'PENDING'),
        ('DEMO-Q-SWP-B', 'b01', 'PENDING'), ('DEMO-Q-SWP-B', 'b02', 'PENDING'),
        ('DEMO-Q-SWP-A', 'old-a', 'EXPIRED'), ('DEMO-Q-SWP-B', 'old-b', 'CANCELLED')
    ) AS fixture(code, suffix, status) LOOP
        SELECT class_id INTO v_class FROM subject_classes WHERE class_code = f.code;
        INSERT INTO student_invitations(class_id, invited_by, email, full_name, token, status, sent_at, expires_at)
        SELECT v_class, v_teacher, 'quang.demo.invite.' || f.suffix || '@example.test',
               '[DEMO-QUANG] Người được mời ' || f.suffix, 'quang-demo-invitation-' || f.suffix, f.status,
               CASE WHEN f.status = 'EXPIRED' THEN v_now - INTERVAL '72 hours' ELSE v_now - INTERVAL '1 hour' END,
               CASE WHEN f.status = 'EXPIRED' THEN v_now - INTERVAL '24 hours' ELSE v_now + INTERVAL '47 hours' END
        WHERE NOT EXISTS (SELECT 1 FROM student_invitations WHERE token = 'quang-demo-invitation-' || f.suffix);
    END LOOP;
    SELECT e.enrollment_id, u.user_id, e.class_id INTO v_enrollment, v_user, v_class
    FROM class_enrollments e JOIN subject_registrations r USING(registration_id) JOIN users u ON u.user_id = r.student_id
    JOIN subject_classes c USING(class_id)
    WHERE u.email = 'quang.demo.student01@example.test' AND c.class_code = 'DEMO-Q-SWP-A';
    INSERT INTO student_invitations(class_id, invited_by, email, full_name, token, status,
                                    sent_at, expires_at, accepted_at, accepted_by, enrollment_id)
    SELECT v_class, v_teacher, 'quang.demo.student01@example.test', '[DEMO-QUANG] Sinh viên 01',
           'quang-demo-invitation-accepted', 'ACCEPTED', v_now - INTERVAL '6 hours', v_now + INTERVAL '42 hours',
           v_now - INTERVAL '5 hours', v_user, v_enrollment
    WHERE NOT EXISTS (SELECT 1 FROM student_invitations WHERE token = 'quang-demo-invitation-accepted');

    FOR f IN SELECT * FROM (VALUES
        ('[DEMO-QUANG] Mẫu báo cáo dự án', 'Trình bày mục tiêu, yêu cầu, thiết kế và kết quả kiểm thử của dự án.'),
        ('[DEMO-QUANG] Mẫu kiểm tra kiến thức', 'Đọc từng câu hỏi và chọn một đáp án đúng. Điểm tối đa của bài là 10.')
    ) AS fixture(title, instructions) LOOP
        INSERT INTO assignment_templates(subject_id, title, instructions, maximum_score)
        SELECT v_subject, f.title, f.instructions, 10
        WHERE NOT EXISTS (SELECT 1 FROM assignment_templates WHERE subject_id = v_subject AND title = f.title);
    END LOOP;

    FOR f IN SELECT * FROM (VALUES
        ('DEMO-Q-SWP-A', '[DEMO-QUANG] 01 Báo cáo yêu cầu SRS', 'REGULAR', 'ESSAY', 'PUBLISHED', 'NORMAL',
         E'Viết tài liệu đặc tả yêu cầu cho hệ thống quản lý thư viện.\nMô tả tác nhân, các use case chính và yêu cầu phi chức năng.\nBài nộp gồm báo cáo PDF và liên kết tài liệu của nhóm.',
         'Quản lý sách, độc giả, mượn và trả sách; chưa bao gồm thanh toán.',
         '[{"task_order":1,"title":"Xác định ít nhất 3 tác nhân"},{"task_order":2,"title":"Vẽ Use Case Diagram"},{"task_order":3,"title":"Mô tả 5 use case chính"},{"task_order":4,"title":"Liệt kê yêu cầu bảo mật và hiệu năng"}]'),
        ('DEMO-Q-SWP-A', '[DEMO-QUANG] 02 Thiết kế database & ERD', 'REGULAR', 'ESSAY', 'DRAFT', 'UNSET',
         E'Thiết kế cơ sở dữ liệu cho hệ thống quản lý thư viện.\nGiải thích từng bảng và quan hệ; chỉ rõ PK, FK và các ràng buộc duy nhất.\nĐưa ra ví dụ truy vấn lấy danh sách sách đang được mượn.',
         'Các bảng Book, Reader, Loan và LoanItem; xem xét chuẩn hóa đến 3NF.',
         '["Vẽ ERD", "Mô tả thuộc tính và khóa", "Viết DDL SQL", "Giải thích quy tắc nghiệp vụ"]'),
        ('DEMO-Q-SWP-A', '[DEMO-QUANG] 03 Trắc nghiệm UML', 'REGULAR', 'OBJECTIVE', 'PUBLISHED', 'NORMAL',
         'Chọn một đáp án đúng cho mỗi câu. Bài gồm 3 câu hỏi về Use Case, quan hệ include và Class Diagram.',
         'Kiến thức UML cơ bản trong phân tích và thiết kế hệ thống.', '["Đọc kỹ đề", "Trả lời đủ 3 câu"]'),
        ('DEMO-Q-SWP-A', '[DEMO-QUANG] 04 Luyện SQL 100%_cơ bản', 'PRACTICE', 'OBJECTIVE', 'PUBLISHED', 'NORMAL',
         E'Luyện tập SQL với 4 câu hỏi.\nBạn có tối đa 3 lượt làm, mỗi lượt 20 phút.\nTập trung vào SELECT, JOIN, GROUP BY và ràng buộc khóa ngoại.',
         'SQL cơ bản; kiểm tra tìm kiếm theo ký tự đặc biệt 100%_ trong tiêu đề.', '["Ôn SELECT và JOIN", "Làm đủ 4 câu"]'),
        ('DEMO-Q-SWP-A', '[DEMO-QUANG] 05 Java Quiz đã hủy', 'REGULAR', 'OBJECTIVE', 'CANCELLED', 'NORMAL',
         'Bài kiểm tra Java cũ đã bị hủy. Nội dung được giữ lại để giáo viên tra cứu.',
         'Kiểu dữ liệu và xử lý ngoại lệ trong Java.', '[]'),
        ('DEMO-Q-SWP-A', '[DEMO-QUANG] 06 Báo cáo kiến trúc quá hạn', 'REGULAR', 'ESSAY', 'PUBLISHED', 'OVERDUE',
         E'Mô tả kiến trúc Servlet – Service – DAO của dự án.\nVẽ luồng xử lý từ request đến database và phản hồi JSP.\nGiải thích vị trí kiểm tra quyền truy cập và xử lý lỗi.',
         'Luồng Assignment List và Assignment Detail; đây là mẫu bài đã quá hạn.', '["Vẽ sơ đồ kiến trúc", "Mô tả trách nhiệm từng lớp", "Nêu cách xử lý lỗi database"]'),
        ('DEMO-Q-SWP-A', '[DEMO-QUANG] 07 Quiz chưa tới giờ mở', 'REGULAR', 'OBJECTIVE', 'PUBLISHED', 'FUTURE',
         'Bài đã công bố nhưng thời điểm mở nằm trong tương lai. Dùng để kiểm tra sự khác nhau giữa Published và Available from.',
         'HTTP và kiểm soát truy cập.', '[]'),
        ('DEMO-Q-SWP-B', '[DEMO-QUANG] 08 Báo cáo Java Web', 'REGULAR', 'ESSAY', 'PUBLISHED', 'NORMAL',
         E'Xây dựng một trang danh sách sản phẩm bằng Servlet, JSP và JDBC.\nGiải thích cách request đi qua Controller, Service và DAO.\nBáo cáo cần có ảnh giao diện và các trường hợp kiểm thử.',
         'Danh sách sản phẩm, lọc theo tên và xử lý trường hợp không có dữ liệu.', '[{"title":"Vẽ luồng request"},{"title":"Mô tả truy vấn JDBC"},{"title":"Chụp ảnh giao diện"},{"title":"Lập bảng test case"}]'),
        ('DEMO-Q-SWP-B', '[DEMO-QUANG] 09 Quiz Servlet bản nháp', 'REGULAR', 'OBJECTIVE', 'DRAFT', 'UNSET',
         'Đề nháp gồm 3 câu về Servlet, HttpSession và JSP. Chưa thiết lập thời gian mở và hạn nộp.',
         'Jakarta Servlet và JSP.', '[]'),
        ('DEMO-Q-SWP-B', '[DEMO-QUANG] 10 Luyện JDBC bản nháp', 'PRACTICE', 'OBJECTIVE', 'DRAFT', 'UNSET',
         'Luyện JDBC với 3 câu hỏi. Dự kiến tối đa 5 lượt làm, mỗi lượt 20 phút; bài đang là bản nháp.',
         'PreparedStatement, transaction và quản lý connection.', '["Ôn JDBC", "Ôn transaction"]'),
        ('DEMO-Q-SWP-B', '[DEMO-QUANG] 11 Trắc nghiệm Git Workflow', 'REGULAR', 'OBJECTIVE', 'PUBLISHED', 'NORMAL',
         E'Chọn một đáp án đúng cho mỗi câu về branch, commit và merge.\nBài gồm 3 câu và có thời lượng 30 phút.',
         'Quy trình làm việc nhóm với Git và Pull Request.', '[]'),
        ('DEMO-Q-SWP-B', '[DEMO-QUANG] 12 Đề nháp chưa có câu hỏi', 'REGULAR', 'OBJECTIVE', 'DRAFT', 'UNSET',
         'Đây là đề nháp mới tạo và chưa có câu hỏi. Dùng để kiểm tra thông báo No questions have been added yet.',
         NULL, '[]')
    ) AS fixture(code, title, purpose, response, status, timing, instructions, scope, tasks) LOOP
        SELECT class_id INTO v_class FROM subject_classes WHERE class_code = f.code;
        SELECT template_id INTO v_template FROM assignment_templates WHERE subject_id = v_subject
          AND title = CASE WHEN f.response = 'ESSAY' THEN '[DEMO-QUANG] Mẫu báo cáo dự án' ELSE '[DEMO-QUANG] Mẫu kiểm tra kiến thức' END
        ORDER BY template_id LIMIT 1;
        INSERT INTO assignments(class_id, template_id, title, assignment_purpose, response_type, instructions,
                                scope_text, task_checklist, maximum_score, status, available_from, due_at,
                                time_limit_minutes, attempt_limit, published_at, cancelled_at)
        SELECT v_class, v_template, f.title, f.purpose, f.response, f.instructions, f.scope, f.tasks::JSONB, 10, f.status,
            CASE WHEN f.timing = 'UNSET' THEN NULL WHEN f.timing = 'FUTURE' THEN v_now + INTERVAL '2 days'
                 WHEN f.timing = 'OVERDUE' THEN v_now - INTERVAL '6 days'
                 WHEN f.status = 'CANCELLED' THEN v_now - INTERVAL '4 days' ELSE v_now - INTERVAL '2 days' END,
            CASE WHEN f.timing = 'UNSET' THEN NULL WHEN f.timing = 'OVERDUE' THEN v_now - INTERVAL '1 day' ELSE v_now + INTERVAL '7 days' END,
            CASE WHEN f.response = 'OBJECTIVE' THEN CASE WHEN f.purpose = 'PRACTICE' THEN 20 ELSE 30 END ELSE NULL END,
            CASE WHEN f.purpose = 'PRACTICE' THEN CASE WHEN f.title LIKE '[DEMO-QUANG] 10 %' THEN 5 ELSE 3 END ELSE NULL END,
            CASE WHEN f.status = 'DRAFT' THEN NULL WHEN f.timing = 'OVERDUE' THEN v_now - INTERVAL '7 days'
                 WHEN f.status = 'CANCELLED' THEN v_now - INTERVAL '5 days' ELSE v_now - INTERVAL '3 days' END,
            CASE WHEN f.status = 'CANCELLED' THEN v_now - INTERVAL '1 day' ELSE NULL END
        WHERE NOT EXISTS (SELECT 1 FROM assignments WHERE class_id = v_class AND title = f.title);
    END LOOP;

    -- Correct option is always A in these explicit demo fixtures; Teacher Detail shows the label.
    FOR v_question_fixture IN SELECT * FROM (VALUES
        ('03', 1, 'Use Case Diagram mô tả điều gì?', 3.0, 'Chức năng hệ thống và tương tác với tác nhân', 'Cấu trúc bảng database', 'Chi tiết triển khai thuật toán'),
        ('03', 2, 'Quan hệ include trong UML được dùng khi nào?', 3.0, 'Một use case luôn sử dụng hành vi chung của use case khác', 'Hai lớp kế thừa nhau', 'Một thuộc tính cho phép NULL'),
        ('03', 3, 'Class Diagram thể hiện nội dung nào?', 4.0, 'Lớp, thuộc tính, phương thức và quan hệ', 'Chỉ thứ tự gửi HTTP request', 'Chỉ dữ liệu mẫu trong database'),
        ('04', 1, 'Câu lệnh nào đọc dữ liệu từ một bảng?', 2.5, 'SELECT', 'INSERT', 'DROP'),
        ('04', 2, 'INNER JOIN trả về những dòng nào?', 2.5, 'Những dòng khớp điều kiện nối ở cả hai bảng', 'Toàn bộ dòng của bảng bên trái', 'Chỉ những dòng không khớp'),
        ('04', 3, 'GROUP BY có tác dụng gì?', 2.5, 'Nhóm các dòng để tính tổng hợp', 'Đổi tên bảng', 'Xóa dữ liệu trùng'),
        ('04', 4, 'Foreign key giúp đảm bảo điều gì?', 2.5, 'Tính toàn vẹn tham chiếu giữa các bảng', 'Mọi cột đều có giá trị khác nhau', 'Truy vấn luôn chạy nhanh hơn'),
        ('05', 1, 'Kiểu nào trong Java biểu diễn giá trị true/false?', 5.0, 'boolean', 'int', 'String'),
        ('05', 2, 'Khối nào dùng để xử lý ngoại lệ?', 5.0, 'catch', 'package', 'import'),
        ('07', 1, 'HTTP 401 thường biểu thị điều gì?', 5.0, 'Yêu cầu chưa được xác thực hợp lệ', 'Yêu cầu thành công', 'Tài nguyên đã bị chuyển vĩnh viễn'),
        ('07', 2, 'Việc kiểm tra giáo viên có phụ trách lớp nên đặt ở đâu?', 5.0, 'Phía server trước khi trả dữ liệu', 'Chỉ trong CSS', 'Chỉ ở nút View trên trình duyệt'),
        ('09', 1, 'Servlet thường nhận HTTP GET bằng phương thức nào?', 3.0, 'doGet', 'getConnection', 'executeQuery'),
        ('09', 2, 'HttpSession dùng để làm gì?', 3.0, 'Lưu trạng thái liên quan tới phiên người dùng', 'Tạo bảng database', 'Định nghĩa CSS'),
        ('09', 3, 'JSP trong kiến trúc MVC thường đảm nhiệm phần nào?', 4.0, 'Hiển thị giao diện', 'Quản lý connection pool', 'Lưu mật khẩu vào database'),
        ('10', 1, 'PreparedStatement giúp ích gì?', 3.0, 'Truyền tham số truy vấn an toàn', 'Tự động tạo tài khoản', 'Tự động merge Git'),
        ('10', 2, 'commit() được dùng khi nào?', 3.0, 'Xác nhận các thay đổi của transaction', 'Hủy các thay đổi chưa commit', 'Đóng trình duyệt'),
        ('10', 3, 'try-with-resources giúp quản lý gì?', 4.0, 'Đóng các tài nguyên JDBC sau khi sử dụng', 'Chỉ đổi tên biến', 'Chỉ tạo HTML'),
        ('11', 1, 'Branch trong Git có ý nghĩa gì?', 3.0, 'Một nhánh phát triển để làm việc độc lập', 'Một bảng trong database', 'Một kiểu dữ liệu Java'),
        ('11', 2, 'Commit ghi lại điều gì?', 3.0, 'Một trạng thái thay đổi của repository', 'Mật khẩu của mọi thành viên', 'Toàn bộ lịch sử trình duyệt'),
        ('11', 3, 'Pull Request hỗ trợ việc gì?', 4.0, 'Review thay đổi trước khi tích hợp vào nhánh đích', 'Bỏ qua mọi xung đột', 'Tự động xóa nhánh master')
    ) AS fixture(number, question_order, question_text, points, correct_text, wrong_b, wrong_c) LOOP
        SELECT assignment_id INTO v_assignment FROM assignments a JOIN subject_classes c USING(class_id)
        WHERE c.teacher_id = v_teacher AND c.class_code IN ('DEMO-Q-SWP-A','DEMO-Q-SWP-B')
          AND a.title LIKE '[DEMO-QUANG] ' || v_question_fixture.number || ' %';
        IF v_assignment IS NULL THEN RAISE EXCEPTION 'Missing demo question assignment %', v_question_fixture.number; END IF;
        INSERT INTO questions(assignment_id, question_order, question_text, maximum_points, options)
        SELECT v_assignment, v_question_fixture.question_order, v_question_fixture.question_text, v_question_fixture.points,
            jsonb_build_array(jsonb_build_object('option_id','A','option_text',v_question_fixture.correct_text,'is_correct',TRUE),
                              jsonb_build_object('option_id','B','option_text',v_question_fixture.wrong_b,'is_correct',FALSE),
                              jsonb_build_object('option_id','C','option_text',v_question_fixture.wrong_c,'is_correct',FALSE))
        WHERE NOT EXISTS (SELECT 1 FROM questions WHERE assignment_id = v_assignment AND question_order = v_question_fixture.question_order);
    END LOOP;

    -- Dashboard counts all 16 demo attempts, including unfinished/expired/void attempts.
    FOR f IN SELECT * FROM (VALUES
        ('01', 1, 1, 'SUBMITTED', 0), ('01', 2, 1, 'GRADED', 0), ('01', 3, 1, 'RELEASED', 0),
        ('01', 4, 1, 'IN_PROGRESS', 0), ('06', 5, 1, 'EXPIRED', 0),
        ('04', 1, 1, 'RELEASED', 240), ('04', 1, 2, 'SUBMITTED', 180),
        ('04', 2, 1, 'IN_PROGRESS', 0), ('04', 3, 1, 'GRADED', 120),
        ('05', 6, 1, 'VOID', 0), ('03', 7, 1, 'SUBMITTED', 0), ('03', 8, 1, 'GRADED', 0),
        ('08', 9, 1, 'SUBMITTED', 0), ('08', 10, 1, 'RELEASED', 0),
        ('11', 11, 1, 'GRADED', 0), ('11', 12, 1, 'SUBMITTED', 0)
    ) AS fixture(number, student_number, attempt, status, minutes_ago) LOOP
        SELECT a.* INTO v_assignment_row FROM assignments a JOIN subject_classes c USING(class_id)
        WHERE c.teacher_id = v_teacher AND c.class_code IN ('DEMO-Q-SWP-A','DEMO-Q-SWP-B')
          AND a.title LIKE '[DEMO-QUANG] ' || f.number || ' %';
        SELECT e.enrollment_id INTO v_enrollment FROM class_enrollments e
        JOIN subject_registrations r USING(registration_id) JOIN users u ON u.user_id = r.student_id
        WHERE e.class_id = v_assignment_row.class_id AND e.status = 'ACTIVE'
          AND u.email = 'quang.demo.student' || lpad(f.student_number::TEXT, 2, '0') || '@example.test';
        IF v_enrollment IS NULL THEN RAISE EXCEPTION 'Missing demo enrollment for submission'; END IF;
        IF EXISTS (SELECT 1 FROM submissions WHERE assignment_id = v_assignment_row.assignment_id
                   AND enrollment_id = v_enrollment AND attempt_number = f.attempt) THEN CONTINUE; END IF;
        v_started := CASE WHEN f.status = 'IN_PROGRESS' THEN v_now - INTERVAL '5 minutes'
                          WHEN f.status = 'EXPIRED' THEN v_assignment_row.due_at - INTERVAL '1 day'
                          WHEN f.status = 'VOID' THEN v_assignment_row.cancelled_at - INTERVAL '10 minutes'
                          ELSE v_now - make_interval(mins => CASE WHEN f.minutes_ago > 0 THEN f.minutes_ago ELSE 120 END) END;
        v_deadline := CASE WHEN v_assignment_row.time_limit_minutes IS NULL THEN v_assignment_row.due_at
                           ELSE LEAST(v_assignment_row.due_at, v_started + make_interval(mins => v_assignment_row.time_limit_minutes)) END;
        v_answer := '[]'::JSONB;
        IF v_assignment_row.response_type = 'OBJECTIVE' AND f.status IN ('SUBMITTED','GRADED','RELEASED') THEN
            SELECT COALESCE(jsonb_agg(jsonb_build_object('question_id',q.question_id,'selected_option_id','A',
                         'awarded_points',CASE WHEN f.status IN ('GRADED','RELEASED') THEN q.maximum_points ELSE NULL END)
                         ORDER BY q.question_order), '[]'::JSONB) INTO v_answer
            FROM questions q WHERE q.assignment_id = v_assignment_row.assignment_id;
        END IF;
        INSERT INTO submissions(assignment_id, enrollment_id, attempt_number, status, started_at, deadline_at,
                                 submitted_at, content, submission_link, answer, score, feedback, graded_by, graded_at, released_at)
        VALUES(v_assignment_row.assignment_id, v_enrollment, f.attempt, f.status, v_started, v_deadline,
            CASE WHEN f.status IN ('SUBMITTED','GRADED','RELEASED') THEN v_started + INTERVAL '10 minutes' ELSE NULL END,
            CASE WHEN v_assignment_row.response_type = 'ESSAY' AND f.status IN ('SUBMITTED','GRADED','RELEASED')
                 THEN E'[DEMO-QUANG] Bài làm mẫu: đã mô tả yêu cầu, thiết kế và các trường hợp kiểm thử.\nDữ liệu này dùng để kiểm tra số bài nộp trên Dashboard.' ELSE NULL END,
            CASE WHEN v_assignment_row.response_type = 'ESSAY' AND f.status IN ('SUBMITTED','GRADED','RELEASED')
                 THEN 'https://example.test/quang-demo/report-' || f.student_number ELSE NULL END,
            v_answer,
            CASE WHEN f.status IN ('GRADED','RELEASED') THEN CASE WHEN v_assignment_row.response_type = 'OBJECTIVE' THEN 10 ELSE 8.5 END ELSE NULL END,
            CASE WHEN f.status IN ('GRADED','RELEASED') THEN '[DEMO-QUANG] Bài làm đầy đủ; cần trình bày rõ hơn phần giải thích.' ELSE NULL END,
            CASE WHEN f.status IN ('GRADED','RELEASED') THEN v_teacher ELSE NULL END,
            CASE WHEN f.status IN ('GRADED','RELEASED') THEN v_started + INTERVAL '15 minutes' ELSE NULL END,
            CASE WHEN f.status = 'RELEASED' THEN v_started + INTERVAL '18 minutes' ELSE NULL END);
    END LOOP;
END
$seed$;
COMMIT;
