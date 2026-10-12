# Quang — Edit Assignment (UC34)

Updated: 2026-10-12.

Teachers can open **Edit** from Assignment List or **Edit Assignment** from Assignment Detail. The form loads the existing draft, and **Save Changes** updates that assignment and returns to its Detail page with a one-time success notice.

## Behavior

Editing requires an active Teacher account assigned to the assignment's class. Only Draft assignments are editable. The server checks access when loading and saving; missing or inaccessible assignments return 404, and assignments that are no longer Draft return 409. Saves require a valid CSRF token.

The form loads all saved values: the source template, title, instructions, purpose, response type, maximum score, essay scope and tasks, objective questions with points and answer options, correct answers, opening time, deadline, time limit, and practice attempt limit. Class context is read-only. Existing seconds and fractional seconds in dates are retained.

Switching between Essay and Objective keeps both editors' unsaved values. Before saving a different response type, the form asks for confirmation if the original type has saved content that will be removed. Cancelling the confirmation keeps the form open without sending a request. Only the selected response type is persisted. Individual task, question, and option removal also requires confirmation.

Existing question and option IDs remain stable when their text, points, correctness, or order changes. New rows receive new IDs, and removed rows are deleted during the same save.

The original template remains available even when it becomes inactive, preserving the draft's customized content. A replacement template must be active and belong to the same subject. Selecting a replacement asks before filling its title, instructions, and maximum score.

Validation failures redisplay the entered values and field errors. An expired CSRF token redisplays the form with a refreshed token for an explicit retry. A revision conflict retains the entered values, disables saving, and provides **Reload latest draft**; reloading asks before discarding unsaved input.

## Architecture and files

The servlet handles requests and form redisplay, the service enforces the edit rules and transaction, and the DAO performs the database work. The shared form supports both Create and Edit.

Java paths in the table are relative to `src/main/java/vn/edu/fpt/ams/`.

| Component | Files and responsibility |
| --- | --- |
| Controller | `controller/AssignmentServlet.java`: edit GET, update POST, CSRF recovery, errors, and redirect. |
| Service and persistence | `service/AssignmentService.java` and `dao/AssignmentDAO.java`: authorization, Draft checks, template eligibility, and atomic updates. |
| Edit state | `dto/AssignmentEditPage.java`, `dto/AssignmentForm.java`, `util/AssignmentEditSupport.java`, and `util/AssignmentEditConflictException.java`: saved form data, identity validation, revision, and conflicts. |
| Template choices | `dto/AssignmentCreatePage.java`: separates the display label from copied content, so the inactive-source annotation cannot become the assignment title. |
| UI | `list.jsp`, `detail.jsp`, and `form.jsp` under `src/main/webapp/WEB-INF/views/assignments/`; `src/main/webapp/js/assignment-form.js`; `src/main/webapp/css/assignment-form.css`: entry points, editors, and confirmations. |
| Tests | `src/test/java/vn/edu/fpt/ams/controller/AssignmentEditServletTest.java` and `src/test/java/vn/edu/fpt/ams/service/AssignmentEditDatabaseTest.java`. |

Saving locks and rechecks the owned draft and relevant authorization, then updates the assignment and nested content in one transaction. Any failure rolls back the complete update. An aggregate SHA-256 revision includes the saved assignment and its questions and options, so a stale tab cannot overwrite changes made after it loaded.

## Scope

Publish and Cancel are now implemented; see [the lifecycle report](quang-assignment-lifecycle.md) for their confirmation pages, rules, transaction safety, and verification. The Edit feature introduces no schema migration, no authentication or Dashboard rewrite, and no Git push.

## Verification

- `mvn -Dassignment.db.tests=true package -q` succeeded: 143 tests, 142 passed, 1 skipped, no failures or errors. The separately gated DBConnectionIntegrationTest was skipped. JSP compilation also passed.
- The 13 PostgreSQL Edit tests use TEMP tables, covering prefill, inactive original source, changed-template rules, stable IDs and reorder, additions/removals, invalid and foreign identities, authorization, Draft state, stale revisions, type changes and full rollback on nested failure. They do not update application tables.
- The 12 Edit servlet tests cover GET/POST sessions, same-ID save and redirect, CSRF rejection and explicit retry, validation retention, revision conflicts, unavailable assignments and non-Draft rejection.
- Browser: Teacher sign-in → Dung's Dashboard → Assignment List → Create a separate test Draft → Detail → Edit. Editing title/instructions/times and adding/reordering questions saved successfully to the same assignment ID. Reopening Edit showed the retained existing question ID and option IDs, and new server-generated identities.
- Browser: an old tab's save after a newer save returned the conflict form, kept its entered content, and disabled Save Changes. Essay scope/task text and descriptions also saved successfully; an invalid deadline retained the form and saved after correction.
- A response-type change persisted Essay content and removed the previous Objective content on the separate test Draft. The native confirmation's cancellation path was not fully verified by browser automation; the confirmation logic remains in the shared form script.
- Browser tests created only Draft 78, `[TEST-QUANG] Edit Assignment — đã chỉnh sửa`, in class IT-Q-A1. Its final content is Essay with scope and one task. Existing user Drafts 76 and 77 were not edited.
- Proof files: `G:\SWP\outputs\edit-assignment-20261012\saved-objective-detail.png`, `saved-essay-detail.png`, and `stale-edit-conflict.png`. The Objective screenshot shows the earlier successful update before the deliberate response-type change.
- `git diff --check` passed. Changes remain local; no commit, push or merge was performed.

## Running locally

The updated WAR is deployed to the isolated review server at `http://localhost:8081/ams/auth/signin`. Current runtime and logs: `G:\SWP\tools\ams-lifecycle-20261012`; Java PID 17744. Local PostgreSQL was started manually with `pg_ctl`. The earlier Edit proof above came from `G:\SWP\tools\ams-edit-20261012`, Java PID 23644; those checks remain recorded as historical evidence. The installed Windows Tomcat is unchanged.

Sign in as a Teacher, open Dashboard → Assignments, then choose **Edit** on a Draft row or **Edit Assignment** in its Detail. Use **Save Changes** to update the same Draft and return to Detail. Publish and Cancel are available through their separate confirmation pages.

The final integrated package run after lifecycle implementation succeeded: 183 tests, 182 passed, 1 skipped, 0 failures/errors, including JSP compilation. See [the lifecycle report](quang-assignment-lifecycle.md) for the 40 focused publication/lifecycle checks and current browser proof; the earlier Edit-specific counts above are retained.
