-- Teacher View Submission fixtures: 10 Objective questions and three mixed answer sheets.
-- Run in ams_db inside one transaction. Reuses class IT-Q-A1 and its existing Students.
-- Append only. A complete existing batch is preserved on rerun, including user test changes.
DO $quang_objective_ten$
DECLARE
    v_now TIMESTAMPTZ := date_trunc('minute', clock_timestamp());
    v_title CONSTANT TEXT := '[TEST-QUANG] Objective - 10 câu SQL và Web';
    v_class BIGINT;
    v_subject_term BIGINT;
    v_subject BIGINT;
    v_template BIGINT;
    v_assignment BIGINT;
    v_enrollments BIGINT[];
    v_students INTEGER;
    v_question_id BIGINT;
    v_question_ids BIGINT[] := ARRAY[]::BIGINT[];
    v_correct_options INTEGER[] := ARRAY[]::INTEGER[];
    v_options JSONB;
    v_answers JSONB;
    v_student_no INTEGER;
    v_question_no INTEGER;
    v_selected INTEGER;
    v_is_correct BOOLEAN;
    v_score INTEGER;
    v_expected_scores INTEGER[] := ARRAY[7, 5, 3];
    v_started_at TIMESTAMPTZ;
    q RECORD;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('AMS/QUANG/OBJECTIVE_TEN_FIXTURES/V1'));

    SELECT c.class_id, c.subject_term_id, st.subject_id
      INTO v_class, v_subject_term, v_subject
      FROM subject_classes c
      JOIN subject_terms st USING (subject_term_id)
      JOIN users u ON u.user_id = c.teacher_id
      WHERE c.class_id = 9 AND c.class_code = 'IT-Q-A1' AND c.status = 'ACTIVE'
        AND lower(btrim(u.email)) = 'quang.it.a@example.test'
        AND u.role = 'TEACHER' AND u.account_status = 'ACTIVE';
    IF v_class IS NULL THEN
        RAISE EXCEPTION 'Expected active IT-Q-A1 class 9 owned by the test Teacher';
    END IF;

    SELECT array_agg(e.enrollment_id ORDER BY lower(btrim(u.email))), count(DISTINCT lower(btrim(u.email)))
      INTO v_enrollments, v_students
      FROM class_enrollments e
      JOIN subject_registrations r USING (registration_id)
      JOIN users u ON u.user_id = r.student_id
      WHERE e.class_id = v_class AND r.subject_term_id = v_subject_term
        AND e.status = 'ACTIVE' AND r.status = 'ACTIVE'
        AND u.role = 'STUDENT' AND u.account_status = 'ACTIVE'
        AND lower(btrim(u.email)) IN (
            'quang.it.student01@example.test',
            'quang.it.student02@example.test',
            'quang.it.student03@example.test');
    IF cardinality(v_enrollments) IS DISTINCT FROM 3 OR v_students <> 3 THEN
        RAISE EXCEPTION 'Expected one existing active enrollment for each of IT Students 01, 02 and 03';
    END IF;

    IF (SELECT count(*) FROM assignment_templates
        WHERE subject_id = v_subject AND title = '[IT-QUANG] Mẫu bài tập Web' AND status = 'ACTIVE') <> 1 THEN
        RAISE EXCEPTION 'Expected one active Web assignment template for this class Subject';
    END IF;
    SELECT template_id INTO v_template FROM assignment_templates
      WHERE subject_id = v_subject AND title = '[IT-QUANG] Mẫu bài tập Web' AND status = 'ACTIVE';

    IF EXISTS (SELECT 1 FROM assignments WHERE title = v_title) THEN
        IF (SELECT count(*) FROM assignments WHERE title = v_title) <> 1
           OR EXISTS (SELECT 1 FROM assignments WHERE title = v_title
               AND (class_id <> v_class OR template_id <> v_template
                    OR assignment_purpose <> 'REGULAR' OR response_type <> 'OBJECTIVE'
                    OR maximum_score <> 10 OR scope_text IS NOT NULL
                    OR task_checklist IS DISTINCT FROM '[]'::jsonb
                    OR attempt_limit IS NOT NULL OR time_limit_minutes IS DISTINCT FROM 30)) THEN
            RAISE EXCEPTION 'Objective fixture title collision or changed structure; existing data is preserved';
        END IF;
        SELECT assignment_id INTO v_assignment FROM assignments WHERE title = v_title;
        IF (SELECT count(*) FROM questions WHERE assignment_id = v_assignment) <> 10
           OR EXISTS (SELECT 1 FROM questions WHERE assignment_id = v_assignment
               AND (question_order NOT BETWEEN 1 AND 10 OR maximum_points <> 1
                    OR jsonb_array_length(options) <> 4))
           OR (SELECT count(*) FROM submissions WHERE assignment_id = v_assignment) <> 3
           OR (SELECT count(DISTINCT enrollment_id) FROM submissions WHERE assignment_id = v_assignment) <> 3
           OR EXISTS (SELECT 1 FROM submissions WHERE assignment_id = v_assignment
               AND (NOT (enrollment_id = ANY(v_enrollments)) OR attempt_number <> 1)) THEN
            RAISE EXCEPTION 'Existing Objective fixture batch is incomplete or changed structurally; no rows were modified';
        END IF;
        RAISE NOTICE 'Ten-question Objective batch already exists; preserving all current data and timestamps';
        RETURN;
    END IF;

    INSERT INTO assignments(class_id, template_id, title, assignment_purpose, response_type,
        instructions, scope_text, task_checklist, maximum_score, status, available_from,
        due_at, time_limit_minutes, attempt_limit, published_at)
    VALUES(v_class, v_template, v_title, 'REGULAR', 'OBJECTIVE',
        'Bài gồm 10 câu về SQL và kiến trúc Web. Mỗi câu có một đáp án đúng và được 1 điểm. Thời gian làm bài: 30 phút.',
        NULL, '[]'::jsonb, 10, 'PUBLISHED', v_now - INTERVAL '7 days',
        v_now + INTERVAL '7 days', 30, NULL, v_now - INTERVAL '8 days')
    RETURNING assignment_id INTO v_assignment;

    -- Correct options use several positions; all four choices remain visible in the review.
    FOR q IN SELECT * FROM (VALUES
        (1, 'Lệnh SQL nào dùng để truy vấn dữ liệu từ một bảng?',
            ARRAY['SELECT', 'UPDATE', 'DELETE', 'INSERT'], 1),
        (2, 'Mục đích chính của PRIMARY KEY trong một bảng là gì?',
            ARRAY['Sắp xếp dữ liệu theo thứ tự chữ cái', 'Xác định duy nhất mỗi dòng trong bảng',
                  'Cho phép mọi dòng có cùng giá trị khóa', 'Tự động mã hóa mọi cột'], 2),
        (3, 'FOREIGN KEY thường dùng để làm gì?',
            ARRAY['Tự động sao lưu dữ liệu', 'Thay đổi tên bảng',
                  'Ràng buộc giá trị tham chiếu đến khóa của bảng liên quan', 'Tăng tốc mọi truy vấn trong cơ sở dữ liệu'], 3),
        (4, 'Mệnh đề nào lọc các dòng theo điều kiện trước khi nhóm dữ liệu trong SELECT?',
            ARRAY['ORDER BY', 'GROUP BY', 'SELECT', 'WHERE'], 4),
        (5, 'Mệnh đề nào chỉ định thứ tự sắp xếp kết quả truy vấn?',
            ARRAY['HAVING', 'ORDER BY', 'WHERE', 'FROM'], 2),
        (6, 'Lệnh SQL nào thêm một dòng dữ liệu mới vào bảng?',
            ARRAY['SELECT', 'DELETE', 'UPDATE', 'INSERT'], 4),
        (7, 'Trong ứng dụng Web dùng Servlet, nhiệm vụ điển hình của Servlet là gì?',
            ARRAY['Nhận HTTP request, gọi xử lý phù hợp và tạo hoặc chuyển tiếp response',
                  'Thay thế hoàn toàn hệ quản trị cơ sở dữ liệu',
                  'Lưu mọi dữ liệu vĩnh viễn trong biến cục bộ', 'Tự động viết toàn bộ giao diện trình duyệt'], 1),
        (8, 'Trong kiến trúc Servlet - Service - DAO, tầng Service chủ yếu xử lý gì?',
            ARRAY['Định dạng màu sắc bằng CSS', 'Hiển thị trực tiếp các hàng của bảng HTML',
                  'Quy tắc nghiệp vụ và phối hợp các thao tác cần thiết', 'Chỉ đổi tên các cột cơ sở dữ liệu'], 3),
        (9, 'Trong kiến trúc Servlet - Service - DAO, DAO chủ yếu phụ trách việc gì?',
            ARRAY['Quản lý bố cục và màu sắc của trang', 'Truy cập và ánh xạ dữ liệu từ cơ sở dữ liệu',
                  'Thay thế mọi kiểm tra quyền nghiệp vụ', 'Chạy mã JavaScript trong trình duyệt'], 2),
        (10, 'Phương thức HTTP nào được thiết kế để lấy biểu diễn của một tài nguyên mà không yêu cầu thay đổi trạng thái của nó?',
            ARRAY['POST', 'PUT', 'DELETE', 'GET'], 4)
    ) AS fixture(question_order, question_text, choices, correct_option) ORDER BY question_order LOOP
        SELECT jsonb_agg(jsonb_build_object(
            'option_id', chr(64 + choice_no::INTEGER),
            'option_order', choice_no,
            'option_text', choice_text,
            'is_correct', choice_no = q.correct_option) ORDER BY choice_no)
          INTO v_options
          FROM unnest(q.choices) WITH ORDINALITY AS choice(choice_text, choice_no);
        INSERT INTO questions(assignment_id, question_order, question_text, maximum_points, options)
        VALUES(v_assignment, q.question_order, q.question_text, 1, v_options)
        RETURNING question_id INTO v_question_id;
        v_question_ids := array_append(v_question_ids, v_question_id);
        v_correct_options := array_append(v_correct_options, q.correct_option);
    END LOOP;

    -- Student 01: Q1-7 correct. Student 02: odd questions correct. Student 03: Q2, Q5, Q9 correct.
    FOR v_student_no IN 1..3 LOOP
        v_answers := '[]'::jsonb;
        v_score := 0;
        FOR v_question_no IN 1..10 LOOP
            v_is_correct := CASE v_student_no
                WHEN 1 THEN v_question_no <= 7
                WHEN 2 THEN v_question_no % 2 = 1
                ELSE v_question_no IN (2, 5, 9)
            END;
            v_selected := CASE WHEN v_is_correct THEN v_correct_options[v_question_no]
                ELSE ((v_correct_options[v_question_no] - 1 + v_student_no) % 4) + 1 END;
            v_score := v_score + CASE WHEN v_is_correct THEN 1 ELSE 0 END;
            v_answers := v_answers || jsonb_build_array(jsonb_build_object(
                'question_id', v_question_ids[v_question_no],
                'selected_option_id', chr(64 + v_selected),
                'awarded_points', CASE WHEN v_is_correct THEN 1 ELSE 0 END));
        END LOOP;
        IF v_score <> v_expected_scores[v_student_no] THEN
            RAISE EXCEPTION 'Unexpected mixed-answer score; rolling back';
        END IF;

        v_started_at := v_now - INTERVAL '1 day' - make_interval(mins => v_student_no * 40);
        INSERT INTO submissions(assignment_id, enrollment_id, attempt_number, status,
            started_at, deadline_at, submitted_at, answer, score, graded_at, released_at)
        VALUES(v_assignment, v_enrollments[v_student_no], 1, 'RELEASED',
            v_started_at, v_started_at + INTERVAL '30 minutes', v_started_at + INTERVAL '15 minutes',
            v_answers, v_score, v_started_at + INTERVAL '16 minutes', v_started_at + INTERVAL '17 minutes');
    END LOOP;

    IF (SELECT count(*) FROM questions WHERE assignment_id = v_assignment) <> 10
       OR (SELECT sum(maximum_points) FROM questions WHERE assignment_id = v_assignment) <> 10
       OR (SELECT count(*) FROM submissions WHERE assignment_id = v_assignment AND status = 'RELEASED') <> 3
       OR EXISTS (SELECT 1 FROM submissions WHERE assignment_id = v_assignment
           AND (jsonb_array_length(answer) <> 10 OR score <= 0 OR score >= 10)) THEN
        RAISE EXCEPTION 'Ten-question mixed-answer fixture is incomplete; rolling back';
    END IF;
END
$quang_objective_ten$;

-- Find the generated IDs; do not assume sequence values:
-- SELECT a.assignment_id, s.submission_id, u.email, s.score, a.maximum_score
-- FROM assignments a JOIN submissions s USING (assignment_id)
-- JOIN class_enrollments e USING (enrollment_id)
-- JOIN subject_registrations r USING (registration_id)
-- JOIN users u ON u.user_id = r.student_id
-- WHERE a.title = '[TEST-QUANG] Objective - 10 câu SQL và Web'
-- ORDER BY u.email;
