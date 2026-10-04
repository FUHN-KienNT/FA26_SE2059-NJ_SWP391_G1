BEGIN;
-- Coherent DEVELOPMENT-ONLY data. Existing Admin/Expert credentials remain unchanged.
-- Teacher development login uses the existing application PBKDF2 mechanism.
INSERT INTO users (full_name, email, role, password_hash) VALUES
  ('AMS Administrator', 'admin@example.test', 'ADMIN', 'pbkdf2-sha256$600000$AHucK3ITC+KZNQZzsXDEjw==$n/sBJih6oSX4xCpY9AdzSJ5QUuVWOqVkyXNfQR0wSrA='),
  ('Expert One', 'expert@example.test', 'EXPERT', 'pbkdf2-sha256$600000$tuvosEHmFDqFGhJRzGQIbA==$SahVow9lJOrYsyWmQg//sM0lWfdVocw+3/tsr8B0Yq0='),
  ('Expert Two', 'expert2@example.test', 'EXPERT', 'pbkdf2-sha256$600000$uQk8aGKCai8qITX/W8T0nQ==$RbY3ziZHArOE/6jYdO6xGYzbVy6krom+FUVIVHtnchM='),
  ('Expert Three', 'expert3@example.test', 'EXPERT', 'pbkdf2-sha256$600000$OyCsTd0dBNe6bmci2m0urQ==$8PgeW57KEcFtIZUnPgT6SDJDT7Y+IDLb6ej2f19SMAk='),
  ('Expert Four', 'expert4@example.test', 'EXPERT', 'pbkdf2-sha256$600000$PsiwctxSeOnGV5JCTgf3Lw==$Fc5Hl7XUpgRTA1OPiKAst4Bx4LQK/nQEvV/ZQR7h2T8='),
  ('Expert Five', 'expert5@example.test', 'EXPERT', 'pbkdf2-sha256$600000$uFrqGessGMTRDPewwRdYvA==$BourhT4DptGHraNBaVBFdp7jSGHGEapj3yXz2ctl4hg='),
  ('Subject Teacher', 'teacher@example.test', 'TEACHER', 'pbkdf2-sha256$600000$cufKhZSQNwfLLvBhwiVCjw==$vq+eihcxTpjD2G+MpfSXk40x820RF+/8KJuWPspQlbw=');

INSERT INTO users (full_name, email, role, google_subject) VALUES
  ('Sample Student', 'student@example.test', 'STUDENT', 'demo-google-subject-student');

INSERT INTO academic_terms (term_code, term_name, starts_at, ends_at, is_current, is_active) VALUES
  ('FALL2026', 'Fall 2026', TIMESTAMPTZ '2026-10-01 00:00:00+00', TIMESTAMPTZ '2027-01-31 23:59:59+00', TRUE, TRUE),
  ('SPRING2027', 'Spring 2027', TIMESTAMPTZ '2027-02-01 00:00:00+00', TIMESTAMPTZ '2027-05-31 23:59:59+00', FALSE, FALSE);

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
JOIN (VALUES ('SE2059', 'SE2059', 25), ('SE2060', 'SE2060', 25))
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

-- DEVELOPMENT ONLY: deterministic fictional fixtures; never use these accounts in production.
-- Internal fixture accounts use PBKDF2 development credentials, not real secrets.
-- Added internal accounts: Demo@123; Subject Teacher: Teacher@123 (development only).
-- Users: additional experts, teachers and 26 fictional Google-identity students.
INSERT INTO users (full_name,email,role,account_status,password_hash) VALUES
 ('Expert Six','expert6@example.test','EXPERT','ACTIVE','pbkdf2-sha256$600000$0WW1q0eTbhV6jCblK/pJBQ==$aTLxOWebVGt5eZilwTMdPkfCSpFNHDgNV/u/KNd99lE='),
 ('Expert Seven','expert7@example.test','EXPERT','INACTIVE','pbkdf2-sha256$600000$0WW1q0eTbhV6jCblK/pJBQ==$aTLxOWebVGt5eZilwTMdPkfCSpFNHDgNV/u/KNd99lE='),
 ('Minh Nguyen','teacher2@example.test','TEACHER','ACTIVE','pbkdf2-sha256$600000$0WW1q0eTbhV6jCblK/pJBQ==$aTLxOWebVGt5eZilwTMdPkfCSpFNHDgNV/u/KNd99lE='),
 ('Linh Tran','teacher3@example.test','TEACHER','ACTIVE','pbkdf2-sha256$600000$0WW1q0eTbhV6jCblK/pJBQ==$aTLxOWebVGt5eZilwTMdPkfCSpFNHDgNV/u/KNd99lE='),
 ('An Pham','teacher4@example.test','TEACHER','INACTIVE','pbkdf2-sha256$600000$0WW1q0eTbhV6jCblK/pJBQ==$aTLxOWebVGt5eZilwTMdPkfCSpFNHDgNV/u/KNd99lE=');
