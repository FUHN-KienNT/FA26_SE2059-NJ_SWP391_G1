# Assignment List — Quang

## Delivered scope

Teacher Assignment List now reads PostgreSQL data for the Teacher's assigned classes. It supports title search and combined Purpose, Response type and Status filters. Reset clears filters while keeping the selected class. The screen shows deadlines in UTC+07, result counts, status-specific action links, empty states and recoverable load errors.

Ownership and active Teacher status are checked in the service and SQL query. Search uses prepared parameters and literal substring matching; displayed user content is HTML-escaped. View now opens the complete read-only Assignment Detail described below. Create, Edit, Publish and Cancel remain pending, and POST remains unimplemented.

## Assignment Detail — 2026-10-04

- View loads the selected assignment with class, subject, academic term and template names. It displays instructions, scope, checklist, maximum score, status and timing in UTC+07. Practice assignments also display their attempt limit.
- Objective assignments show questions in question order, options, correct-answer labels for the authorized Teacher, and points per question. Essay assignments display their instructions and tasks without a question section. Missing content has an empty state; unsupported saved checklist/option formats show a warning rather than a raw JSON dump.
- Both detail and question queries require the current active Teacher to own the assignment's class. Missing and inaccessible assignments return the same 404; malformed IDs return 400. Anonymous requests still require sign-in. The browser's classId does not determine ownership or the return destination.
- Back to list preserves the actual class and title/Purpose/Response type/Status filters. Stored text is HTML-escaped; multiline content remains readable. Database failures render a recoverable message without SQL details.
- Implementation uses existing AssignmentServlet/AssignmentService/AssignmentDAO and assignments/detail.jsp, plus new AssignmentDetailPage, AssignmentDetailView and assignment-detail.css. No schema migration or persistent assignment-data changes are needed. Unfinished action modes now display an unavailable message instead of the old nonfunctional Save form.
- Validation: Maven package with assignment.db.tests=true passed 92 tests, failed 0, skipped 1 environment-gated DBConnectionIntegrationTest; the actual PostgreSQL List/Detail tests passed using session-local TEMP tables. All JSPs compiled. Live Tomcat checks verified 13 detail pages (6 Essay, 7 Objective, 7 questions), content/context, filter round trips, 400/404 responses, anonymous redirects and test-session sign-out. Browser verification also confirmed View opens the question/answer page.
- Local deployment backup: G:\SWP\backups\before-assignment-detail-20261004-224256.

## Quang change notes — 2026-10-04

- Teacher Dashboard includes an Assignments link and a View assignments action for each assigned class. Its overview continues to cover all classes owned by the signed-in Teacher; Assignment List remains scoped to the selected class.
- Assignment List and Detail require an active Teacher login. The temporary preview bypass is disabled even when legacy preview flags are enabled.
- View opens a complete, read-only Assignment Detail with instructions, scope, checklist, questions/options for Objective assignments, class/subject/term context, score and timing. Return links preserve the real class and selected filters.
- Dashboard, List and Detail use the shared role layout introduced by upstream commit 6e5b533. Integration retained the local Detail implementation and login requirement while removing duplicate navigation and the obsolete preview banner.
- Added additive development fixtures in `database/seed_teacher_demo_quang.sql` and a test guide in `database/TEST_DATA_QUANG.md` for Dashboard, List and Detail. Running the SQL is an explicit local step; pulling or deploying the code does not apply it automatically.
- Updated the local deployment script to verify the sign-in requirement instead of expecting the retired preview banner. Credentials, local configuration, database backups and build artifacts are excluded from Git.
- Pre-publication validation: Maven package with `assignment.db.tests=true` passed 92 tests with zero failures/errors and one environment-gated test skipped. PostgreSQL List/Detail tests passed, all JSPs compiled, and both layout conflicts were resolved without removing local functionality.
- Remaining scope: Create, Edit, Publish, Cancel and submission workflows are still pending. This update does not change the database schema or deploy automatically to Tomcat.

## Files and integration

- Updated AssignmentServlet, AssignmentService, AssignmentDAO and assignments/list.jsp.
- Added AssignmentListFilter, AssignmentClassOption, AssignmentListPage and AssignmentListView; separate assignment-list.css.
- Added AssignmentDatabaseLifecycle for webapp JDBC initialization and cleanup. It coexists with upstream DBConnection's driver registration.
- AuthFilter requires sign-in for Assignment List and retains upstream role checks and account revalidation.
- Includes controller, PostgreSQL query, escaping/date formatting, classloader lifecycle and authorization tests.
- Integrated with master containing NamDH authentication/Admin changes and DungNM Teacher Dashboard/Enrollment changes.

## Sign-in requirement

Open `/ams/auth/signin` and sign in with an active Teacher account, then open `/ams/teacher/assignments`. Anonymous GET requests redirect to sign-in; anonymous POST requests are rejected with HTTP 401. Other roles cannot access the Teacher screen.

The temporary preview bypass was retired on 2026-10-04 now that Teacher sign-in is available. Legacy preview properties, environment variables and JVM options no longer grant access. `AssignmentPreviewAccess` remains as a compatibility API that always returns false. Credentials and local configuration are not committed.

## Test data

`database/seed_assignment_list_quang.sql` adds 12 tagged `[TEST-QUANG]` assignments to the sample Teacher's first active class and 7 sample Objective questions. It reuses an active Subject template, preserves existing rows and skips fixtures already present. Apply only to a development database after the schema, migrations and base seed.

With the original Project Proposal plus these fixtures, the class has 13 assignments: Draft 6, Published 4, Cancelled 3; Regular 10, Practice 3; Essay 6, Objective 7. Search `Quiz` returns 4; Practice returns 3. Examples cover missing deadlines, an overdue Published assignment, Vietnamese text and literal `%_` search.

## Validation and deployment

Run with JDK 17 and Maven:

```powershell
mvn -B '-Dassignment.db.tests=true' package
```

Database integration tests require a reachable configured PostgreSQL database. Assignment query fixtures use TEMP tables; other workflow tests use isolated H2 databases. Merge validation on 2026-10-02 used a separate PostgreSQL 18 instance with schema, both migrations and seed files, leaving Quang's database unchanged: **46 tests passed, zero failures/errors/skips**, and all JSPs compiled successfully.

The original 2026-10-02 Tomcat smoke check covered the temporary local preview. That mode is now retired; current verification checks the anonymous sign-in redirect, authenticated Teacher access and sign-out protection.

The 2026-10-04 login requirement passed the default Maven package checks: **62 tests passed, zero failures/errors, 11 optional database tests skipped**, and all JSPs compiled. Live checks against the installed Tomcat confirmed anonymous Assignment List/View/Create GETs redirect to `/ams/auth/signin` (302), anonymous POST is rejected (401), and a signed-in Teacher can load Assignment List (200). The temporary test session signed out successfully. Regression tests also keep access blocked when the legacy preview JVM flag is explicitly enabled.

`deploy-assignment-local.ps1` updates this Windows machine's Tomcat 10.1 deployment, verifies the WAR hash and checks HTTP. Run it in an Administrator terminal. It does not migrate the database. For existing databases, apply the two upstream additive migrations separately; do not rerun the base schema or reset the database.

## Remaining work

- Complete Assignment create/edit/publish/cancel and submission workflows in their own tasks.
- Local sign-in requires valid password hashes; Student Google sign-in requires OAuth configuration.
- Rebuild and deploy future source updates; pushing/merging source does not deploy them.
