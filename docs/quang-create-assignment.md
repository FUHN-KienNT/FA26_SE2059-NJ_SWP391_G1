# Quang — Create Assignment (UC33)

Updated: 2026-10-12.

## Completed behavior

- Teacher signs in, reaches the shared Teacher Dashboard, opens Assignment List for an assigned class, then selects Create Assignment.
- GET `/teacher/assignments?mode=create&classId=...` loads class/subject context and only active templates for that subject.
- Selecting a template prefills title, instructions and maximum score. All three can be edited immediately, before the first save. Changing template asks before replacing these fields; other content stays.
- Supported combinations: Regular/Essay, Regular/Objective and Practice/Objective. Practice automatically selects Objective.
- Response type selects one content editor: Essay shows scope and ordered task titles/descriptions; Objective shows ordered questions, points, options and correct answers. The other editor is hidden. Title, general instructions, score and timing remain shared.
- Switching response type retains unsaved editor content, including when a field validation error redisplays the form. Only content belonging to the selected response type is validated and saved. Hidden required editor fields do not block Save Draft.
- Save Draft creates the assignment and its questions in one transaction, then redirects to Detail with a success notice. Submitted content overrides the template defaults; the template itself is unchanged.
- Detail now also displays task descriptions saved by Create.

## Validation and access

- Active Teacher session, CSRF token and class ownership are required. The write transaction locks/rechecks the current Teacher, class assignment and active same-subject template.
- Title: nonblank, at most 200 characters. Maximum score/question points: positive, at most 99999999.99, up to two decimal places.
- Instructions, the selected type's content and schedule may remain incomplete in a Draft. Added active task/question/option rows must have their required text; blank rows can be removed.
- Draft questions may have no options/correct answer, and the question-point total may differ from the maximum score. Publication readiness is a separate workflow.
- At most one correct answer per question. Essay saves scope/tasks and no questions; Objective saves questions, an empty scope and an empty task checklist. Inactive editor payloads are ignored during validation and persistence.
- If both dates are entered, deadline must follow opening time. Form times use UTC+07 and are converted to instants before persistence.
- Time/attempt limits, when provided, must be positive integers. Regular assignments do not accept an attempt limit. Practice may leave it blank until publication.
- Server generates task/question ordering and option identities. A submitted status cannot publish the assignment: the insert explicitly writes DRAFT.
- Field validation errors keep normal entered form content. A database outage while reloading class/template context also keeps the submitted form and selected template for retry; this temporary display context never grants write access. Database failures roll back the parent and questions. POST success uses redirect to avoid a normal refresh repeating the insert.
- No schema migration is needed; tasks and options use existing JSONB columns.

## Files

New production files:

- `src/main/java/vn/edu/fpt/ams/dto/AssignmentCreatePage.java`: context/templates for the form.
- `src/main/java/vn/edu/fpt/ams/dto/AssignmentForm.java`: submitted values preserved for redisplay.
- `src/main/java/vn/edu/fpt/ams/util/AssignmentDraftValidator.java`: validates and canonicalizes a Draft.
- `src/main/webapp/WEB-INF/views/assignments/form.jsp`: Create form.
- `src/main/webapp/js/assignment-form.js`: template prefill, type-dependent fields and nested editors.
- `src/main/webapp/css/assignment-form.css`: scoped form styling.

Updated Assignment files:

- `AssignmentServlet.java`: Create GET/POST, CSRF, errors and redirect.
- `AssignmentService.java`: form load and atomic Draft creation.
- `AssignmentDAO.java`: subject templates, authorization locks and inserts.
- `AssignmentDetailView.java`, `assignments/detail.jsp`: task descriptions and creation notice.

Existing local navigation edits and integration seed files were preserved. No Dashboard, authentication or other team's feature module was rewritten. No commit/push was performed for this change.

## Verification

- `mvn package -q`: succeeded, including JSP compilation.
- `mvn -Dassignment.db.tests=true package -q`: 110 tests, 109 passed, 1 skipped, 0 failures/errors. The skipped test is the separately gated DBConnectionIntegrationTest. The final form/context refinement also passed AssignmentCreateServletTest and JspCompilationTest.
- New tests: AssignmentDraftValidatorTest, AssignmentCreateServletTest, AssignmentCreateDatabaseTest; one task-description regression case added to AssignmentDetailViewTest.
- PostgreSQL Create tests use TEMP tables and local sequences: persisted overrides/JSON content, role/ownership/template rejection, and rollback after a nested question insert failure. They do not write application tables.
- Browser check: Teacher sign-in → Dashboard → List → Create; template prefill, Practice/Objective controls, date error with retained inputs, successful save and Detail rendering. See the response-type update below for checks of the revised mutually exclusive editors.
- Before the response-type update, a test draft was added to local class IT-Q-A1: `[TEST-QUANG] Create Objective đầy đủ`, assignment ID 70, with one question, two options and one task. Its existing data is retained; Objective Detail now hides its old essay scope/tasks.
- Another browser test saved `[TEST-QUANG] Create Essay — báo cáo Java`, assignment ID 71, with edited instructions, scope, task description and no schedule. It opened Detail successfully as Draft.
- Browser proof: `G:\SWP\outputs\create-assignment-20261012\created-draft-detail.png`.
- Essay proof: `G:\SWP\outputs\create-assignment-20261012\created-essay-detail.png`.