INSERT INTO users (full_name,email,role,account_status,google_subject)
SELECT (ARRAY['Mai Nguyen','Bao Tran','Anh Le','Linh Pham','Minh Vo','Hanh Dang','Duy Bui','Quynh Do','Khoa Ho','Thao Vu','Nam Nguyen','Vy Tran','Phuc Le','Chi Pham','Huy Vo','Nhi Dang','Long Bui','Tram Do','Son Ho','Yen Vu','Dat Nguyen','Ngoc Tran','Tuan Le','Ha Pham','Cuong Dao','My Nguyen'])[n],
 'student'||lpad(n::text,2,'0')||'@example.test','STUDENT',CASE WHEN n>24 THEN 'INACTIVE' ELSE 'ACTIVE' END,'ams-demo-google-student-'||n
FROM generate_series(1,26) n;

-- Academic Terms: only FALL2026 is active/current; past and future records stay inactive.
INSERT INTO academic_terms (term_code,term_name,starts_at,ends_at,is_active,is_current) VALUES
 ('FALL2025','Fall 2025','2025-09-01+00','2025-12-31+00',false,false),
 ('SPRING2026','Spring 2026','2026-01-01+00','2026-04-30+00',false,false),
 ('SUMMER2026','Summer 2026','2026-05-01+00','2026-08-31+00',false,false),
 ('SUMMER2027','Summer 2027','2027-06-01+00','2027-08-31+00',false,false);

-- Subjects: a realistic mix of active offerings and inactive catalog entries.
INSERT INTO subjects(subject_code,subject_name,description,status) VALUES
 ('PRN232','Advanced Cross-Platform Application Programming','Build layered applications and APIs with integration testing.','ACTIVE'),
 ('ITE302','Ethics in Information Technology','Case studies on professional responsibility in computing.','ACTIVE'),
 ('MLN131','Marxist-Leninist Philosophy','Foundational philosophy and guided reading discussions.','INACTIVE'),
 ('HCM202','Ho Chi Minh Ideology','Historical perspectives and thematic seminars.','INACTIVE'),
 ('PRO192','Object-Oriented Programming','Objects, inheritance, collections and maintainable design.','ACTIVE'),
 ('FER202','Front-End Web Development with React','Accessible interfaces, state management and responsive layouts.','ACTIVE'),
 ('MAS291','Statistics and Probability','Probability models, inference and applied data analysis.','ACTIVE');

-- Subject Experts: preserve initial SWP391/PRJ301 responsibilities, complete the catalog.
INSERT INTO subject_experts(subject_id,expert_id,responsibility_type)
SELECT s.subject_id,u.user_id,'PRIMARY' FROM subjects s JOIN users u ON u.email='expert@example.test'
WHERE s.subject_code NOT IN ('SWP391','PRJ301');
INSERT INTO subject_experts(subject_id,expert_id,responsibility_type)
SELECT s.subject_id,u.user_id,'SUPPORTING' FROM subjects s JOIN users u ON u.email='expert2@example.test'
WHERE s.subject_code NOT IN ('SWP391');
INSERT INTO subject_experts(subject_id,expert_id,responsibility_type)
SELECT s.subject_id,u.user_id,'PRIMARY' FROM subjects s JOIN users u ON u.email='expert6@example.test'
WHERE s.subject_code IN ('FER202','MAS291');

-- Teacher Subjects: active teachers may teach multiple eligible subjects.
INSERT INTO teacher_subjects(teacher_id,subject_id)
SELECT u.user_id,s.subject_id FROM users u CROSS JOIN subjects s
WHERE u.role='TEACHER' AND u.account_status='ACTIVE'
AND NOT (u.email='teacher@example.test' AND s.subject_code='SWP391');

