-- Minimal coherent development data. Expert hashes are valid PBKDF2 development credentials.
-- The Teacher hash remains an explicit non-login placeholder.
INSERT INTO users (full_name, email, role, password_hash) VALUES
  ('AMS Administrator', 'admin@example.test', 'ADMIN', 'pbkdf2-sha256$600000$AHucK3ITC+KZNQZzsXDEjw==$n/sBJih6oSX4xCpY9AdzSJ5QUuVWOqVkyXNfQR0wSrA='),
  ('Expert One', 'expert@example.test', 'EXPERT', 'pbkdf2-sha256$600000$tuvosEHmFDqFGhJRzGQIbA==$SahVow9lJOrYsyWmQg//sM0lWfdVocw+3/tsr8B0Yq0='),
  ('Expert Two', 'expert2@example.test', 'EXPERT', 'pbkdf2-sha256$600000$uQk8aGKCai8qITX/W8T0nQ==$RbY3ziZHArOE/6jYdO6xGYzbVy6krom+FUVIVHtnchM='),
  ('Expert Three', 'expert3@example.test', 'EXPERT', 'pbkdf2-sha256$600000$OyCsTd0dBNe6bmci2m0urQ==$8PgeW57KEcFtIZUnPgT6SDJDT7Y+IDLb6ej2f19SMAk='),
  ('Expert Four', 'expert4@example.test', 'EXPERT', 'pbkdf2-sha256$600000$PsiwctxSeOnGV5JCTgf3Lw==$Fc5Hl7XUpgRTA1OPiKAst4Bx4LQK/nQEvV/ZQR7h2T8='),
  ('Expert Five', 'expert5@example.test', 'EXPERT', 'pbkdf2-sha256$600000$uFrqGessGMTRDPewwRdYvA==$BourhT4DptGHraNBaVBFdp7jSGHGEapj3yXz2ctl4hg='),
  ('Subject Teacher', 'teacher@example.test', 'TEACHER', 'DEMO_HASH_NOT_A_PASSWORD');

INSERT INTO users (full_name, email, role, google_subject) VALUES
  ('Sample Student', 'student@example.test', 'STUDENT', 'demo-google-subject-student');

INSERT INTO academic_terms (term_code, term_name, starts_at, ends_at, is_current, is_active) VALUES
  ('FALL2026', 'Fall 2026', TIMESTAMPTZ '2026-10-01 00:00:00+00', TIMESTAMPTZ '2027-01-31 23:59:59+00', TRUE, TRUE),
  ('SPRING2027', 'Spring 2027', TIMESTAMPTZ '2027-02-01 00:00:00+00', TIMESTAMPTZ '2027-05-31 23:59:59+00', FALSE, TRUE);

INSERT INTO subjects (subject_code, subject_name, description, status) VALUES
  ('SWP391', 'Software Development Project', 'Development seed Subject', 'ACTIVE'),
  ('PRJ301', 'Java Web Application Development', 'Additional active Subject for development testing', 'ACTIVE'),
  ('DBI202', 'Introduction to Databases', 'Inactive Subject for development testing', 'INACTIVE');

INSERT INTO subject_experts (subject_id, expert_id, responsibility_type)
SELECT s.subject_id, u.user_id, assignment.responsibility_type
FROM subjects s
CROSS JOIN (VALUES
  ('expert@example.test', 'PRIMARY'),
  ('expert2@example.test', 'SUPPORTING')
) AS assignment(email, responsibility_type)
JOIN users u ON u.email = assignment.email
WHERE s.subject_code = 'SWP391';

INSERT INTO subject_experts (subject_id, expert_id, responsibility_type)
SELECT s.subject_id, u.user_id, 'PRIMARY'
FROM subjects s JOIN users u ON u.email = 'expert3@example.test'
WHERE s.subject_code = 'PRJ301';

INSERT INTO teacher_subjects (teacher_id, subject_id)
SELECT u.user_id, s.subject_id
FROM users u CROSS JOIN subjects s
WHERE u.email = 'teacher@example.test' AND s.subject_code = 'SWP391';

INSERT INTO subject_terms (subject_id, term_id, registration_enabled, registration_opens_at, registration_closes_at)
SELECT s.subject_id, t.term_id, TRUE, TIMESTAMPTZ '2026-09-01 00:00:00+00', TIMESTAMPTZ '2026-09-25 00:00:00+00'
FROM subjects s CROSS JOIN academic_terms t
WHERE s.subject_code = 'SWP391' AND t.term_code = 'FALL2026';

INSERT INTO subject_classes (subject_term_id, teacher_id, class_code, class_name, capacity, registration_enabled)
SELECT st.subject_term_id, u.user_id, fixture.class_code, fixture.class_name, fixture.capacity, TRUE
FROM subject_terms st
JOIN subjects s ON s.subject_id = st.subject_id
JOIN academic_terms t ON t.term_id = st.term_id
CROSS JOIN users u
JOIN (VALUES ('SE2059', 'SE2059', 30), ('SE2060', 'SE2060', 25))
  AS fixture(class_code, class_name, capacity) ON TRUE
WHERE s.subject_code = 'SWP391' AND t.term_code = 'FALL2026' AND u.email = 'teacher@example.test';

INSERT INTO class_experts (class_id, subject_id, expert_id)
SELECT c.class_id, st.subject_id, u.user_id
FROM subject_classes c JOIN subject_terms st ON st.subject_term_id = c.subject_term_id
JOIN (VALUES
  ('SE2059', 'expert@example.test'),
  ('SE2059', 'expert2@example.test'),
  ('SE2060', 'expert@example.test')
) AS fixture(class_code, email) ON fixture.class_code = c.class_code
JOIN users u ON u.email = fixture.email;

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
