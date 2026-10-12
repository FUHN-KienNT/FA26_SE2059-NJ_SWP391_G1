-- Additive local fixtures for Quang's Dashboard, Assignment List and Detail.
-- Run with psql -v ON_ERROR_STOP=1 against local ams_db; no existing rows are changed.
-- This is a reusable browsing fixture, not the independent exact-count Dashboard matrix.
-- Shared List A1 stays at seven rows; independent Detail examples belong to Teacher C.
-- Session expiry/revocation, reassignment, query faults and before/after time boundaries
-- require separate runtime setup. Dashboard historical/canary/count variants are not seeded.
-- Pending invitations expire naturally; rerunning never renews them or resets stored data.
-- Invalid checklist/options examples are intentional Draft JSON arrays, not Published content.
BEGIN;
SET LOCAL TIME ZONE 'UTC';
DO $seed$
DECLARE
    v_hash TEXT := 'pbkdf2-sha256$600000$zB4IMSpyuFJ9bYlizXRe3w==$zon4vAK/qaihYY6PlUYFEdmemaJR+VgFU96Bc36LKyM=';
    v_now TIMESTAMPTZ := CURRENT_TIMESTAMP;
    v_subject BIGINT; v_term BIGINT; v_offering BIGINT; v_expert BIGINT;
    v_teacher BIGINT; v_user BIGINT; v_class BIGINT; v_registration BIGINT;
    v_enrollment BIGINT; v_template BIGINT; v_assignment BIGINT;
    v_email TEXT; v_code TEXT; v_started TIMESTAMPTZ; v_deadline TIMESTAMPTZ;
    v_answer JSONB; a RECORD; f RECORD; n INTEGER;
