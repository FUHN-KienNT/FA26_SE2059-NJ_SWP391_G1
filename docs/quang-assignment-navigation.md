# Teacher navigation for Quang's Assignments

The project uses Dung's existing `/teacher/dashboard`. There is no separate Quang Teacher Dashboard to delete. Its Servlet, Service, DAO and business view are retained.

The navigation is:

1. Sign in with an ACTIVE Teacher account.
2. Authentication sends the Teacher to `/teacher/dashboard`.
3. Choose Assignments, or a Class's View assignments link, on the Dashboard.
4. On Assignment List, use View to open an owned Assignment Detail.
5. Back to list returns to the same Class and applied filters. Dashboard links return directly to Dung's Dashboard.

## Changes

- `assignments/list.jsp`: the breadcrumb now links directly to `/teacher/dashboard`, labelled Dashboard.
- `assignments/detail.jsp`: the existing Dashboard breadcrumb has the same label.
- Existing authentication and ownership checks remain in use: anonymous GETs redirect to sign-in; Teacher sign-in lands on the Dashboard; other roles and foreign Class/Assignment requests are rejected.
- No files, database rows or Dashboard business code were deleted.

## Verification on 07 October 2026

- Maven package passed with 20 tests: AuthSessionTest, AssignmentServletTest, AssignmentPreviewAccessTest and JspCompilationTest.
- 21 live HTTP checks passed: anonymous GET/POST protection, Teacher landing, Dashboard-to-List links, View-to-Detail, filtered Back, foreign-data rejection and protection after sign-out.
- The same Dashboard → Class Assignments → View flow was exercised in the browser.

## Local review and deployment

The updated WAR is `target/ams.war`. The Windows account used for this change cannot read/write the installed Tomcat application under Program Files, so port 8080 has not been replaced.

A loopback-only review server runs the new WAR at `http://localhost:8081/ams/auth/signin`. Its launcher and HTTP verification evidence are in `G:\SWP\tools\ams-teacher-navigation-20261007`. The review uses the existing local `ams_db`.

Test account: `quang.it.a@example.test`, password `QuangIT@391`. Sign-in opens Dung's Dashboard with two owned Classes.

To update the installed Tomcat on port 8080, use an Administrator PowerShell in the project directory and run the existing `deploy-assignment-local.ps1` after reviewing the new WAR. The script replaces only the local AMS deployment and verifies sign-in protection.