-- Subject Terms: dates are fixed and reproducible (not randomized or now-relative).
-- FALL windows include closed SWP391, open other subjects and upcoming MAS291.
INSERT INTO subject_terms(subject_id,term_id,registration_enabled,registration_opens_at,registration_closes_at)
SELECT s.subject_id,t.term_id,s.status='ACTIVE',
 CASE WHEN s.subject_code='MAS291' THEN '2026-11-01+00'::timestamptz ELSE '2026-09-01+00'::timestamptz END,
 CASE WHEN s.subject_code='MAS291' THEN '2026-11-30+00'::timestamptz ELSE '2026-10-31+00'::timestamptz END
FROM subjects s CROSS JOIN academic_terms t WHERE t.term_code='FALL2026' AND s.subject_code<>'SWP391';
INSERT INTO subject_terms(subject_id,term_id,registration_enabled,registration_opens_at,registration_closes_at)
SELECT s.subject_id,t.term_id,t.term_code='SPRING2027','2026-12-01+00','2027-01-25+00'
FROM subjects s CROSS JOIN academic_terms t WHERE s.status='ACTIVE' AND t.term_code='SPRING2027';
INSERT INTO subject_terms(subject_id,term_id,registration_enabled,registration_opens_at,registration_closes_at)
SELECT s.subject_id,t.term_id,false,'2026-04-01+00','2026-04-25+00'
FROM subjects s CROSS JOIN academic_terms t WHERE s.subject_code IN ('SWP391','PRN232','PRO192') AND t.term_code='SUMMER2026';

-- Classes: setup without a teacher, inactive historical classes, spare and occupied seats.
INSERT INTO subject_classes(subject_term_id,teacher_id,class_code,class_name,capacity,registration_enabled,status)
SELECT st.subject_term_id,CASE WHEN t.term_code='SPRING2027' THEN NULL ELSE u.user_id END,
 s.subject_code||'-'||t.term_code||'-01',s.subject_name||' — Group 01',
 CASE WHEN s.subject_code='PRO192' THEN 10 ELSE 30 END,
 st.registration_enabled,CASE WHEN s.status='INACTIVE' OR t.term_code='SUMMER2026' THEN 'INACTIVE' ELSE 'ACTIVE' END
FROM subject_terms st JOIN subjects s USING(subject_id) JOIN academic_terms t USING(term_id)
JOIN users u ON u.email='teacher@example.test'
WHERE NOT (s.subject_code='SWP391' AND t.term_code='FALL2026');
INSERT INTO subject_classes(subject_term_id,teacher_id,class_code,class_name,capacity,registration_enabled)
SELECT st.subject_term_id,u.user_id,s.subject_code||'-FALL2026-02',s.subject_name||' — Group 02',25,true
FROM subject_terms st JOIN subjects s USING(subject_id) JOIN academic_terms t USING(term_id)
JOIN users u ON u.email='teacher2@example.test'
WHERE t.term_code='FALL2026' AND s.subject_code IN ('PRN232','PRO192','FER202');

-- Class Experts always match an active responsibility for the class's own Subject.
INSERT INTO class_experts(class_id,subject_id,expert_id)
SELECT c.class_id,st.subject_id,se.expert_id
FROM subject_classes c JOIN subject_terms st USING(subject_term_id)
JOIN subject_experts se ON se.subject_id=st.subject_id AND se.is_active
WHERE se.responsibility_type='PRIMARY' AND NOT EXISTS(SELECT 1 FROM class_experts ce WHERE ce.class_id=c.class_id AND ce.expert_id=se.expert_id);

