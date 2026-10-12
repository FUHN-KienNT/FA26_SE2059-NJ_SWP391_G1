# Assignment publication and cancellation — UC35 / UC36

Updated: 2026-10-12.

This local change implements **UC35 Publish Assignment** and **UC36 Cancel Assignment** through a dedicated confirmation page. These are the corrected use-case numbers for the SRS material on pages 70–72.

## Confirmation and authorization

`GET /teacher/assignments?mode=publish|cancel&assignmentId=...` reads the saved assignment and questions. It never changes assignment state, timestamps, or submissions. The preview shows the current/proposed state, saved settings, publication errors, and the current count of in-progress attempts for cancellation. The count can change before confirmation.

The confirmation POST uses the Teacher identity from the session, requires an active Teacher session and a valid CSRF token, and rechecks the active account and current class ownership in the database. A revision fingerprint binds confirmation to the saved assignment and nested questions/options. Missing or stale revisions, validation errors, and database failures reload or explain the latest state without showing success. Confirmation is unavailable when the latest content cannot be loaded or the state does not permit the action.

## Publication

Publication changes `DRAFT → PUBLISHED` in one transaction. The service locks the parent assignment and relevant saved rows before validating. Both availability dates are required; `due_at` must be strictly later than `available_from` and the current database time from `clock_timestamp()`, read after locking. Opening may be in the past or future.

Instructions are required. Supported types are Regular Essay, Regular Objective, and Practice Objective. Scores and question points must be positive and exactly representable as `NUMERIC(10,2)`. Regular assignments have no attempt limit; Practice assignments require a positive attempt limit. Optional time limits must be positive.

Essay publication requires nonblank scope and at least one valid task, and rejects saved Objective questions. Task objects need a positive unique order and nonblank title; optional descriptions must be text. Nonblank legacy task strings remain supported because the existing detail and edit views already support that format. Objective publication requires saved questions with valid ownership, identities, orders, points, at least two options, and exactly one boolean correct answer. Option identities/orders are preserved, and question points must total the maximum score exactly.

The latest requirement makes **Scope and Tasks Essay-only**. Objective publication rejects saved Essay scope/tasks; it does not silently delete incompatible content. The SRS descriptions on pages 70–72 and related SDS content must be aligned with this override, the required instructions, and the corrected UC35/UC36 numbering.

Publication freezes assignment content and settings through the existing Draft-only edit rules. It preserves question IDs, option IDs/orders, and the saved assignment snapshot. A source template becoming inactive does not invalidate or overwrite the already saved assignment copy.

## Cancellation, retries, and transaction safety

Cancellation permits `DRAFT → CANCELLED` and `PUBLISHED → CANCELLED`, including incomplete Drafts. In the same transaction, only the assignment's `IN_PROGRESS` submissions become `VOID`. Submitted/final attempts, results, feedback, files, answers, question IDs, and option content remain preserved. A failure rolls back both the parent transition and every submission change.

Repeating Publish on an already Published assignment or Cancel on an already Cancelled assignment is a no-op after authorization. Original lifecycle timestamps remain unchanged. This also supports an idempotent retry when a successful response is lost: reopen the latest confirmation or retry the same authorized action without applying the transition twice. A Cancelled assignment cannot be published.

Future Submission **start, save, submit, and timeout** transactions must lock the parent assignment first and recheck `PUBLISHED` before locking or writing child rows. If cancellation obtains the parent lock first, a waiting submission operation must see `CANCELLED` and reject its write. This parent-first protocol is required for cancellation to win safely; future submission flows must not rely on an earlier preview or session state.

## Files and verification