BEGIN
    IF current_database() <> 'ams_db' THEN
        RAISE EXCEPTION 'This development fixture requires local ams_db';
    END IF;
    PERFORM pg_advisory_xact_lock(3912059, 20261005);
    FOR f IN SELECT * FROM (VALUES
        ('quang.it.a@example.test', '[IT-QUANG] Teacher A'),
        ('quang.it.b@example.test', '[IT-QUANG] Teacher B'),
        ('quang.it.detail@example.test', '[IT-QUANG] Teacher C'),
        ('quang.it.empty@example.test', '[IT-QUANG] Teacher without classes')
    ) AS x(email, name) LOOP
        IF EXISTS (SELECT 1 FROM users WHERE lower(btrim(email)) = f.email
                   AND (role <> 'TEACHER' OR account_status <> 'ACTIVE'
                        OR full_name NOT LIKE '[IT-QUANG] %' OR google_subject IS NOT NULL)) THEN
            RAISE EXCEPTION 'Unexpected fixture account context: %', f.email;
        END IF;
        INSERT INTO users(full_name, email, role, password_hash, created_at, updated_at)
        SELECT f.name, f.email, 'TEACHER', v_hash, v_now - INTERVAL '60 days', v_now - INTERVAL '60 days'
        WHERE NOT EXISTS (SELECT 1 FROM users WHERE lower(btrim(email)) = f.email);
    END LOOP;
    IF EXISTS (SELECT 1 FROM subjects WHERE subject_code = 'IT-Q-SUBJECT'
               AND (status <> 'ACTIVE' OR subject_name <> '[IT-QUANG] Kiểm thử ứng dụng Web')) THEN
        RAISE EXCEPTION 'Unexpected IT-Q-SUBJECT context';
    END IF;
    INSERT INTO subjects(subject_code, subject_name, description)
    SELECT 'IT-Q-SUBJECT', '[IT-QUANG] Kiểm thử ứng dụng Web', 'Isolated Quang integration fixtures'
    WHERE NOT EXISTS (SELECT 1 FROM subjects WHERE subject_code = 'IT-Q-SUBJECT');
    SELECT subject_id INTO v_subject FROM subjects WHERE subject_code = 'IT-Q-SUBJECT';
    IF EXISTS (SELECT 1 FROM academic_terms WHERE term_code = 'IT-Q-2026'
               AND (NOT is_active OR is_current OR term_name <> '[IT-QUANG] Học kỳ kiểm thử 2026'
                    OR starts_at <> TIMESTAMPTZ '2026-09-01 00:00:00+00'
                    OR ends_at <> TIMESTAMPTZ '2027-02-28 23:59:59+00')) THEN
        RAISE EXCEPTION 'Unexpected IT-Q-2026 context';
    END IF;
    INSERT INTO academic_terms(term_code, term_name, starts_at, ends_at, is_current, is_active)
    SELECT 'IT-Q-2026', '[IT-QUANG] Học kỳ kiểm thử 2026',
           TIMESTAMPTZ '2026-09-01 00:00:00+00', TIMESTAMPTZ '2027-02-28 23:59:59+00', FALSE, TRUE
    WHERE NOT EXISTS (SELECT 1 FROM academic_terms WHERE term_code = 'IT-Q-2026');
    SELECT term_id INTO v_term FROM academic_terms WHERE term_code = 'IT-Q-2026';
    IF EXISTS (SELECT 1 FROM subject_terms WHERE subject_id = v_subject AND term_id = v_term
               AND (registration_enabled OR registration_opens_at <> TIMESTAMPTZ '2026-09-01 00:00:00+00'
                    OR registration_closes_at <> TIMESTAMPTZ '2027-02-01 00:00:00+00')) THEN
        RAISE EXCEPTION 'Unexpected fixture offering context';
    END IF;
    INSERT INTO subject_terms(subject_id, term_id, registration_enabled, registration_opens_at, registration_closes_at)
    SELECT v_subject, v_term, FALSE, TIMESTAMPTZ '2026-09-01 00:00:00+00', TIMESTAMPTZ '2027-02-01 00:00:00+00'
    WHERE NOT EXISTS (SELECT 1 FROM subject_terms WHERE subject_id = v_subject AND term_id = v_term);
    SELECT subject_term_id INTO v_offering FROM subject_terms WHERE subject_id = v_subject AND term_id = v_term;
    SELECT se.expert_id INTO v_expert FROM subject_experts se JOIN users u ON u.user_id = se.expert_id
    WHERE se.subject_id = v_subject AND se.responsibility_type = 'PRIMARY' AND se.is_active
      AND u.role = 'EXPERT' AND u.account_status = 'ACTIVE' ORDER BY se.expert_id LIMIT 1;
    IF v_expert IS NULL THEN
        IF EXISTS (SELECT 1 FROM subject_experts WHERE subject_id = v_subject) THEN
            RAISE EXCEPTION 'Unexpected fixture Subject Expert context';
        END IF;
        SELECT user_id INTO v_expert FROM users WHERE role = 'EXPERT' AND account_status = 'ACTIVE'
        ORDER BY user_id LIMIT 1;
        IF v_expert IS NULL THEN RAISE EXCEPTION 'An existing ACTIVE Expert is required'; END IF;
        INSERT INTO subject_experts(subject_id, expert_id, responsibility_type, assigned_at)
        VALUES(v_subject, v_expert, 'PRIMARY', v_now - INTERVAL '30 days');
    END IF;
    FOR f IN SELECT * FROM (VALUES
        ('IT-Q-A1', 'quang.it.a@example.test', '[IT-QUANG] Bộ lọc và danh sách bài tập'),
        ('IT-Q-A2', 'quang.it.a@example.test', '[IT-QUANG] Lớp thứ hai'),
        ('IT-Q-B1', 'quang.it.b@example.test', '[IT-QUANG] Lớp của Teacher B'),
        ('IT-Q-C1', 'quang.it.detail@example.test', '[IT-QUANG] Chi tiết bài tập')
    ) AS x(code, email, name) LOOP
        SELECT user_id INTO v_teacher FROM users WHERE lower(btrim(email)) = f.email;
        IF EXISTS (SELECT 1 FROM teacher_subjects WHERE teacher_id = v_teacher AND subject_id = v_subject AND NOT is_active) THEN
            RAISE EXCEPTION 'Inactive fixture Teacher eligibility: %', f.email;
        END IF;
        INSERT INTO teacher_subjects(teacher_id, subject_id, assigned_at)
        SELECT v_teacher, v_subject, v_now - INTERVAL '30 days'
        WHERE NOT EXISTS (SELECT 1 FROM teacher_subjects WHERE teacher_id = v_teacher AND subject_id = v_subject);
        IF EXISTS (SELECT 1 FROM subject_classes WHERE class_code = f.code
                   AND (teacher_id IS DISTINCT FROM v_teacher OR subject_term_id <> v_offering
                        OR status <> 'ACTIVE' OR capacity <> 20)) THEN
            RAISE EXCEPTION 'Unexpected fixture Class context: %', f.code;
        END IF;
        INSERT INTO subject_classes(subject_term_id, teacher_id, class_code, class_name, capacity, registration_enabled)
        SELECT v_offering, v_teacher, f.code, f.name, 20, FALSE
        WHERE NOT EXISTS (SELECT 1 FROM subject_classes WHERE class_code = f.code);
        SELECT class_id INTO v_class FROM subject_classes WHERE class_code = f.code;
        IF EXISTS (SELECT 1 FROM class_experts WHERE class_id = v_class
                   AND (expert_id <> v_expert OR subject_id <> v_subject OR NOT is_active)) THEN
            RAISE EXCEPTION 'Unexpected fixture Class Expert context: %', f.code;
        END IF;
        INSERT INTO class_experts(class_id, subject_id, expert_id, assigned_at)
        SELECT v_class, v_subject, v_expert, v_now - INTERVAL '30 days'
        WHERE NOT EXISTS (SELECT 1 FROM class_experts WHERE class_id = v_class AND expert_id = v_expert);
    END LOOP;
    IF EXISTS (SELECT 1 FROM subject_classes c JOIN users u ON u.user_id = c.teacher_id
               WHERE lower(btrim(u.email)) = 'quang.it.empty@example.test') THEN
        RAISE EXCEPTION 'The empty fixture Teacher must have no Classes';
    END IF;
    INSERT INTO assignment_templates(subject_id, title, instructions, maximum_score)
    SELECT v_subject, '[IT-QUANG] Mẫu bài tập Web', 'Nội dung mẫu hiện tại; bài tập lưu bản nội dung riêng.', 20
    WHERE NOT EXISTS (SELECT 1 FROM assignment_templates WHERE subject_id = v_subject AND title = '[IT-QUANG] Mẫu bài tập Web');
    IF (SELECT count(*) FROM assignment_templates WHERE subject_id = v_subject AND title = '[IT-QUANG] Mẫu bài tập Web') <> 1 THEN
        RAISE EXCEPTION 'Ambiguous fixture Template';
    END IF;
    SELECT template_id INTO v_template FROM assignment_templates WHERE subject_id = v_subject AND title = '[IT-QUANG] Mẫu bài tập Web';
    FOR f IN SELECT * FROM (VALUES
        ('IT-Q-A1','[IT] Alpha Essay Draft','REGULAR','ESSAY','DRAFT','UNSET','','', '[]'),
        ('IT-Q-A1','[IT] Quiz Alpha','REGULAR','OBJECTIVE','PUBLISHED','LIST_UTC',E'Đọc kỹ từng câu hỏi.\nChọn một đáp án đúng cho mỗi câu.','Servlet, phiên đăng nhập và truy vấn an toàn.','[]'),
        ('IT-Q-A1','[IT] Quiz Beta','PRACTICE','OBJECTIVE','PUBLISHED','NORMAL','Luyện tập tối đa 3 lượt, mỗi lượt 20 phút.','Kiến thức cơ bản về cơ sở dữ liệu.','[]'),
        ('IT-Q-A1','[IT] Cancelled Quiz','REGULAR','OBJECTIVE','CANCELLED','NORMAL','Bài kiểm tra đã hủy; nội dung được giữ để tham khảo.','Kiến thức HTTP.','[]'),
        ('IT-Q-A1','[IT] SQL 100%_ Basics','REGULAR','OBJECTIVE','DRAFT','UNSET','','', '[]'),
        ('IT-Q-A1',repeat('L',200),'REGULAR','ESSAY','DRAFT','UNSET','Tiêu đề dài đúng 200 ký tự ASCII.','Kiểm tra giới hạn độ dài.','[]'),
        ('IT-Q-A1','[IT] Essay <script>alert(1)</script> & "review"','REGULAR','ESSAY','PUBLISHED','NORMAL',E'Trình bày kiến trúc ứng dụng Web.\nGiải thích vai trò Servlet, Service và DAO.','Báo cáo ngắn bằng tiếng Việt.','[]'),
        ('IT-Q-A2','[IT] Class Two Essay','REGULAR','ESSAY','DRAFT','UNSET','Mô tả một trường hợp kiểm thử của lớp thứ hai.',NULL,'[]'),
        ('IT-Q-B1','[IT] Foreign Quiz','REGULAR','OBJECTIVE','PUBLISHED','NORMAL','Câu hỏi riêng của Teacher B.','FOREIGN-B-CONTENT','[]'),
        ('IT-Q-C1','[IT-QUANG] C01 Báo cáo tiếng Việt','REGULAR','ESSAY','DRAFT','UNSET',E'Phân tích yêu cầu quản lý thư viện.\nVẽ sơ đồ luồng request.\nNêu các trường hợp kiểm thử.','Phạm vi: đăng nhập, xem danh sách và xem chi tiết.','[]'),
        ('IT-Q-C1','[IT-QUANG] C02 Trắc nghiệm Web','REGULAR','OBJECTIVE','PUBLISHED','NORMAL',E'Đọc từng câu hỏi.\nChọn một đáp án đúng.','HTTP và kiến trúc Web.','[]'),
        ('IT-Q-C1','[IT-QUANG] C03 Luyện tập SQL','PRACTICE','OBJECTIVE','PUBLISHED','NORMAL','Mỗi lượt 20 phút; tối đa 3 lượt.','SQL cơ bản.','[]'),
        ('IT-Q-C1','[IT-QUANG] C04 Bài đã hủy','REGULAR','OBJECTIVE','CANCELLED','NORMAL','Giữ nội dung bài đã hủy để giáo viên tham khảo.',NULL,'[]'),
        ('IT-Q-C1','[IT-QUANG] C05 Bài chưa mở','REGULAR','OBJECTIVE','PUBLISHED','FUTURE','Giáo viên vẫn xem được bài chưa đến giờ mở.',NULL,'[]'),
        ('IT-Q-C1','[IT-QUANG] C06 Báo cáo quá hạn','REGULAR','ESSAY','PUBLISHED','OVERDUE','Giáo viên vẫn xem được báo cáo quá hạn.',NULL,'[]'),
        ('IT-Q-C1','[IT-QUANG] C07 Giờ UTC cộng bảy','REGULAR','ESSAY','PUBLISHED','DETAIL_UTC','Kiểm tra giờ mở và hạn nộp qua nửa đêm.',NULL,'[]'),
        ('IT-Q-C1','[IT-QUANG] C08 Đề nháp rỗng','REGULAR','OBJECTIVE','DRAFT','UNSET','',NULL,'[]'),
        ('IT-Q-C1','[IT-QUANG] C09 Thứ tự công việc','REGULAR','ESSAY','DRAFT','UNSET','Kiểm tra thứ tự, tiêu đề và mô tả công việc.',NULL,'[{"task_order":3,"title":"Kiểm thử","description":"Chạy các trường hợp biên."},{"task_order":1,"title":"Phân tích","description":"Liệt kê yêu cầu chức năng."},{"task_order":2,"title":"Thiết kế","description":"Vẽ sơ đồ lớp."}]'),
        ('IT-Q-C1','[IT-QUANG] C10 Thứ tự lựa chọn','REGULAR','OBJECTIVE','DRAFT','UNSET','Thứ tự lựa chọn khác thứ tự phần tử JSON.',NULL,'[]'),
        ('IT-Q-C1','[IT-QUANG] C11 Công việc sai định dạng','REGULAR','ESSAY','DRAFT','UNSET','Giữ công việc hợp lệ và báo mục sai định dạng.',NULL,'[{"task_order":1,"title":"Công việc hợp lệ","description":"Nội dung này vẫn phải hiển thị."},42,{}]'),
        ('IT-Q-C1','[IT-QUANG] C12 Lựa chọn sai định dạng','REGULAR','OBJECTIVE','DRAFT','UNSET','Giữ lựa chọn hợp lệ và báo mục sai định dạng.',NULL,'[]'),
        ('IT-Q-C1','[IT-QUANG] C13 <b>Chi tiết</b>','REGULAR','OBJECTIVE','DRAFT','UNSET','Hiển thị nguyên văn <b>IT-QUANG-INSTRUCTIONS</b>.','Phạm vi <b>IT-QUANG-SCOPE</b>.','[]')
    ) AS x(code,title,purpose,response,status,timing,instructions,scope,tasks) LOOP
        SELECT class_id INTO v_class FROM subject_classes WHERE class_code = f.code;
        IF (SELECT count(*) FROM assignments WHERE class_id = v_class AND title = f.title) > 1 THEN
            RAISE EXCEPTION 'Ambiguous fixture Assignment: %', f.title;
        END IF;
        IF EXISTS (SELECT 1 FROM assignments WHERE class_id = v_class AND title = f.title AND template_id <> v_template) THEN
            RAISE EXCEPTION 'Unexpected fixture Template context: %', f.title;
        END IF;
        INSERT INTO assignments(class_id,template_id,title,assignment_purpose,response_type,instructions,scope_text,
                                task_checklist,maximum_score,status,available_from,due_at,time_limit_minutes,attempt_limit,published_at,cancelled_at)
        SELECT v_class,v_template,f.title,f.purpose,f.response,f.instructions,NULLIF(f.scope,''),f.tasks::JSONB,10,f.status,
            CASE f.timing WHEN 'UNSET' THEN NULL WHEN 'LIST_UTC' THEN TIMESTAMPTZ '2026-10-01 09:00:00+00'
                 WHEN 'DETAIL_UTC' THEN TIMESTAMPTZ '2026-10-05 09:00:00+00' WHEN 'FUTURE' THEN v_now + INTERVAL '2 days'
                 WHEN 'OVERDUE' THEN v_now - INTERVAL '6 days' ELSE v_now - INTERVAL '2 days' END,
            CASE f.timing WHEN 'UNSET' THEN NULL WHEN 'LIST_UTC' THEN TIMESTAMPTZ '2026-10-04 17:00:00+00'
                 WHEN 'DETAIL_UTC' THEN TIMESTAMPTZ '2026-10-05 18:30:00+00' WHEN 'OVERDUE' THEN v_now - INTERVAL '1 day'
                 ELSE v_now + INTERVAL '7 days' END,
            CASE WHEN f.response = 'OBJECTIVE' AND f.timing <> 'UNSET' THEN CASE WHEN f.purpose = 'PRACTICE' THEN 20 ELSE 30 END ELSE NULL END,
            CASE WHEN f.purpose = 'PRACTICE' THEN 3 ELSE NULL END,
            CASE WHEN f.status = 'DRAFT' THEN NULL WHEN f.timing = 'LIST_UTC' THEN TIMESTAMPTZ '2026-10-01 08:00:00+00'
                 WHEN f.timing = 'DETAIL_UTC' THEN TIMESTAMPTZ '2026-10-04 08:00:00+00'
                 WHEN f.timing = 'OVERDUE' THEN v_now - INTERVAL '7 days' ELSE v_now - INTERVAL '3 days' END,
            CASE WHEN f.status = 'CANCELLED' THEN v_now - INTERVAL '1 day' ELSE NULL END
        WHERE NOT EXISTS (SELECT 1 FROM assignments WHERE class_id = v_class AND title = f.title);
    END LOOP;
    -- Question insertion order deliberately differs from question_order for Quiz Alpha.
    FOR f IN SELECT * FROM (VALUES
        ('IT-Q-A1','[IT] Quiz Alpha',3,'PreparedStatement hỗ trợ điều gì?',4.0,'Truyền tham số truy vấn an toàn'),
        ('IT-Q-A1','[IT] Quiz Alpha',1,'Servlet nhận HTTP GET bằng phương thức nào?',3.0,'doGet'),
        ('IT-Q-A1','[IT] Quiz Alpha',2,'HttpSession lưu loại dữ liệu nào?',3.0,'Trạng thái của phiên người dùng'),
        ('IT-Q-A1','[IT] Quiz Beta',1,'Khóa chính của một bảng có tác dụng gì?',10.0,'Xác định duy nhất một dòng'),
        ('IT-Q-A1','[IT] Cancelled Quiz',1,'HTTP được dùng để làm gì?',10.0,'Trao đổi request và response trên Web'),
        ('IT-Q-B1','[IT] Foreign Quiz',1,'FOREIGN-B-QUESTION: nội dung riêng của Teacher B',10.0,'FOREIGN-B-CORRECT'),
        ('IT-Q-C1','[IT-QUANG] C02 Trắc nghiệm Web',3,'DAO đảm nhiệm công việc nào?',4.0,'Đọc và ghi dữ liệu'),
        ('IT-Q-C1','[IT-QUANG] C02 Trắc nghiệm Web',1,'Servlet đảm nhiệm công việc nào?',3.0,'Nhận và xử lý request'),
        ('IT-Q-C1','[IT-QUANG] C02 Trắc nghiệm Web',2,'JSP đảm nhiệm công việc nào?',3.0,'Hiển thị giao diện'),
        ('IT-Q-C1','[IT-QUANG] C03 Luyện tập SQL',1,'SELECT được dùng để làm gì?',10.0,'Đọc dữ liệu'),
        ('IT-Q-C1','[IT-QUANG] C04 Bài đã hủy',1,'Câu hỏi tham khảo của bài đã hủy',10.0,'Đáp án đúng của bài đã hủy'),
        ('IT-Q-C1','[IT-QUANG] C05 Bài chưa mở',1,'Câu hỏi của bài chưa đến giờ mở',10.0,'Đáp án đúng của bài chưa mở'),
        ('IT-Q-C1','[IT-QUANG] C10 Thứ tự lựa chọn',1,'Lựa chọn nào được đánh dấu đúng?',10.0,'Đáp án đúng A'),
        ('IT-Q-C1','[IT-QUANG] C12 Lựa chọn sai định dạng',1,'Một lựa chọn hợp lệ và các mục lỗi',10.0,'Lựa chọn hợp lệ'),
        ('IT-Q-C1','[IT-QUANG] C13 <b>Chi tiết</b>',1,'Câu hỏi <b>IT-QUANG-QUESTION</b>',10.0,'<b>IT-QUANG-OPTION</b>')
    ) AS x(code,title,question_order,text,points,correct_text) LOOP
        SELECT a2.assignment_id INTO v_assignment FROM assignments a2 JOIN subject_classes c USING(class_id)
        WHERE c.class_code = f.code AND a2.title = f.title;
        INSERT INTO questions(assignment_id,question_order,question_text,maximum_points,options)
        SELECT v_assignment,f.question_order,f.text,f.points,
            CASE WHEN f.title = '[IT-QUANG] C12 Lựa chọn sai định dạng' THEN
                '[{"option_id":"A","option_text":"Lựa chọn hợp lệ","is_correct":true},{"option_id":"B","is_correct":false},{"option_id":"C","option_text":"Thiếu giá trị boolean","is_correct":"invalid"}]'::JSONB
                 WHEN f.title = '[IT-QUANG] C13 <b>Chi tiết</b>' THEN
                '[{"option_id":"A","option_text":"<b>IT-QUANG-OPTION</b>","is_correct":true},{"option_id":"B","option_text":"Lựa chọn khác","is_correct":false}]'::JSONB
                 ELSE jsonb_build_array(
                    jsonb_build_object('option_id','C','option_order',3,'option_text','Đáp án sai C','is_correct',FALSE),
                    jsonb_build_object('option_id','A','option_order',1,'option_text',f.correct_text,'is_correct',TRUE),
                    jsonb_build_object('option_id','B','option_order',2,'option_text','Đáp án sai B','is_correct',FALSE)) END
        WHERE NOT EXISTS (SELECT 1 FROM questions WHERE assignment_id = v_assignment AND question_order = f.question_order);
    END LOOP;
    -- Four active placements in A1, two active and one retained inactive placement in A2.
    FOR n IN 1..7 LOOP
        v_email := 'quang.it.student' || lpad(n::TEXT,2,'0') || '@example.test';
        v_code := CASE WHEN n <= 4 THEN 'IT-Q-A1' ELSE 'IT-Q-A2' END;
        IF EXISTS (SELECT 1 FROM users WHERE lower(btrim(email)) = v_email
                   AND (role <> 'STUDENT' OR account_status <> 'ACTIVE' OR full_name NOT LIKE '[IT-QUANG] %'
                        OR google_subject IS DISTINCT FROM 'quang-it-student-' || n)) THEN
            RAISE EXCEPTION 'Unexpected fixture Student context: %', v_email;
        END IF;
        INSERT INTO users(full_name,email,role,google_subject,created_at,updated_at)
        SELECT '[IT-QUANG] Sinh viên ' || lpad(n::TEXT,2,'0'),v_email,'STUDENT','quang-it-student-' || n,
               v_now - INTERVAL '60 days',v_now - INTERVAL '60 days'
        WHERE NOT EXISTS (SELECT 1 FROM users WHERE lower(btrim(email)) = v_email);
        SELECT user_id INTO v_user FROM users WHERE lower(btrim(email)) = v_email;
        INSERT INTO subject_registrations(student_id,subject_term_id,registered_at)
        SELECT v_user,v_offering,v_now - INTERVAL '14 days'
        WHERE NOT EXISTS (SELECT 1 FROM subject_registrations WHERE student_id = v_user AND subject_term_id = v_offering);
        SELECT registration_id INTO v_registration FROM subject_registrations WHERE student_id = v_user AND subject_term_id = v_offering;
        SELECT class_id INTO v_class FROM subject_classes WHERE class_code = v_code;
        IF EXISTS (SELECT 1 FROM subject_registrations WHERE registration_id = v_registration AND status <> 'ACTIVE')
           OR EXISTS (SELECT 1 FROM class_enrollments WHERE registration_id = v_registration
                      AND (class_id <> v_class OR status <> CASE WHEN n = 7 THEN 'INACTIVE' ELSE 'ACTIVE' END))
           OR (SELECT count(*) FROM class_enrollments WHERE registration_id = v_registration) > 1 THEN
            RAISE EXCEPTION 'Unexpected fixture placement: %', v_email;
        END IF;
        INSERT INTO class_enrollments(registration_id,class_id,status,enrollment_method,enrolled_at,deactivated_at)
        SELECT v_registration,v_class,CASE WHEN n = 7 THEN 'INACTIVE' ELSE 'ACTIVE' END,'MANUAL',
               v_now - INTERVAL '13 days',CASE WHEN n = 7 THEN v_now - INTERVAL '2 days' ELSE NULL END
        WHERE NOT EXISTS (SELECT 1 FROM class_enrollments WHERE registration_id = v_registration AND class_id = v_class);
    END LOOP;
    SELECT user_id INTO v_teacher FROM users WHERE lower(btrim(email)) = 'quang.it.a@example.test';
    FOR f IN SELECT * FROM (VALUES ('IT-Q-A1','01','PENDING'),('IT-Q-A1','02','PENDING'),('IT-Q-A2','03','EXPIRED')) AS x(code,suffix,status) LOOP
        SELECT class_id INTO v_class FROM subject_classes WHERE class_code = f.code;
        IF EXISTS (SELECT 1 FROM student_invitations WHERE token = 'quang-it-invitation-' || f.suffix
                   AND (class_id <> v_class OR invited_by <> v_teacher OR email <> 'quang.it.invite' || f.suffix || '@example.test')) THEN
            RAISE EXCEPTION 'Unexpected fixture Invitation context: %', f.suffix;
        END IF;
        INSERT INTO student_invitations(class_id,invited_by,email,full_name,token,status,sent_at,expires_at)
        SELECT v_class,v_teacher,'quang.it.invite' || f.suffix || '@example.test','[IT-QUANG] Người được mời ' || f.suffix,
               'quang-it-invitation-' || f.suffix,f.status,
               CASE WHEN f.status = 'EXPIRED' THEN v_now - INTERVAL '3 days' ELSE v_now - INTERVAL '1 hour' END,
               CASE WHEN f.status = 'EXPIRED' THEN v_now - INTERVAL '1 day' ELSE v_now + INTERVAL '47 hours' END
        WHERE NOT EXISTS (SELECT 1 FROM student_invitations WHERE token = 'quang-it-invitation-' || f.suffix);
    END LOOP;
    FOR f IN SELECT * FROM (VALUES
        ('[IT] Essay <script>alert(1)</script> & "review"',1,1,'RELEASED',120),
        ('[IT] Quiz Beta',2,1,'SUBMITTED',240),('[IT] Quiz Beta',2,2,'GRADED',180)
    ) AS x(title,student_number,attempt,status,minutes_ago) LOOP
        SELECT a2.* INTO a FROM assignments a2 JOIN subject_classes c USING(class_id) WHERE c.class_code = 'IT-Q-A1' AND a2.title = f.title;
        SELECT e.enrollment_id INTO v_enrollment FROM class_enrollments e JOIN subject_registrations r USING(registration_id)
        JOIN users u ON u.user_id = r.student_id WHERE e.class_id = a.class_id
        AND u.email = 'quang.it.student' || lpad(f.student_number::TEXT,2,'0') || '@example.test';
        IF EXISTS (SELECT 1 FROM submissions WHERE assignment_id = a.assignment_id AND enrollment_id = v_enrollment AND attempt_number = f.attempt) THEN CONTINUE; END IF;
        v_started := a.available_from + INTERVAL '1 day' + make_interval(mins => 240 - f.minutes_ago);
        v_deadline := CASE WHEN a.time_limit_minutes IS NULL THEN a.due_at ELSE LEAST(a.due_at,v_started + make_interval(mins => a.time_limit_minutes)) END;
        SELECT COALESCE(jsonb_agg(jsonb_build_object('question_id',q.question_id,'selected_option_id','A',
                   'awarded_points',CASE WHEN f.status = 'GRADED' THEN q.maximum_points ELSE NULL END) ORDER BY q.question_order),'[]'::JSONB)
        INTO v_answer FROM questions q WHERE q.assignment_id = a.assignment_id;
        INSERT INTO submissions(assignment_id,enrollment_id,attempt_number,status,started_at,deadline_at,submitted_at,
                                content,answer,score,feedback,graded_by,graded_at,released_at)
        VALUES(a.assignment_id,v_enrollment,f.attempt,f.status,v_started,v_deadline,v_started + INTERVAL '10 minutes',
               CASE WHEN a.response_type = 'ESSAY' THEN E'[IT-QUANG] Báo cáo mẫu.\nĐã mô tả kiến trúc và trường hợp kiểm thử.' ELSE NULL END,
               v_answer,CASE WHEN f.status = 'RELEASED' THEN 8.5 WHEN f.status = 'GRADED' THEN 10 ELSE NULL END,
               CASE WHEN f.status IN ('GRADED','RELEASED') THEN '[IT-QUANG] Hoàn thành yêu cầu.' ELSE NULL END,
               CASE WHEN f.status IN ('GRADED','RELEASED') THEN v_teacher ELSE NULL END,
               CASE WHEN f.status IN ('GRADED','RELEASED') THEN v_started + INTERVAL '15 minutes' ELSE NULL END,
               CASE WHEN f.status = 'RELEASED' THEN v_started + INTERVAL '18 minutes' ELSE NULL END);
    END LOOP;
    FOR f IN SELECT * FROM (VALUES ('IT-Q-A1',7),('IT-Q-A2',1),('IT-Q-B1',1),('IT-Q-C1',13)) AS x(code,total) LOOP
        IF (SELECT count(*) FROM assignments a2 JOIN subject_classes c USING(class_id) WHERE c.class_code = f.code) <> f.total THEN
            RAISE EXCEPTION 'Unexpected Assignment count in %; preserve existing data', f.code;
        END IF;
    END LOOP;
    IF EXISTS (SELECT 1 FROM subject_classes c JOIN users u ON u.user_id = c.teacher_id
               WHERE lower(btrim(u.email)) IN ('quang.it.a@example.test','quang.it.b@example.test','quang.it.detail@example.test')
                 AND c.class_code NOT IN ('IT-Q-A1','IT-Q-A2','IT-Q-B1','IT-Q-C1')) THEN
        RAISE EXCEPTION 'Fixture Teachers have additional Classes; independent List counts would be ambiguous';
    END IF;
