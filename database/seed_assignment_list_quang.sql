-- Additive fixtures for Quang's Assignment List. Run against the local ams_db only.
-- No existing records are changed/deleted. Re-running skips the same tagged fixtures.
-- Execute this script in one transaction (the supplied loader does this).
DO $$
DECLARE
    v_teacher BIGINT;
    v_class BIGINT;
    v_template BIGINT;
    v_subject BIGINT;
BEGIN
    -- Same deterministic Teacher selection as local Assignment preview.
    SELECT u.user_id INTO v_teacher FROM users u
    WHERE u.role = 'TEACHER' AND u.account_status = 'ACTIVE'
    ORDER BY EXISTS (SELECT 1 FROM subject_classes c WHERE c.teacher_id = u.user_id) DESC, u.user_id
    LIMIT 1;
    SELECT c.class_id, st.subject_id INTO v_class, v_subject
    FROM subject_classes c JOIN subject_terms st USING (subject_term_id)
    WHERE c.teacher_id = v_teacher AND c.status = 'ACTIVE'
    ORDER BY c.class_code, c.class_id LIMIT 1;
    IF v_class IS NULL THEN RAISE EXCEPTION 'An active class assigned to the preview Teacher is required'; END IF;
    SELECT template_id INTO v_template FROM assignment_templates
    WHERE subject_id = v_subject AND status = 'ACTIVE' ORDER BY template_id LIMIT 1;
    IF v_template IS NULL THEN RAISE EXCEPTION 'An active template for the class Subject is required'; END IF;

    INSERT INTO assignments (class_id, template_id, title, assignment_purpose, response_type,
        instructions, scope_text, task_checklist, maximum_score, status, available_from, due_at,
        time_limit_minutes, attempt_limit, published_at, cancelled_at)
    SELECT v_class, v_template, f.title, f.purpose, f.response,
        'Sample data for testing Assignment List. No student work is required.',
        'Assignment List filters, search, status and deadline display.', '[]'::jsonb,
        10, f.status,
        CASE WHEN f.status = 'DRAFT' THEN NULL ELSE CURRENT_TIMESTAMP - INTERVAL '2 days' END,
        CASE WHEN f.status = 'DRAFT' THEN NULL
             WHEN f.overdue THEN CURRENT_TIMESTAMP - INTERVAL '1 day'
             ELSE CURRENT_TIMESTAMP + INTERVAL '7 days' END,
        CASE WHEN f.response = 'OBJECTIVE' THEN 30 ELSE NULL END,
        CASE WHEN f.purpose = 'PRACTICE' THEN 3 ELSE NULL END,
        CASE WHEN f.status = 'DRAFT' THEN NULL ELSE CURRENT_TIMESTAMP - INTERVAL '3 days' END,
        CASE WHEN f.status = 'CANCELLED' THEN CURRENT_TIMESTAMP ELSE NULL END
    FROM (VALUES
        ('[TEST-QUANG] 01 Project Plan',            'REGULAR',  'ESSAY',     'DRAFT',     false),
        ('[TEST-QUANG] 02 Requirements Report',     'REGULAR',  'ESSAY',     'PUBLISHED', false),
        ('[TEST-QUANG] 03 Old Design Report',       'REGULAR',  'ESSAY',     'CANCELLED', false),
        ('[TEST-QUANG] 04 Database Quiz',           'REGULAR',  'OBJECTIVE', 'DRAFT',     false),
        ('[TEST-QUANG] 05 UML Quiz',                'REGULAR',  'OBJECTIVE', 'PUBLISHED', false),
        ('[TEST-QUANG] 06 Old Java Quiz',           'REGULAR',  'OBJECTIVE', 'CANCELLED', false),
        ('[TEST-QUANG] 07 SQL Practice',            'PRACTICE', 'OBJECTIVE', 'DRAFT',     false),
        ('[TEST-QUANG] 08 Java Practice',           'PRACTICE', 'OBJECTIVE', 'PUBLISHED', false),
        ('[TEST-QUANG] 09 UML Practice',            'PRACTICE', 'OBJECTIVE', 'CANCELLED', false),
        ('[TEST-QUANG] 10 Overdue Report',          'REGULAR',  'ESSAY',     'PUBLISHED', true),
        ('[TEST-QUANG] 11 100%_SQL Quiz',           'REGULAR',  'OBJECTIVE', 'DRAFT',     false),
        ('[TEST-QUANG] 12 Bài tập phân tích UML',   'REGULAR',  'ESSAY',     'DRAFT',     false)
    ) AS f(title, purpose, response, status, overdue)
    WHERE NOT EXISTS (SELECT 1 FROM assignments a WHERE a.class_id = v_class AND a.title = f.title);

    -- Published/cancelled Objective examples include valid question content for future detail testing.
    INSERT INTO questions (assignment_id, question_order, question_text, maximum_points, options)
    SELECT a.assignment_id, 1, 'Which statement correctly describes a primary key?', 10,
        '[{"option_id":"A","option_text":"Uniquely identifies a row","is_correct":true},
          {"option_id":"B","option_text":"Always allows duplicates","is_correct":false}]'::jsonb
    FROM assignments a
    WHERE a.class_id = v_class AND a.title LIKE '[TEST-QUANG] %' AND a.response_type = 'OBJECTIVE'
      AND NOT EXISTS (SELECT 1 FROM questions q WHERE q.assignment_id = a.assignment_id AND q.question_order = 1);
END $$;