-- Registrations and Enrollments: students are enrolled only in their matching Subject Term.
INSERT INTO subject_registrations(student_id,subject_term_id,registered_at)
SELECT u.user_id,st.subject_term_id,'2026-09-20+00'
FROM users u CROSS JOIN subject_terms st JOIN subjects s USING(subject_id) JOIN academic_terms t USING(term_id)
WHERE u.email ~ '^student[0-9]+@example.test$' AND u.account_status='ACTIVE' AND s.status='ACTIVE' AND t.term_code='FALL2026'
AND substring(u.email from '[0-9]+')::int <= CASE s.subject_code WHEN 'SWP391' THEN 24 WHEN 'PRJ301' THEN 24 WHEN 'PRO192' THEN 10 WHEN 'PRN232' THEN 18 ELSE 8 END;
INSERT INTO class_enrollments(registration_id,class_id,enrollment_method,enrolled_at)
SELECT r.registration_id,c.class_id,CASE WHEN s.subject_code='SWP391' THEN 'IMPORT' ELSE 'SELF_REGISTRATION' END,'2026-09-20+00'
FROM subject_registrations r JOIN users u ON u.user_id=r.student_id JOIN subject_terms st USING(subject_term_id) JOIN subjects s USING(subject_id)
JOIN subject_classes c ON c.subject_term_id=st.subject_term_id
WHERE u.email ~ '^student[0-9]+@example.test$' AND (c.class_code='SE2059' OR c.class_code=s.subject_code||'-FALL2026-01');
INSERT INTO subject_registrations(student_id,subject_term_id,status,registered_at,deactivated_at)
SELECT u.user_id,st.subject_term_id,'INACTIVE','2026-04-15+00','2026-08-31+00'
FROM users u CROSS JOIN subject_terms st JOIN academic_terms t USING(term_id)
WHERE u.email IN ('student01@example.test','student02@example.test','student03@example.test') AND t.term_code='SUMMER2026';
INSERT INTO class_enrollments(registration_id,class_id,status,enrollment_method,enrolled_at,deactivated_at)
SELECT r.registration_id,c.class_id,'INACTIVE','MANUAL',r.registered_at,r.deactivated_at FROM subject_registrations r
JOIN subject_classes c ON c.subject_term_id=r.subject_term_id WHERE r.status='INACTIVE';

-- Invitations: synthetic, non-usable tokens; 48-hour expiry and accepted enrollment evidence.
INSERT INTO student_invitations(class_id,invited_by,email,full_name,token,status,sent_at,expires_at)
SELECT c.class_id,u.user_id,'invite-'||v.n||'@example.test','Development Guest '||v.n,'demo-not-a-real-token-'||v.n,v.status,v.sent,v.sent+interval '48 hours'
FROM subject_classes c JOIN users u ON u.email='teacher@example.test'
CROSS JOIN (VALUES(1,'PENDING','2026-10-04+00'::timestamptz),(2,'EXPIRED','2026-09-01+00'::timestamptz),(3,'CANCELLED','2026-09-01+00'::timestamptz)) v(n,status,sent)
WHERE c.class_code='SE2060';
INSERT INTO student_invitations(class_id,invited_by,email,full_name,token,status,sent_at,expires_at,accepted_at,accepted_by,enrollment_id)
SELECT e.class_id,teacher.user_id,u.email,u.full_name,'demo-accepted-token','ACCEPTED','2026-09-19+00','2026-09-21+00','2026-09-20+00',u.user_id,e.enrollment_id
FROM users u JOIN subject_registrations r ON r.student_id=u.user_id JOIN class_enrollments e USING(registration_id)
JOIN subject_classes c USING(class_id) JOIN users teacher ON teacher.email='teacher@example.test'
WHERE u.email='student01@example.test' AND c.class_code='SE2059';
UPDATE class_enrollments SET enrollment_method='INVITATION' WHERE enrollment_id=(SELECT enrollment_id FROM student_invitations WHERE status='ACCEPTED');

-- Overflow / Additional Class: lifecycle evidence without adding new tables or workflow logic.
INSERT INTO overflow_batches(source_class_id,imported_by,source_file_name,status,overflow_students,created_at)
SELECT c.class_id,u.user_id,'dev-overflow-'||n||'.csv',CASE n WHEN 1 THEN 'OPEN' WHEN 4 THEN 'COMPLETED' ELSE 'PROCESSING' END,
 jsonb_build_array(jsonb_build_object('email','overflow-'||n||'@example.test','full_name','Development Overflow '||n)),'2026-09-20+00'
FROM subject_classes c JOIN users u ON u.email='teacher@example.test' CROSS JOIN generate_series(1,4)n WHERE c.class_code='SE2059';
INSERT INTO additional_class_requests(overflow_batch_id,requested_by,preferred_teacher_id,willing_to_teach,overflow_context,status,requested_at,review_due_at,reviewed_by,reviewed_at,decision_reason,created_class_id,resolved_at)
SELECT b.overflow_batch_id,teacher.user_id,teacher.user_id,true,'Development overflow roster for SWP391',
 CASE b.source_file_name WHEN 'dev-overflow-1.csv' THEN 'PENDING_REVIEW' WHEN 'dev-overflow-2.csv' THEN 'APPROVED' WHEN 'dev-overflow-3.csv' THEN 'REJECTED' ELSE 'RESOLVED' END,
 '2026-09-20+00','2026-09-22+00',CASE WHEN b.source_file_name='dev-overflow-1.csv' THEN NULL ELSE expert.user_id END,
 CASE WHEN b.source_file_name='dev-overflow-1.csv' THEN NULL ELSE '2026-09-21+00'::timestamptz END,
 CASE WHEN b.source_file_name='dev-overflow-3.csv' THEN 'Existing class still has spare seats.' ELSE NULL END,
 CASE WHEN b.source_file_name='dev-overflow-4.csv' THEN c.class_id ELSE NULL END,
 CASE WHEN b.source_file_name='dev-overflow-4.csv' THEN '2026-09-21 12:00+00'::timestamptz ELSE NULL END
