-- Append test answers to Quang's existing published Objective assignments.
-- No UPDATE/DELETE. Run together with seed_objective_10_questions_quang.sql in one transaction.
-- Two Students answer each assignment differently; a one-question quiz has one correct
-- Student and one incorrect Student. Multi-question quizzes contain both outcomes per attempt.
DO $objective_mixed$
DECLARE
    v_teacher BIGINT;
    v_class BIGINT;
    v_term BIGINT;
    v_start TIMESTAMPTZ;
    v_deadline TIMESTAMPTZ;
    v_answers JSONB;
    v_score NUMERIC(10,2);
    v_option TEXT;
    v_correct BOOLEAN;
    v_existing BIGINT;
    a RECORD;
    student RECORD;
    q RECORD;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('AMS/QUANG/OBJECTIVE_MIXED/V1'));
    SELECT c.class_id,c.subject_term_id,u.user_id INTO v_class,v_term,v_teacher
    FROM subject_classes c JOIN users u ON u.user_id=c.teacher_id
    WHERE c.class_id=9 AND c.class_code='IT-Q-A1' AND c.status='ACTIVE'
      AND u.email='quang.it.a@example.test' AND u.role='TEACHER' AND u.account_status='ACTIVE';
    IF v_class IS NULL THEN RAISE EXCEPTION 'Expected the active Quang test Teacher and IT-Q-A1 class'; END IF;

    IF (SELECT count(*) FROM assignments WHERE class_id=v_class
        AND title IN ('[IT] Quiz Alpha','[IT] Quiz Beta','[TEST-QUANG] Submission 02 - Luyện tập SQL')) <> 3 THEN
        RAISE EXCEPTION 'Expected exactly three existing Objective fixture assignments';
    END IF;
    IF (SELECT count(*) FROM class_enrollments e JOIN subject_registrations r USING(registration_id)
        JOIN users u ON u.user_id=r.student_id WHERE e.class_id=v_class AND r.subject_term_id=v_term
        AND e.status='ACTIVE' AND r.status='ACTIVE' AND u.role='STUDENT' AND u.account_status='ACTIVE'
        AND u.email IN ('quang.it.student03@example.test','quang.it.student04@example.test')) <> 2 THEN
        RAISE EXCEPTION 'Expected two existing active test Student enrollments';
    END IF;

    FOR a IN SELECT * FROM assignments WHERE class_id=v_class
        AND title IN ('[IT] Quiz Alpha','[IT] Quiz Beta','[TEST-QUANG] Submission 02 - Luyện tập SQL')
        ORDER BY assignment_id FOR SHARE LOOP
        IF a.response_type<>'OBJECTIVE' OR a.status<>'PUBLISHED'
            OR a.available_from IS NULL OR a.due_at IS NULL OR a.published_at IS NULL
            OR (SELECT count(*) FROM questions WHERE assignment_id=a.assignment_id)
                <> (CASE a.title WHEN '[IT] Quiz Alpha' THEN 3 WHEN '[IT] Quiz Beta' THEN 1 ELSE 2 END)
            OR (SELECT sum(maximum_points) FROM questions WHERE assignment_id=a.assignment_id) IS DISTINCT FROM a.maximum_score THEN
            RAISE EXCEPTION 'Objective fixture % has changed; no existing rows are modified',a.assignment_id;
        END IF;
        FOR student IN
            SELECT e.enrollment_id,e.enrolled_at,r.registered_at,u.email
            FROM class_enrollments e JOIN subject_registrations r USING(registration_id)
            JOIN users u ON u.user_id=r.student_id
            WHERE e.class_id=v_class AND r.subject_term_id=v_term
              AND e.status='ACTIVE' AND r.status='ACTIVE' AND u.role='STUDENT' AND u.account_status='ACTIVE'
              AND u.email IN ('quang.it.student03@example.test','quang.it.student04@example.test')
            ORDER BY u.email
        LOOP
            v_answers:='[]'::jsonb;
            v_score:=0;
            FOR q IN SELECT * FROM questions WHERE assignment_id=a.assignment_id ORDER BY question_order LOOP
                IF jsonb_array_length(q.options)<2
                    OR (SELECT count(*) FROM jsonb_array_elements(q.options) o WHERE o->>'is_correct'='true')<>1
                    OR EXISTS (SELECT 1 FROM jsonb_array_elements(q.options) o
                        WHERE jsonb_typeof(o->'is_correct') IS DISTINCT FROM 'boolean'
                           OR nullif(btrim(o->>'option_id'),'') IS NULL)
                    OR (SELECT count(DISTINCT o->>'option_id') FROM jsonb_array_elements(q.options) o) <> jsonb_array_length(q.options) THEN
                    RAISE EXCEPTION 'Question % has an invalid answer key',q.question_id;
                END IF;
                v_correct:=(q.question_order % 2 = CASE WHEN student.email='quang.it.student03@example.test' THEN 1 ELSE 0 END);
                SELECT o->>'option_id' INTO v_option FROM jsonb_array_elements(q.options) WITH ORDINALITY AS x(o,n)
                WHERE (o->>'is_correct')::boolean=v_correct ORDER BY n LIMIT 1;
                v_answers:=v_answers||jsonb_build_array(jsonb_build_object('question_id',q.question_id,
                    'selected_option_id',v_option,'awarded_points',CASE WHEN v_correct THEN q.maximum_points ELSE 0 END));
                v_score:=v_score+CASE WHEN v_correct THEN q.maximum_points ELSE 0 END;
            END LOOP;

            SELECT submission_id INTO v_existing FROM submissions
            WHERE assignment_id=a.assignment_id AND enrollment_id=student.enrollment_id AND attempt_number=1;
            IF v_existing IS NOT NULL THEN
                IF NOT EXISTS (SELECT 1 FROM submissions WHERE submission_id=v_existing
                    AND status='RELEASED' AND answer=v_answers AND score=v_score AND graded_by IS NULL) THEN
                    RAISE EXCEPTION 'Existing Student attempt collides with the mixed-answer fixture; preserving it';
                END IF;
                CONTINUE;
            END IF;
            IF EXISTS (SELECT 1 FROM submissions WHERE assignment_id=a.assignment_id AND enrollment_id=student.enrollment_id) THEN
                RAISE EXCEPTION 'Student already has other attempts; no new conflicting work is added';
            END IF;

            -- Historical test attempts fall inside the original published window and enrollment.
            v_start:=greatest(a.available_from,a.published_at,student.enrolled_at,student.registered_at)+INTERVAL '1 hour';
            v_deadline:=least(a.due_at,v_start+make_interval(mins=>coalesce(a.time_limit_minutes,30)));
            IF v_start+INTERVAL '5 minutes'>v_deadline OR v_start+INTERVAL '6 minutes'>clock_timestamp() THEN
                RAISE EXCEPTION 'No valid historical test window for assignment %',a.assignment_id;
            END IF;
            INSERT INTO submissions(assignment_id,enrollment_id,attempt_number,status,started_at,deadline_at,
                submitted_at,answer,score,graded_at,released_at)
            VALUES(a.assignment_id,student.enrollment_id,1,'RELEASED',v_start,v_deadline,
                v_start+INTERVAL '5 minutes',v_answers,v_score,v_start+INTERVAL '5 minutes',v_start+INTERVAL '5 minutes');
        END LOOP;
    END LOOP;
END
$objective_mixed$;