| Area | Files |
| --- | --- |
| Controller, service, persistence | `src/main/java/vn/edu/fpt/ams/controller/AssignmentServlet.java`, `service/AssignmentService.java`, `dao/AssignmentDAO.java` |
| Saved confirmation and validation | `src/main/java/vn/edu/fpt/ams/dto/AssignmentLifecyclePage.java`, `util/AssignmentPublicationValidator.java` |
| Confirmation and navigation | `src/main/webapp/WEB-INF/views/assignments/lifecycle.jsp`, `src/main/webapp/css/assignment-lifecycle.css`, assignment list/detail views |
| Focused tests | `src/test/java/vn/edu/fpt/ams/util/AssignmentPublicationValidatorTest.java` (16), `controller/AssignmentLifecycleServletTest.java` (8), `service/AssignmentLifecycleDatabaseTest.java` (16) |
| Handoff documentation | `docs/quang-assignment-lifecycle.md`, `docs/quang-create-assignment.md`, `docs/quang-edit-assignment.md` |

Java filenames without a repeated root in the table share the preceding `src/main/java/vn/edu/fpt/ams/` or `src/test/java/vn/edu/fpt/ams/` root.

Coverage includes publication boundaries, stale/incompatible content, read-only previews, session/CSRF/ownership checks, revision conflicts, preserved saved identities and final data, repeated actions, locking order, and rollback after parent or child updates. The 16 validator tests also passed independently using `javac` and cached JUnit assertions before integration.

The implementation owner's final `mvn -Dassignment.db.tests=true package -q` run succeeded: **183 tests, 182 passed, 1 skipped, 0 failures/errors**, including JSP compilation. The 40 focused publication/lifecycle tests all passed: 16 validator, 8 servlet, and 16 PostgreSQL database tests. The skipped test is the separately gated `DBConnectionIntegrationTest`. These totals were checked against the generated Surefire XML reports. PostgreSQL lifecycle tests use temporary tables and do not change application tables.

Browser verification used a new local fixture only: Assignment **79**, class **9**, title `[TEST-QUANG] Publish Cancel — kiểm thử`. Existing Assignments 76, 77, and 78 were not changed.

- Create saved an incomplete Objective Draft. Publish displayed missing opening/deadline/question errors and offered no confirmation button; the assignment remained Draft.
- Edit added one question worth 20 points, two options, exactly one correct answer, and dates **2026-10-13 08:00 → 2026-10-20 22:00 (UTC+07)**. Saving returned to the same Draft.
- The publication page displayed the saved readiness/configuration. Confirming publication returned Published Detail with a publication timestamp and no Edit action. The opening date is in the future, so publication does not imply Student work can start immediately.
- The cancellation page displayed its effects. Confirming cancellation returned Cancelled Detail with both lifecycle timestamps and the saved question/content retained. All Edit/Publish/Cancel actions were hidden.

Proof directory: `G:\SWP\outputs\assignment-lifecycle-20261012`.

| Screenshot | Evidence |
| --- | --- |
| `publication-errors.png` | Incomplete Draft readiness errors; confirmation unavailable. |
| `publish-confirmation.png` | Saved valid Objective configuration ready for explicit publication. |
| `published-detail.png` | Published state and publication timestamp after confirmation. |
| `cancel-confirmation.png` | Explicit cancellation confirmation and preserved-history warning. |
| `cancelled-detail.png` | Cancelled state, lifecycle timestamps, retained question/content, and hidden lifecycle actions. |

The browser fixture had no Student submissions. Voiding unfinished sessions, preserving submitted/graded/released attempts, retry timestamps, conflicting requests, and rollback were verified by the PostgreSQL lifecycle tests.

## Running locally

The packaged WAR is deployed to the isolated review server at `http://localhost:8081/ams/auth/signin`. Current runtime and logs: `G:\SWP\tools\ams-lifecycle-20261012`; Java PID **17744**. Local PostgreSQL was started manually with `pg_ctl` for this run.

Sign in as a Teacher, open Dashboard → Assignments, then open **Publish Assignment** or **Cancel Assignment** from a permitted row or Detail page. Review the saved configuration and confirm the action. Create, Edit, Publish, and Cancel are implemented; Student submission and Essay evaluation/release are separate workflows.

The work remains local. It introduces no schema migration, authentication change, shared dashboard change, commit, push, or merge.