FROM overflow_batches b JOIN users teacher ON teacher.email='teacher@example.test' JOIN users expert ON expert.email='expert@example.test'
JOIN subject_classes c ON c.class_code='SE2060';
INSERT INTO additional_class_assignment_offers(request_id,teacher_id,offered_by,status,sent_at,expires_at,responded_at,response_note)
SELECT r.request_id,teacher.user_id,expert.user_id,v.status,'2026-09-21 01:00+00','2026-09-23 01:00+00',
 CASE WHEN v.status IN ('ACCEPTED','DECLINED') THEN '2026-09-21 02:00+00'::timestamptz ELSE NULL END,'Development offer lifecycle fixture'
FROM additional_class_requests r JOIN overflow_batches b USING(overflow_batch_id)
JOIN users teacher ON teacher.email='teacher@example.test' JOIN users expert ON expert.email='expert@example.test'
JOIN (VALUES('dev-overflow-2.csv','PENDING'),('dev-overflow-2.csv','DECLINED'),('dev-overflow-2.csv','EXPIRED'),('dev-overflow-2.csv','CANCELLED'),('dev-overflow-4.csv','ACCEPTED')) v(file,status) ON v.file=b.source_file_name;

-- Templates: regular project, objective assessment, practice and inactive archive per Subject.
INSERT INTO assignment_templates(subject_id,title,instructions,maximum_score,status,created_at,updated_at)
SELECT s.subject_id,s.subject_code||' — '||v.title,'Development exercise: explain your approach and check your work.',10,v.status,'2026-09-01+00','2026-09-15+00'
FROM subjects s CROSS JOIN (VALUES('Case Study','ACTIVE'),('Knowledge Check','ACTIVE'),('Practice Quiz','ACTIVE'),('Archived Workshop','INACTIVE')) v(title,status);

-- Assignments: keep the original proposal and cover every current enum combination/state.
INSERT INTO assignments(class_id,template_id,title,assignment_purpose,response_type,instructions,scope_text,task_checklist,maximum_score,status,available_from,due_at,time_limit_minutes,attempt_limit,published_at,cancelled_at)
SELECT c.class_id,tp.template_id,s.subject_code||' — '||v.title,v.purpose,v.response,tp.instructions,'Demonstrate the concepts covered in this class.',
 '[{"task_order":1,"title":"Read the instructions"},{"task_order":2,"title":"Complete and review your work"}]'::jsonb,10,v.status,
 CASE WHEN v.status='DRAFT' THEN NULL ELSE '2026-09-20+00'::timestamptz END,
 CASE WHEN v.status='DRAFT' THEN NULL ELSE '2026-12-20+00'::timestamptz END,
 CASE WHEN v.response='OBJECTIVE' THEN 30 ELSE NULL END,CASE WHEN v.purpose='PRACTICE' THEN 3 ELSE NULL END,
 CASE WHEN v.status IN ('PUBLISHED') THEN '2026-09-19+00'::timestamptz ELSE NULL END,
 CASE WHEN v.status='CANCELLED' THEN '2026-09-22+00'::timestamptz ELSE NULL END
FROM subject_classes c JOIN subject_terms st USING(subject_term_id) JOIN subjects s USING(subject_id) JOIN academic_terms t USING(term_id)
CROSS JOIN (VALUES('Case Study','REGULAR','ESSAY','PUBLISHED'),('Knowledge Check','REGULAR','OBJECTIVE','PUBLISHED'),('Practice Quiz','PRACTICE','OBJECTIVE','PUBLISHED'),('Workshop Draft','REGULAR','ESSAY','DRAFT'),('Cancelled Workshop','REGULAR','ESSAY','CANCELLED'))v(title,purpose,response,status)
JOIN assignment_templates tp ON tp.subject_id=s.subject_id AND tp.title=s.subject_code||' — '||CASE WHEN v.title IN ('Workshop Draft','Cancelled Workshop') THEN 'Case Study' ELSE v.title END
WHERE t.term_code='FALL2026' AND c.status='ACTIVE';

