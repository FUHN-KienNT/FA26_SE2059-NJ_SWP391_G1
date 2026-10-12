# Quang integration fixtures

This fixture prepares separate local accounts and Classes for Teacher Dashboard, Assignment List and Assignment Detail. It complements the 36 consolidated test cases; it does not execute the full workbook or satisfy every independent Dashboard fixture.

## Sign-in

Open [AMS sign-in](http://localhost:8080/ams/auth/signin). The local development password for the four accounts below is `QuangIT@391`.

| Account | Purpose | Owned Classes |
| --- | --- | --- |
| `quang.it.a@example.test` | Shared seven-row List fixture and a populated Dashboard | IT-Q-A1, IT-Q-A2 |
| `quang.it.b@example.test` | Positive/negative ownership checks | IT-Q-B1 |
| `quang.it.detail@example.test` | Independent Detail variants | IT-Q-C1 |
| `quang.it.empty@example.test` | No-Class Dashboard/List | None |

The password hash uses the application's PBKDF2-SHA256 format. Students in this fixture have synthetic Google identifiers for database relationships; they are not Google login accounts.

## Dashboard and List baseline

Teacher A has **2 Classes, 6 ACTIVE placements, 8 Assignments and 3 Submission attempts**. The retained INACTIVE placement is excluded from the active total.

| Class | ID | Active roster / capacity | Pending invitations | Seats left initially | Assignments | Submissions |
| --- | --- | --- | --- | --- | --- | --- |
| IT-Q-A1 | 9 | 4 / 20 | 2 | 14 | 7 | 3 |
| IT-Q-A2 | 10 | 2 / 20 | 0 | 18 | 1 | 0 |
| IT-Q-B1 | 11 | 0 / 20 | 0 | 20 | 1 | 0 |
| IT-Q-C1 | 12 | 0 / 20 | 0 | 20 | 13 | 0 |

PENDING invitations expire 47 hours after the first seed run. Rerunning preserves their original timestamps; after expiry, A1 has no reserved seats and its seats left become 16.

Teacher A's Class selector contains only A1 and A2, and defaults to A1. A1 totals: Regular/Practice **6/1**, Essay/Objective **3/4**, Draft/Published/Cancelled **3/3/1**. A2 contains only `[IT] Class Two Essay`.

For the workbook, map shared fixture aliases CL-A1/CL-A2/CL-B1 to IT-Q-A1/IT-Q-A2/IT-Q-B1. Replace placeholder IDs with the actual values below. Independent Detail data uses Teacher C, so its additional Assignments do not change A1's seven-row filter counts. Cases requiring an empty A2, historical Classes or a different Dashboard metric matrix still need their own separate fixture.

## Detail examples

Teacher C has Essay/Objective/Practice, Cancelled, future/overdue timing, UTC+07 rollover, an empty Draft, mixed valid/invalid embedded items and harmless markup. Two examples intentionally distinguish stored order from JSON array position for checklist/options. As of the 2026-10-13 handoff, Assignment Detail respects task_order/option_order, with regression tests for shuffled and legacy/malformed-order content. The presence of other fixtures alone does not establish a passing test result.

| Class | Assignment ID | Title | Status | Detail |
| --- | --- | --- | --- | --- |
| IT-Q-A1 | 48 | [IT] Alpha Essay Draft | DRAFT | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=48) |
| IT-Q-A1 | 49 | [IT] Quiz Alpha | PUBLISHED | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=49) |
| IT-Q-A1 | 50 | [IT] Quiz Beta | PUBLISHED | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=50) |
| IT-Q-A1 | 51 | [IT] Cancelled Quiz | CANCELLED | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=51) |
| IT-Q-A1 | 52 | [IT] SQL 100%_ Basics | DRAFT | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=52) |
| IT-Q-A1 | 53 | (title consists of exactly 200 L characters) | DRAFT | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=53) |
| IT-Q-A1 | 54 | [IT] Essay &lt;script&gt;alert(1)&lt;/script&gt; & "review" | PUBLISHED | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=54) |
| IT-Q-A2 | 55 | [IT] Class Two Essay | DRAFT | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=55) |
| IT-Q-B1 | 56 | [IT] Foreign Quiz | PUBLISHED | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=56) |
| IT-Q-C1 | 57 | [IT-QUANG] C01 Báo cáo tiếng Việt | DRAFT | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=57) |
| IT-Q-C1 | 58 | [IT-QUANG] C02 Trắc nghiệm Web | PUBLISHED | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=58) |
| IT-Q-C1 | 59 | [IT-QUANG] C03 Luyện tập SQL | PUBLISHED | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=59) |
| IT-Q-C1 | 60 | [IT-QUANG] C04 Bài đã hủy | CANCELLED | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=60) |
| IT-Q-C1 | 61 | [IT-QUANG] C05 Bài chưa mở | PUBLISHED | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=61) |
| IT-Q-C1 | 62 | [IT-QUANG] C06 Báo cáo quá hạn | PUBLISHED | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=62) |
| IT-Q-C1 | 63 | [IT-QUANG] C07 Giờ UTC cộng bảy | PUBLISHED | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=63) |
| IT-Q-C1 | 64 | [IT-QUANG] C08 Đề nháp rỗng | DRAFT | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=64) |
| IT-Q-C1 | 65 | [IT-QUANG] C09 Thứ tự công việc | DRAFT | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=65) |
| IT-Q-C1 | 66 | [IT-QUANG] C10 Thứ tự lựa chọn | DRAFT | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=66) |
| IT-Q-C1 | 67 | [IT-QUANG] C11 Công việc sai định dạng | DRAFT | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=67) |
| IT-Q-C1 | 68 | [IT-QUANG] C12 Lựa chọn sai định dạng | DRAFT | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=68) |
| IT-Q-C1 | 69 | [IT-QUANG] C13 &lt;b&gt;Chi tiết&lt;/b&gt; | DRAFT | [View](http://localhost:8080/ams/teacher/assignments?mode=view&assignmentId=69) |

## Seed and verification

The new script is `database/seed_integration_quang.sql`. Run it only against local `ams_db`, with psql's `ON_ERROR_STOP=1`. It inserts tagged fixtures in one transaction and prints their IDs. A second run skips existing tagged rows. Collision guards abort if a fixture account or Class belongs to an unexpected context.

Verified on 05 October 2026:

- Every SQL statement validated in a rollback transaction before the actual insert transaction.
- Existing row hashes unchanged after fixture loading.
- A second run added or changed no rows.
- Added 84 rows across 15 existing tables; no schema changes.
- HTTP smoke checks: **57 Passed, 0 Failed**. These cover fixture sign-in, data-backed pages, read-only browsing, filters, ownership and selected boundaries; they are not the full 36-case Round 1.

The workbook remains Pending until each complete grouped case, including all variants, has been executed. Session expiry/revocation, account role changes, Class reassignment, database faults and timed invitation boundaries require separate runtime setup. No existing account, Class, Assignment or schema was changed by the seed.

## HTTP smoke evidence

| Check | Result |
| --- | --- |
| Anonymous access /ams/teacher/dashboard | Passed |
| Anonymous access /ams/teacher/assignments?classId=9 | Passed |
| Anonymous access /ams/teacher/assignments?mode=view&assignmentId=48 | Passed |
| Sign-in quang.it.a@example.test | Passed |
| Sign-in quang.it.b@example.test | Passed |
| Sign-in quang.it.detail@example.test | Passed |
| Sign-in quang.it.empty@example.test | Passed |
| Teacher A Dashboard aggregates | Passed |
| Teacher response has no-store | Passed |
| Empty Teacher Dashboard | Passed |
| Empty Teacher List | Passed |
| List default and class scope | Passed |
| Switch to owned A2 | Passed |
| Foreign Class blocked | Passed |
| Owner B can view B1 | Passed |
| Title Search qUiZ | Passed |
| Title Search ALPHA | Passed |
| Title Search 100%_ | Passed |
| Title Search % | Passed |
| Title Search _ | Passed |
| Title Search    Quiz Alpha    | Passed |
| Title Search  | Passed |
| Title Search     | Passed |
| Title Search 200 L | Passed |
| 201-character Search rejected | Passed |
| purpose=REGULAR | Passed |
| purpose=PRACTICE | Passed |
| responseType=ESSAY | Passed |
| responseType=OBJECTIVE | Passed |
| status=DRAFT | Passed |
| status=PUBLISHED | Passed |
| status=CANCELLED | Passed |
| Combined AND filters | Passed |
| Valid zero-match combination | Passed |
| Invalid classId abc | Passed |
| Invalid classId 0 | Passed |
| Invalid classId -1 | Passed |
| Invalid classId 99999999999999999999999 | Passed |
| Owned Objective Detail | Passed |
| Foreign Assignment blocked | Passed |
| List UTC+07 rollover | Passed |
| List title escaping | Passed |
| Detail fixture [IT-QUANG] C01 Báo cáo tiếng Việt | Passed |
| Detail fixture [IT-QUANG] C02 Trắc nghiệm Web | Passed |
| Detail fixture [IT-QUANG] C03 Luyện tập SQL | Passed |
| Detail fixture [IT-QUANG] C04 Bài đã hủy | Passed |
| Detail fixture [IT-QUANG] C05 Bài chưa mở | Passed |
| Detail fixture [IT-QUANG] C06 Báo cáo quá hạn | Passed |
| Detail fixture [IT-QUANG] C07 Giờ UTC cộng bảy | Passed |
| Detail UTC+07 rollover | Passed |
| Detail fixture [IT-QUANG] C08 Đề nháp rỗng | Passed |
| Detail fixture [IT-QUANG] C09 Thứ tự công việc | Passed |
| Detail fixture [IT-QUANG] C10 Thứ tự lựa chọn | Passed |
| Detail fixture [IT-QUANG] C11 Công việc sai định dạng | Passed |
| Detail fixture [IT-QUANG] C12 Lựa chọn sai định dạng | Passed |
| Detail fixture [IT-QUANG] C13 <b>Chi tiết</b> | Passed |
| Browsing leaves all academic rows unchanged | Passed |
