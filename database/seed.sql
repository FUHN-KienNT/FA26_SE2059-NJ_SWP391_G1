-- Minimal coherent development data. Staff hashes are explicit non-login placeholders.
-- Replace them through the future credential provisioning flow before authentication testing.
INSERT INTO users (full_name, email, role, password_hash) VALUES
  ('AMS Administrator', 'admin@example.test', 'ADMIN', 'DEMO_HASH_NOT_A_PASSWORD'),
  ('Primary Expert', 'expert@example.test', 'EXPERT', 'DEMO_HASH_NOT_A_PASSWORD'),
  ('Subject Teacher', 'teacher@example.test', 'TEACHER', 'DEMO_HASH_NOT_A_PASSWORD');

INSERT INTO users (full_name, email, role, google_subject) VALUES
  ('Sample Student', 'student@example.test', 'STUDENT', 'demo-google-subject-student');

INSERT INTO academic_terms (term_code, term_name, starts_at, ends_at, is_current, is_active) VALUES
  ('FALL2026', 'Fall 2026', TIMESTAMPTZ '2026-10-01 00:00:00+00', TIMESTAMPTZ '2027-01-31 23:59:59+00', TRUE, TRUE);

INSERT INTO subjects (subject_code, subject_name, description) VALUES
  ('SWP391', 'Software Development Project', 'Development seed Subject');

INSERT INTO subject_experts (subject_id, expert_id, responsibility_type)
SELECT s.subject_id, u.user_id, 'PRIMARY'
FROM subjects s JOIN users u ON u.email = 'expert@example.test'
WHERE s.subject_code = 'SWP391';

INSERT INTO teacher_subjects (teacher_id, subject_id)
SELECT u.user_id, s.subject_id
FROM users u CROSS JOIN subjects s
WHERE u.email = 'teacher@example.test' AND s.subject_code = 'SWP391';

INSERT INTO subject_terms (subject_id, term_id, registration_enabled, registration_opens_at, registration_closes_at)
SELECT s.subject_id, t.term_id, TRUE, TIMESTAMPTZ '2026-09-01 00:00:00+00', TIMESTAMPTZ '2026-09-25 00:00:00+00'
FROM subjects s CROSS JOIN academic_terms t
WHERE s.subject_code = 'SWP391' AND t.term_code = 'FALL2026';

INSERT INTO subject_classes (subject_term_id, teacher_id, class_code, class_name, capacity, registration_enabled)
SELECT st.subject_term_id, u.user_id, 'SE2059', 'SE2059', 30, TRUE
FROM subject_terms st
JOIN subjects s ON s.subject_id = st.subject_id
JOIN academic_terms t ON t.term_id = st.term_id
CROSS JOIN users u
WHERE s.subject_code = 'SWP391' AND t.term_code = 'FALL2026' AND u.email = 'teacher@example.test';

INSERT INTO class_experts (class_id, subject_id, expert_id)
SELECT c.class_id, st.subject_id, u.user_id
FROM subject_classes c JOIN subject_terms st ON st.subject_term_id = c.subject_term_id CROSS JOIN users u
WHERE c.class_code = 'SE2059' AND u.email = 'expert@example.test';

INSERT INTO subject_registrations (student_id, subject_term_id)
SELECT u.user_id, st.subject_term_id
FROM users u CROSS JOIN subject_terms st
JOIN subjects s ON s.subject_id = st.subject_id
JOIN academic_terms t ON t.term_id = st.term_id
WHERE u.email = 'student@example.test' AND s.subject_code = 'SWP391' AND t.term_code = 'FALL2026';

INSERT INTO class_enrollments (registration_id, class_id, enrollment_method)
SELECT r.registration_id, c.class_id, 'MANUAL'
FROM subject_registrations r
JOIN subject_terms st ON st.subject_term_id = r.subject_term_id
JOIN subject_classes c ON c.subject_term_id = st.subject_term_id
JOIN users u ON u.user_id = r.student_id
WHERE u.email = 'student@example.test' AND c.class_code = 'SE2059';

INSERT INTO assignment_templates (subject_id, title, instructions, maximum_score, created_at, updated_at)
SELECT subject_id, 'Project Proposal', 'Prepare and submit a project proposal.', 10.00,
       TIMESTAMPTZ '2026-09-01 00:00:00+00', TIMESTAMPTZ '2026-09-01 00:00:00+00'
FROM subjects WHERE subject_code = 'SWP391';

INSERT INTO assignments (class_id, template_id, title, assignment_purpose, response_type,
                         instructions, scope_text, task_checklist, maximum_score, status)
SELECT c.class_id, at.template_id, 'Project Proposal', 'REGULAR', 'ESSAY',
       at.instructions, 'Define the proposed system and delivery scope.',
       '[{"task_order":1,"title":"Prepare proposal"}]'::jsonb,
       at.maximum_score, 'DRAFT'
FROM subject_classes c
JOIN subject_terms st ON st.subject_term_id = c.subject_term_id
JOIN assignment_templates at ON at.subject_id = st.subject_id
WHERE c.class_code = 'SE2059' AND at.title = 'Project Proposal';