## Running locally

The current review server is at `http://localhost:8081/ams/auth/signin`.
Open Dashboard → Assignments → choose a class → Create Assignment.
Current runtime and logs: `G:\SWP\tools\ams-lifecycle-20261012`; Java PID 17744. Local PostgreSQL was started manually with `pg_ctl`. Earlier Create/Edit verification used the previous `G:\SWP\tools\ams-edit-20261012` runtime; the historical proof below remains valid for those checks.

Create/Save Draft, Edit/Save Changes, Publish, and Cancel are implemented. See [the Edit report](quang-edit-assignment.md) and [the lifecycle report](quang-assignment-lifecycle.md) for publication/cancellation rules, proof, and current runtime. The final integrated package run after lifecycle implementation passed 183 tests: 182 passed, 1 skipped, 0 failures/errors; earlier feature-specific verification counts above are preserved as historical evidence.

## Response-type update requested by Quang

The user's 2026-10-12 instruction makes scope/tasks Essay-only content. Both Regular Objective and Practice Objective hide that content and save only Objective questions. Essay hides Objective questions. Detail follows the same display rule. Existing database records are not deleted or migrated.

The existing SRS should be aligned with this decision: page 23 DBR-30 describes Essay scope/checklist storage; DBR-31 requires scope/tasks for Regular Essay only; DBR-32 no longer describes Practice scope/checklist content. Page 97 GB-50 should also apply to Regular Essay. Objective publication readiness uses questions/options/scoring and its applicable timing/attempt rules. This update supersedes the earlier rule requiring scope/tasks for Regular Objective.

Validation for this update: `mvn -Dassignment.db.tests=true -Dtest=AssignmentDraftValidatorTest,AssignmentCreateDatabaseTest,JspCompilationTest package -q` passed all 15 selected tests (7 validator, 7 PostgreSQL Create and 1 JSP compilation). These checks include ignoring malformed inactive editor payloads, preserving active Essay tasks/Objective questions and incomplete Draft persistence.

Browser validation passed for Essay/Objective switching, Practice forcing the Objective editor, retention of both editors after a date-validation error, and saving despite incomplete hidden rows. Local test Draft 72 (`[TEST-QUANG] Objective chỉ lưu trắc nghiệm`) has Objective questions only; Draft 73 (`[TEST-QUANG] Essay chỉ lưu tự luận`) has Essay scope/tasks only. Screenshots are in `G:\SWP\outputs\create-assignment-20261012`: `objective-only-form.png`, `essay-only-form.png`, `objective-only-detail.png`, `essay-only-detail.png`.

## Stale form/session recovery

A Create form opened before another sign-in contains the earlier session's CSRF token. Sign-in invalidates the old session and creates a new token; submitting that stale form caused a bare Tomcat 403 page and lost the visible editor.

Create now rejects the stale-token POST without any insert, verifies the current Teacher's class access, and redisplays the submitted form with a clear notice and the current session token. The Teacher must review and explicitly click Save Draft again. Missing tokens also fail closed and can recover in the same way. Wrong-role/anonymous sessions and forbidden classes still cannot access or save the form. Shared authentication and CSRF verification were not bypassed.

The failing regression invocation was `mvn -Dtest=AssignmentCreateServletTest#expiredFormTokenRetainsDraftAndRequiresExplicitRetryWithCurrentToken test -q`; it reproduced the missing form forward under HTTP 403. After the fix, `mvn -Dtest=AssignmentCreateServletTest,AssignmentServletTest,JspCompilationTest package -q` passes, including explicit retry, absent token recovery and forbidden class checks.

Verification: all 20 selected tests passed. A real HTTP session-rotation check returned 403 with retained form/questions and an updated token; explicit retry returned 200 Detail for local test Draft 74. Browser testing repeated sign-in in another tab, submitted the old Essay form, visibly retained its title/instructions/scope, and saved successfully on the second click as Draft 75. Proof files: `G:\SWP\outputs\create-assignment-20261012\csrf-recovery-form.png` and `csrf-recovery-saved.png`.
