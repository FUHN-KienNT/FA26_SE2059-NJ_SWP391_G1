-- PostgreSQL fixtures for Quang's Teacher View Submission: one Essay answer per task.
-- Run in ams_db inside one transaction. Reuses IT-Q-A1 and its existing test Students.
-- Append only: existing accounts, assignments, enrollments and submissions are preserved.
-- Essay content remains TEXT and contains a versioned document when answers are mapped:
-- {"format":"ams-essay-v1","answers":[{"task_order":1,"text":"..."}]}
-- The separate submissions.answer JSONB field remains [] (reserved for Objective answers).
-- Legacy plain text cannot be truthfully associated with individual tasks automatically.
DO $quang_essay_answers$
DECLARE
    v_now TIMESTAMPTZ := date_trunc('minute', clock_timestamp());
    v_title CONSTANT TEXT := '[TEST-QUANG] Essay - Câu hỏi và trả lời riêng';
    v_teacher BIGINT;
    v_class BIGINT;
    v_subject_term BIGINT;
    v_subject BIGINT;
    v_template BIGINT;
    v_assignment BIGINT;
    v_enrollments BIGINT[];
    v_students INTEGER;
    v_tasks JSONB := '[{"task_order":1,"title":"Mô tả luồng xem bài nộp","description":"Nêu dữ liệu đầu vào và quyền truy cập."},{"task_order":2,"title":"Liệt kê trường hợp kiểm thử","description":"Có bài chưa chấm, điểm 0 và bài đã công bố."}]'::jsonb;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('AMS/QUANG/ESSAY_ANSWERS_FIXTURES/V1'));

    SELECT c.class_id, c.subject_term_id, st.subject_id, u.user_id
      INTO v_class, v_subject_term, v_subject, v_teacher
      FROM subject_classes c
      JOIN subject_terms st USING (subject_term_id)
      JOIN users u ON u.user_id = c.teacher_id
      WHERE c.class_id = 9 AND c.class_code = 'IT-Q-A1' AND c.status = 'ACTIVE'
        AND lower(btrim(u.email)) = 'quang.it.a@example.test'
        AND u.role = 'TEACHER' AND u.account_status = 'ACTIVE';
    IF v_class IS NULL THEN
        RAISE EXCEPTION 'Expected active IT-Q-A1 class 9 owned by the test Teacher';
    END IF;

    SELECT array_agg(e.enrollment_id ORDER BY lower(btrim(u.email))), count(DISTINCT u.user_id)
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

    -- Only the existing Subject template is referenced; no template is created or edited.
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
                    OR assignment_purpose <> 'REGULAR' OR response_type <> 'ESSAY'
                    OR maximum_score <> 20 OR task_checklist IS DISTINCT FROM v_tasks)) THEN
            RAISE EXCEPTION 'Essay fixture title collision or changed task structure; existing data is preserved';
        END IF;
        SELECT assignment_id INTO v_assignment FROM assignments WHERE title = v_title;
        IF (SELECT count(*) FROM submissions WHERE assignment_id = v_assignment) <> 3
           OR (SELECT count(DISTINCT enrollment_id) FROM submissions WHERE assignment_id = v_assignment) <> 3
           OR EXISTS (SELECT 1 FROM submissions WHERE assignment_id = v_assignment
               AND (NOT (enrollment_id = ANY(v_enrollments)) OR attempt_number <> 1))
           OR EXISTS (SELECT 1 FROM questions WHERE assignment_id = v_assignment) THEN
            RAISE EXCEPTION 'Existing Essay fixture batch is incomplete or changed structurally; no rows were modified';
        END IF;
        -- Preserve all saved content, statuses, scores and timestamps after user testing.
        RAISE NOTICE 'Essay answer fixture batch already exists; preserving all current data and timestamps';
        RETURN;
    END IF;

    INSERT INTO assignments(class_id, template_id, title, assignment_purpose, response_type,
        instructions, scope_text, task_checklist, maximum_score, status,
        available_from, due_at, published_at)
    VALUES(v_class, v_template, v_title, 'REGULAR', 'ESSAY',
        'Trả lời riêng từng câu hỏi dưới đây.',
        'Báo cáo về Assignment List và Submission List.', v_tasks, 20, 'PUBLISHED',
        v_now - INTERVAL '7 days', v_now + INTERVAL '7 days', v_now - INTERVAL '8 days')
    RETURNING assignment_id INTO v_assignment;

    -- Student 01: a completed submission with two independently mapped answers.
    INSERT INTO submissions(assignment_id, enrollment_id, attempt_number, status,
        started_at, deadline_at, submitted_at, content, answer)
    VALUES(v_assignment, v_enrollments[1], 1, 'SUBMITTED',
        v_now - INTERVAL '2 days', v_now + INTERVAL '7 days',
        v_now - INTERVAL '2 days' + INTERVAL '35 minutes',
        jsonb_build_object('format', 'ams-essay-v1', 'answers', jsonb_build_array(
            jsonb_build_object('task_order', 1, 'text',
                E'Giáo viên mở Assignment List, chọn bài tập rồi nhấn View submissions. Request mang assignmentId; khi mở một bài nộp cụ thể, request có thêm submissionId. Bộ lọc tên sinh viên và trạng thái được gửi riêng nếu có.\n\nAuthFilter kiểm tra tài khoản đã đăng nhập và còn hoạt động. Service xác nhận giáo viên đang phụ trách lớp của bài tập; DAO chỉ lấy bài nộp thuộc đúng bài tập và lớp đó bằng truy vấn có tham số. Servlet chuyển dữ liệu sang JSP để hiển thị tên sinh viên, lượt làm, câu hỏi và câu trả lời.'),
            jsonb_build_object('task_order', 2, 'text',
                E'Với bài SUBMITTED chưa chấm, kiểm tra nội dung trả lời vẫn đọc được và điểm hiển thị Not graded. Với bài đã được chấm 0 điểm, kiểm tra giao diện hiển thị 0 / maximum_score, không coi số 0 là thiếu điểm.\n\nVới bài RELEASED, kiểm tra điểm, phản hồi và thời gian công bố được hiển thị đúng. Bổ sung trường hợp chưa đăng nhập, giáo viên không phụ trách lớp và submissionId không thuộc assignmentId; các trường hợp này phải bị từ chối và không làm thay đổi dữ liệu.'))
        )::text, '[]'::jsonb);

    -- Student 02: unfinished work maps only task 1; task 2 must remain visibly unanswered.
    INSERT INTO submissions(assignment_id, enrollment_id, attempt_number, status,
        started_at, deadline_at, content, answer)
    VALUES(v_assignment, v_enrollments[2], 1, 'IN_PROGRESS',
        v_now - INTERVAL '5 minutes', v_now + INTERVAL '7 days',
        jsonb_build_object('format', 'ams-essay-v1', 'answers', jsonb_build_array(
            jsonb_build_object('task_order', 1, 'text',
                E'Giáo viên chọn bài tập rồi mở danh sách bài nộp. Dữ liệu đầu vào gồm assignmentId và bộ lọc tên hoặc trạng thái.\n\nPhần đang viết: cần xác minh giáo viên phụ trách lớp trước khi hiển thị nội dung bài làm.'))
        )::text, '[]'::jsonb);

    -- Student 03: old plain-text format is retained as one unassigned answer block.
    -- The renderer must not guess that paragraph order matches task_order.
    INSERT INTO submissions(assignment_id, enrollment_id, attempt_number, status,
        started_at, deadline_at, submitted_at, content, answer)
    VALUES(v_assignment, v_enrollments[3], 1, 'SUBMITTED',
        v_now - INTERVAL '1 day', v_now + INTERVAL '7 days',
        v_now - INTERVAL '1 day' + INTERVAL '25 minutes',
        E'Bài nộp mẫu định dạng cũ: nội dung được lưu chung, chưa gắn riêng với từng câu hỏi.\n\nServlet nhận request; Service kiểm tra quyền giáo viên và DAO truy vấn bài nộp bằng tham số. Cần kiểm tra bài chưa chấm, bài có điểm 0, bài đã công bố và trường hợp giáo viên truy cập lớp không được phân công.',
        '[]'::jsonb);

    IF (SELECT count(*) FROM submissions WHERE assignment_id = v_assignment) <> 3
       OR (SELECT count(*) FROM submissions WHERE assignment_id = v_assignment AND status = 'SUBMITTED') <> 2
       OR (SELECT count(*) FROM submissions WHERE assignment_id = v_assignment AND status = 'IN_PROGRESS') <> 1 THEN
        RAISE EXCEPTION 'Essay answer fixture coverage is incomplete; rolling back';
    END IF;
END
$quang_essay_answers$;

-- Find the generated IDs without relying on sequence values:
-- SELECT a.assignment_id, s.submission_id, u.email, s.status
-- FROM assignments a JOIN submissions s USING (assignment_id)
-- JOIN class_enrollments e USING (enrollment_id)
-- JOIN subject_registrations r USING (registration_id)
-- JOIN users u ON u.user_id = r.student_id
-- WHERE a.title = '[TEST-QUANG] Essay - Câu hỏi và trả lời riêng'
-- ORDER BY u.email;
