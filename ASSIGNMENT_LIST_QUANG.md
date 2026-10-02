# Assignment List — Quang

## Delivered scope

Teacher Assignment List now reads PostgreSQL data for the Teacher's assigned classes. It supports title search and combined Purpose, Response type and Status filters. Reset clears filters while keeping the selected class. The screen shows deadlines in UTC+07, result counts, status-specific action links, empty states and recoverable load errors.

Ownership and active Teacher status are checked in the service and SQL query. Search uses prepared parameters and literal substring matching; displayed user content is HTML-escaped. Create, View, Edit, Publish and Cancel links point to the existing Assignment modes; those detail/write workflows remain pending, and POST remains unimplemented.

## Files and integration

- Updated AssignmentServlet, AssignmentService, AssignmentDAO and assignments/list.jsp.
- Added AssignmentListFilter, AssignmentClassOption, AssignmentListPage and AssignmentListView; separate assignment-list.css.
- Added AssignmentDatabaseLifecycle for webapp JDBC initialization and cleanup. It coexists with upstream DBConnection's driver registration.
- AuthFilter retains upstream sign-in redirects and account revalidation, with one narrow opt-in preview hook.
- Includes controller, PostgreSQL query, escaping/date formatting, classloader lifecycle and preview authorization tests.
- Integrated with master containing NamDH authentication/Admin changes and DungNM Teacher Dashboard/Enrollment changes.

## Normal use and optional local preview

After deployment, open `/ams/teacher/assignments` with an active Teacher session.

Preview is **disabled by default** for the shared source. To test without login on your own computer, add this to ignored `src/main/resources/application.properties`, then rebuild and deploy:

```properties
assignment.preview.enabled=true
```

Alternatively set JVM option `-Dams.assignment.preview=true`. Set it to false to disable preview. The setting does not grant access from remote IPs, to other modules, or through POST, and never replaces an existing signed-in identity. The selected test identity is the active Teacher with the lowest user_id, preferring Teachers with assigned classes. A visible banner identifies local preview mode.

Quang's ignored local configuration remains enabled. Credentials and local configuration are not committed.

## Test data

`database/seed_assignment_list_quang.sql` adds 12 tagged `[TEST-QUANG]` assignments to the preview Teacher's first active class and 7 sample Objective questions. It reuses an active Subject template, preserves existing rows and skips fixtures already present. Apply only to a development database after the schema, migrations and base seed.

With the original Project Proposal plus these fixtures, the class has 13 assignments: Draft 6, Published 4, Cancelled 3; Regular 10, Practice 3; Essay 6, Objective 7. Search `Quiz` returns 4; Practice returns 3. Examples cover missing deadlines, an overdue Published assignment, Vietnamese text and literal `%_` search.

## Validation and deployment

Run with JDK 17 and Maven:

```powershell
mvn -B '-Dassignment.db.tests=true' package
```

Database integration tests require a reachable configured PostgreSQL database. Assignment query fixtures use TEMP tables; other workflow tests use isolated H2 databases. Merge validation on 2026-10-02 used a separate PostgreSQL 18 instance with schema, both migrations and seed files, leaving Quang's database unchanged: **46 tests passed, zero failures/errors/skips**, and all JSPs compiled successfully.

An isolated Tomcat smoke check also passed: sign-in page 200; protected Assignment GET redirects to sign-in with preview disabled; enabled local preview shows 13 assignments and 3 Practice results; Admin remains protected; disabling preview restores the sign-in redirect.

`deploy-assignment-local.ps1` updates this Windows machine's Tomcat 10.1 deployment, verifies the WAR hash and checks HTTP. Run it in an Administrator terminal. It does not migrate the database. For existing databases, apply the two upstream additive migrations separately; do not rerun the base schema or reset the database.

## Remaining work

- Complete Assignment details and mutation workflows in their own tasks.
- Local sign-in requires valid password hashes; Student Google sign-in requires OAuth configuration.
- Deploy the new WAR to the installed Tomcat service; pushing/merging source does not deploy it.