END
$seed$;
COMMIT;

-- Record these generated IDs in HTTP requests; no ID values are assumed by the seed.
SELECT user_id,full_name,email,role,account_status FROM users
WHERE lower(btrim(email)) IN ('quang.it.a@example.test','quang.it.b@example.test','quang.it.detail@example.test','quang.it.empty@example.test') ORDER BY email;
SELECT c.class_id,c.class_code,u.email AS teacher_email,c.capacity,
       (SELECT count(*) FROM class_enrollments e WHERE e.class_id=c.class_id AND e.status='ACTIVE') AS active_placements,
       (SELECT count(*) FROM student_invitations i WHERE i.class_id=c.class_id AND i.status='PENDING' AND i.expires_at>CURRENT_TIMESTAMP) AS pending_reservations,
       (SELECT count(*) FROM assignments a WHERE a.class_id=c.class_id) AS assignments,
       (SELECT count(*) FROM submissions s JOIN assignments a USING(assignment_id) WHERE a.class_id=c.class_id) AS submissions
FROM subject_classes c JOIN users u ON u.user_id=c.teacher_id WHERE c.class_code IN ('IT-Q-A1','IT-Q-A2','IT-Q-B1','IT-Q-C1') ORDER BY c.class_code;
SELECT c.class_code,a.assignment_id,a.title,a.assignment_purpose,a.response_type,a.status,
       (SELECT count(*) FROM questions q WHERE q.assignment_id=a.assignment_id) AS questions
FROM assignments a JOIN subject_classes c USING(class_id) WHERE c.class_code IN ('IT-Q-A1','IT-Q-A2','IT-Q-B1','IT-Q-C1') ORDER BY c.class_code,a.assignment_id;