-- Question.options and Submission.answer stay embedded JSONB; no child tables introduced.
INSERT INTO questions(assignment_id,question_order,question_text,maximum_points,options)
SELECT a.assignment_id,n,CASE n WHEN 1 THEN 'Which approach best enforces data integrity?' ELSE 'Which design improves maintainability?' END,5,
 '[{"option_id":"A","option_text":"Use explicit constraints and clear responsibilities","is_correct":true},{"option_id":"B","option_text":"Ignore validation and duplicate all logic","is_correct":false},{"option_id":"C","option_text":"Store unrelated state without boundaries","is_correct":false}]'::jsonb
FROM assignments a CROSS JOIN generate_series(1,2)n WHERE a.response_type='OBJECTIVE';

-- Submissions: exercise all six states with consistent timing, grading and release data.
INSERT INTO submissions(assignment_id,enrollment_id,attempt_number,status,started_at,deadline_at,submitted_at,content,answer,score,feedback,graded_by,graded_at,released_at)
SELECT a.assignment_id,e.enrollment_id,1,v.status,'2026-09-22 08:00+00','2026-09-22 08:30+00',
 CASE WHEN v.status IN ('SUBMITTED','GRADED','RELEASED') THEN '2026-09-22 08:20+00'::timestamptz ELSE NULL END,
 CASE WHEN a.response_type='ESSAY' AND v.status IN ('SUBMITTED','GRADED','RELEASED') THEN 'Development response: analysis, design rationale and review notes.' ELSE NULL END,
 CASE WHEN a.response_type='OBJECTIVE' AND v.status IN ('SUBMITTED','GRADED','RELEASED') THEN COALESCE((SELECT jsonb_agg(jsonb_build_object('question_id',q.question_id,'selected_option_id','A') ORDER BY q.question_order) FROM questions q WHERE q.assignment_id=a.assignment_id),'[]'::jsonb) ELSE '[]'::jsonb END,
 CASE WHEN v.status IN ('GRADED','RELEASED') THEN CASE WHEN a.response_type='OBJECTIVE' THEN 10 ELSE 8.5 END ELSE NULL END,
 CASE WHEN v.status IN ('GRADED','RELEASED') THEN 'Good reasoning; improve the explanation of edge cases.' ELSE NULL END,
 CASE WHEN v.status IN ('GRADED','RELEASED') THEN c.teacher_id ELSE NULL END,
 CASE WHEN v.status IN ('GRADED','RELEASED') THEN '2026-09-22 10:00+00'::timestamptz ELSE NULL END,
 CASE WHEN v.status='RELEASED' THEN '2026-09-23+00'::timestamptz ELSE NULL END
FROM assignments a JOIN subject_classes c USING(class_id) JOIN class_enrollments e USING(class_id)
JOIN subject_registrations r USING(registration_id) JOIN users u ON u.user_id=r.student_id
JOIN (VALUES('student01@example.test','IN_PROGRESS'),('student02@example.test','SUBMITTED'),('student03@example.test','GRADED'),('student04@example.test','RELEASED'),('student05@example.test','EXPIRED'),('student06@example.test','VOID'))v(email,status) ON v.email=u.email
WHERE a.status='PUBLISHED' AND e.status='ACTIVE';

-- Accounts and responsibilities predate the historical enrollments/imports.
UPDATE users SET created_at='2025-08-01+00',updated_at='2026-09-01+00';
UPDATE subject_experts SET assigned_at='2026-03-01+00';
UPDATE teacher_subjects SET assigned_at='2026-03-01+00';
UPDATE class_experts SET assigned_at='2026-03-01+00';

-- Transient fixtures use the transaction clock; all identities, relationships and
-- historical timestamps remain deterministic across clean seed runs.
UPDATE student_invitations SET sent_at=CURRENT_TIMESTAMP,expires_at=CURRENT_TIMESTAMP+interval '48 hours' WHERE status='PENDING';
UPDATE additional_class_assignment_offers SET sent_at=CURRENT_TIMESTAMP,expires_at=CURRENT_TIMESTAMP+interval '48 hours' WHERE status='PENDING';
UPDATE submissions SET started_at=CURRENT_TIMESTAMP-interval '5 minutes',deadline_at=CURRENT_TIMESTAMP+interval '25 minutes' WHERE status='IN_PROGRESS';
COMMIT;
