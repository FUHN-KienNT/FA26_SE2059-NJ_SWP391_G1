-- PostgreSQL fixtures for Quang's Teacher Submission List/Detail (UC37).
-- Run in ams_db inside one transaction. Requires the existing IT-Q-A1 test class.
-- Append only: does not update/delete accounts, enrollments or existing assignments.
-- A complete existing batch is preserved on rerun, including any subsequent user actions.
DO $quang_submission$
DECLARE
    v_now TIMESTAMPTZ := date_trunc('minute', clock_timestamp());
    v_teacher BIGINT;
    v_class BIGINT;
    v_subject_term BIGINT;
    v_subject BIGINT;
    v_template BIGINT;
    v_enrollments BIGINT[];
    v_essay BIGINT;
    v_objective BIGINT;
    v_expired BIGINT;
    v_cancelled BIGINT;
    v_q1 BIGINT;
    v_q2 BIGINT;
    v_start TIMESTAMPTZ;
    v_answer JSONB;
    v_score NUMERIC(10,2);
    v_titles TEXT[] := ARRAY[
        '[TEST-QUANG] Submission 01 - Báo cáo Web',
        '[TEST-QUANG] Submission 02 - Luyện tập SQL',
        '[TEST-QUANG] Submission 03 - Bài đã hết hạn',
        '[TEST-QUANG] Submission 04 - Bài đã hủy'
    ];
    f RECORD;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('AMS/QUANG/SUBMISSION_FIXTURES/V1'));
    SELECT c.class_id, c.subject_term_id, st.subject_id, u.user_id
      INTO v_class, v_subject_term, v_subject, v_teacher
      FROM subject_classes c
      JOIN subject_terms st USING (subject_term_id)
      JOIN users u ON u.user_id = c.teacher_id
      WHERE c.class_code = 'IT-Q-A1' AND c.status = 'ACTIVE'
        AND lower(btrim(u.email)) = 'quang.it.a@example.test'
        AND u.role = 'TEACHER' AND u.account_status = 'ACTIVE';
    IF v_class IS NULL THEN RAISE EXCEPTION 'Expected active IT-Q-A1 class owned by the test Teacher'; END IF;

    SELECT array_agg(e.enrollment_id ORDER BY u.email) INTO v_enrollments
      FROM class_enrollments e JOIN subject_registrations r USING (registration_id)
      JOIN users u ON u.user_id = r.student_id
      WHERE e.class_id = v_class AND r.subject_term_id = v_subject_term
        AND e.status = 'ACTIVE' AND r.status = 'ACTIVE'
        AND u.role = 'STUDENT' AND u.account_status = 'ACTIVE'
        AND lower(btrim(u.email)) IN ('quang.it.student01@example.test',
            'quang.it.student02@example.test', 'quang.it.student03@example.test', 'quang.it.student04@example.test');
    IF cardinality(v_enrollments) IS DISTINCT FROM 4 THEN
        RAISE EXCEPTION 'Expected four existing active IT test Student enrollments';
    END IF;
    IF (SELECT count(*) FROM assignment_templates
        WHERE subject_id = v_subject AND title = '[IT-QUANG] Mẫu bài tập Web' AND status = 'ACTIVE') <> 1 THEN
        RAISE EXCEPTION 'Expected one active template for the class Subject';
    END IF;
    SELECT template_id INTO v_template FROM assignment_templates
      WHERE subject_id = v_subject AND title = '[IT-QUANG] Mẫu bài tập Web' AND status = 'ACTIVE';

    IF EXISTS (SELECT 1 FROM assignments WHERE title = ANY(v_titles)) THEN
        IF (SELECT count(*) FROM assignments WHERE title = ANY(v_titles)) <> 4
           OR (SELECT count(DISTINCT title) FROM assignments WHERE title = ANY(v_titles)) <> 4
           OR EXISTS (SELECT 1 FROM assignments WHERE title = ANY(v_titles)
                AND (class_id <> v_class OR template_id <> v_template OR maximum_score <> 20
                     OR response_type <> CASE WHEN title = v_titles[2] THEN 'OBJECTIVE' ELSE 'ESSAY' END
                     OR assignment_purpose <> CASE WHEN title = v_titles[2] THEN 'PRACTICE' ELSE 'REGULAR' END)) THEN
            RAISE EXCEPTION 'Seed title collision or incomplete batch; existing data is preserved';
        END IF;
        IF (SELECT count(*) FROM submissions s JOIN assignments a USING (assignment_id)
            WHERE a.title = ANY(v_titles)) <> 12
           OR EXISTS (SELECT 1 FROM assignments a WHERE a.title = ANY(v_titles)
                AND (SELECT count(*) FROM submissions s WHERE s.assignment_id = a.assignment_id)
                    <> CASE WHEN a.title IN (v_titles[1],v_titles[2]) THEN 4 ELSE 2 END)
           OR EXISTS (SELECT 1 FROM submissions s JOIN assignments a USING (assignment_id)
                WHERE a.title = ANY(v_titles) AND NOT (s.enrollment_id = ANY(v_enrollments))) THEN
            RAISE EXCEPTION 'Existing seed submissions changed structurally; no rows were modified';
        END IF;
        RAISE NOTICE 'Submission fixture batch already exists; preserving all current data and timestamps';
        RETURN;
    END IF;

    INSERT INTO assignments(class_id,template_id,title,assignment_purpose,response_type,instructions,
        scope_text,task_checklist,maximum_score,status,available_from,due_at,published_at)
    VALUES(v_class,v_template,v_titles[1],'REGULAR','ESSAY',
        E'Trình bày kiến trúc ứng dụng Web.\nGiải thích luồng Servlet - Service - DAO và kiểm tra quyền Teacher.',
        'Báo cáo về Assignment List và Submission List.',
        '[{"task_order":1,"title":"Mô tả luồng xem bài nộp","description":"Nêu dữ liệu đầu vào và quyền truy cập."},{"task_order":2,"title":"Liệt kê trường hợp kiểm thử","description":"Có bài chưa chấm, điểm 0 và bài đã công bố."}]'::jsonb,
        20,'PUBLISHED',v_now-INTERVAL '7 days',v_now+INTERVAL '7 days',v_now-INTERVAL '8 days')
    RETURNING assignment_id INTO v_essay;

    INSERT INTO assignments(class_id,template_id,title,assignment_purpose,response_type,instructions,
        maximum_score,status,available_from,due_at,time_limit_minutes,attempt_limit,published_at)
    VALUES(v_class,v_template,v_titles[2],'PRACTICE','OBJECTIVE',
        'Chọn một đáp án đúng cho mỗi câu. Tối đa 3 lượt, mỗi lượt 30 phút.',
        20,'PUBLISHED',v_now-INTERVAL '7 days',v_now+INTERVAL '7 days',30,3,v_now-INTERVAL '8 days')
    RETURNING assignment_id INTO v_objective;
    INSERT INTO questions(assignment_id,question_order,question_text,maximum_points,options)
    VALUES(v_objective,1,'Lệnh SQL nào dùng để đọc dữ liệu?',10,
        '[{"option_id":"A","option_order":1,"option_text":"SELECT","is_correct":true},{"option_id":"B","option_order":2,"option_text":"UPDATE","is_correct":false}]'::jsonb)
    RETURNING question_id INTO v_q1;
    INSERT INTO questions(assignment_id,question_order,question_text,maximum_points,options)
    VALUES(v_objective,2,'Ràng buộc nào liên kết dữ liệu giữa hai bảng?',10,
        '[{"option_id":"A","option_order":1,"option_text":"UNIQUE","is_correct":false},{"option_id":"B","option_order":2,"option_text":"FOREIGN KEY","is_correct":true}]'::jsonb)
    RETURNING question_id INTO v_q2;

    INSERT INTO assignments(class_id,template_id,title,assignment_purpose,response_type,instructions,
        scope_text,task_checklist,maximum_score,status,available_from,due_at,published_at)
    VALUES(v_class,v_template,v_titles[3],'REGULAR','ESSAY','Mô tả các bước xử lý một request HTTP.',
        'HTTP và vòng đời request.', '[{"task_order":1,"title":"Giải thích request và response"}]'::jsonb,
        20,'PUBLISHED',v_now-INTERVAL '7 days',v_now-INTERVAL '1 day',v_now-INTERVAL '8 days')
    RETURNING assignment_id INTO v_expired;
    INSERT INTO assignments(class_id,template_id,title,assignment_purpose,response_type,instructions,
        scope_text,task_checklist,maximum_score,status,available_from,due_at,published_at,cancelled_at)
    VALUES(v_class,v_template,v_titles[4],'REGULAR','ESSAY','Bài mẫu đã hủy; giữ bài nộp và kết quả cũ để tham khảo.',
        'Lưu lịch sử sau khi hủy Assignment.', '[{"task_order":1,"title":"Mô tả cách bảo toàn dữ liệu"}]'::jsonb,
        20,'CANCELLED',v_now-INTERVAL '7 days',v_now+INTERVAL '2 days',v_now-INTERVAL '8 days',v_now-INTERVAL '1 day')
    RETURNING assignment_id INTO v_cancelled;

    -- Four Regular Essay attempts: awaiting grading, graded/unreleased, released zero, unfinished.
    FOR f IN SELECT * FROM (VALUES
        (1,'SUBMITTED',NULL::NUMERIC,'Báo cáo đầy đủ, đang chờ giảng viên đánh giá.',3),
        (2,'GRADED',16::NUMERIC,'Đã giải thích tốt các tầng. Cần bổ sung trường hợp sai quyền.',2),
        (3,'RELEASED',0::NUMERIC,'Bài không đáp ứng yêu cầu; cần bổ sung nội dung phân tích.',1),
        (4,'IN_PROGRESS',NULL::NUMERIC,'Đang viết phần giới thiệu; đây là nội dung lưu tạm.',0)
    ) AS x(student_no,state,points,work,days_ago) LOOP
        v_start := CASE WHEN f.state = 'IN_PROGRESS' THEN v_now-INTERVAL '5 minutes'
                        ELSE v_now-make_interval(days=>f.days_ago) END;
        INSERT INTO submissions(assignment_id,enrollment_id,attempt_number,status,started_at,deadline_at,
            submitted_at,content,submission_link,answer,score,feedback,graded_by,graded_at,released_at)
        VALUES(v_essay,v_enrollments[f.student_no],1,f.state,v_start,v_now+INTERVAL '7 days',
            CASE WHEN f.state <> 'IN_PROGRESS' THEN v_start+INTERVAL '20 minutes' END,
            f.work||E'\nServlet nhận request, Service kiểm tra quyền và DAO truy vấn có tham số.',
            CASE WHEN f.student_no = 1 THEN 'https://example.com/' END,'[]'::jsonb,f.points,
            CASE WHEN f.state IN ('GRADED','RELEASED') THEN f.work END,
            CASE WHEN f.state IN ('GRADED','RELEASED') THEN v_teacher END,
            CASE WHEN f.state IN ('GRADED','RELEASED') THEN v_start+INTERVAL '30 minutes' END,
            CASE WHEN f.state = 'RELEASED' THEN v_start+INTERVAL '40 minutes' END);
    END LOOP;

    -- One Practice student's three completed attempts show improvement: 0, 10, 20.
    FOR f IN SELECT * FROM (VALUES (1,3,0),(2,2,10),(3,1,20)) AS x(attempt,days_ago,points) LOOP
        v_start := v_now-make_interval(days=>f.days_ago);
        v_score := f.points;
        v_answer := jsonb_build_array(
            jsonb_build_object('question_id',v_q1,'selected_option_id',CASE WHEN f.points=0 THEN 'B' ELSE 'A' END,
                'awarded_points',CASE WHEN f.points=0 THEN 0 ELSE 10 END),
            jsonb_build_object('question_id',v_q2,'selected_option_id',CASE WHEN f.points=20 THEN 'B' ELSE 'A' END,
                'awarded_points',CASE WHEN f.points=20 THEN 10 ELSE 0 END));
        INSERT INTO submissions(assignment_id,enrollment_id,attempt_number,status,started_at,deadline_at,
            submitted_at,answer,score,graded_at,released_at)
        VALUES(v_objective,v_enrollments[1],f.attempt,'RELEASED',v_start,v_start+INTERVAL '30 minutes',
            v_start+INTERVAL '10 minutes',v_answer,v_score,v_start+INTERVAL '10 minutes',v_start+INTERVAL '10 minutes');
    END LOOP;
    INSERT INTO submissions(assignment_id,enrollment_id,attempt_number,status,started_at,deadline_at,answer)
    VALUES(v_objective,v_enrollments[2],1,'IN_PROGRESS',v_now-INTERVAL '2 minutes',v_now+INTERVAL '28 minutes',
        jsonb_build_array(jsonb_build_object('question_id',v_q1,'selected_option_id','A','awarded_points',NULL),
                          jsonb_build_object('question_id',v_q2,'selected_option_id',NULL,'awarded_points',NULL)));

    -- A past-due assignment retains both an expired draft and a timely, ungraded submission.
    INSERT INTO submissions(assignment_id,enrollment_id,attempt_number,status,started_at,deadline_at,content)
    VALUES(v_expired,v_enrollments[1],1,'EXPIRED',v_now-INTERVAL '2 days',v_now-INTERVAL '1 day',
        'Nội dung viết dở đã hết hạn. Không có thời gian nộp và không có điểm.');
    INSERT INTO submissions(assignment_id,enrollment_id,attempt_number,status,started_at,deadline_at,submitted_at,content)
    VALUES(v_expired,v_enrollments[2],1,'SUBMITTED',v_now-INTERVAL '2 days',v_now-INTERVAL '1 day',
        v_now-INTERVAL '2 days'+INTERVAL '20 minutes','Bài đã nộp đúng hạn trước khi Assignment hết hạn, đang chờ chấm.');

    -- Cancellation voids unfinished work and preserves previously released results.
    INSERT INTO submissions(assignment_id,enrollment_id,attempt_number,status,started_at,deadline_at,content)
    VALUES(v_cancelled,v_enrollments[2],1,'VOID',v_now-INTERVAL '1 day'-INTERVAL '10 minutes',
        v_now+INTERVAL '2 days','Nội dung lưu tạm trước khi bài bị hủy; lượt làm không được tính là bài đã nộp.');
    v_start := v_now-INTERVAL '3 days';
    INSERT INTO submissions(assignment_id,enrollment_id,attempt_number,status,started_at,deadline_at,
        submitted_at,content,score,feedback,graded_by,graded_at,released_at)
    VALUES(v_cancelled,v_enrollments[1],1,'RELEASED',v_start,v_now+INTERVAL '2 days',v_start+INTERVAL '20 minutes',
        'Bài đã hoàn thành trước khi Assignment bị hủy. Kết quả đã công bố vẫn được giữ lại.',18,
        'Đã mô tả đúng yêu cầu bảo toàn lịch sử bài nộp.',v_teacher,v_start+INTERVAL '30 minutes',v_start+INTERVAL '40 minutes');

    IF (SELECT count(*) FROM submissions WHERE assignment_id IN (v_essay,v_objective,v_expired,v_cancelled)) <> 12
       OR (SELECT count(DISTINCT status) FROM submissions
           WHERE assignment_id IN (v_essay,v_objective,v_expired,v_cancelled)) <> 6 THEN
        RAISE EXCEPTION 'Fixture coverage is incomplete; rolling back';
    END IF;
END
$quang_submission$;
