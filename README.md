# AMS – Assignment Management System

## Project Overview
This repository currently contains the initial codebase and modules corresponding to features that have Detailed Code Design in the current SDS. Full implementation of all AMS use cases is not part of this iteration.

## Technology Stack
Java 17, Maven WAR, Jakarta Servlet 6, JSP, JDBC, PostgreSQL, Tomcat 10.1.

## Architecture
Browser → JSP → Servlet → Service → DAO → JDBC → PostgreSQL. Services own business decisions; DAOs own SQL. No Spring or JPA.

## Current Implemented Code Modules
System Administration, Subject Management, Assignment Template Management, Assignment Management, and Subject Class & Enrollment Management have models, DTOs, DAO contracts, services, Servlet routes, and initial JSP screens. These are code skeletons: mutating workflows and most reads throw TODO exceptions or return HTTP 501 until authorization, transactions, and JDBC mapping are finished. They must not be used as a production application.

The new Subject Class module has Expert routes for class setup and Teacher assignment. The Enrollment module has Teacher routes for assigned classes, rosters, adding a Student, CSV import, and enrollment status. The SDS concepts `Controller`, `Repository`, `BaseDAO`, and `Role` map to this project's `Servlet`, `DAO`, `DBConnection`, and `UserRole` conventions. The existing `EmailClient` is reserved for any post-commit Teacher assignment notification.

## Package Structure
`vn.edu.fpt.ams`: `controller`, `service`, `dao`, `model`, `dto`, `filter`, `integration`, `util`.

## Database Setup
Create an empty PostgreSQL database named `ams`; run `database/schema.sql`. `database/seed.sql` intentionally has no credentials. The schema includes `subject_classes`, `subject_registrations`, and `class_enrollments`. A class may have no Teacher during setup. New placement and Assignment publication require an active Teacher. Role eligibility, matching Subject Terms across registration and placement, capacity, and cross-table ownership need transactional application checks in later work. This script is for a fresh database; an already initialized database needs a separately reviewed migration.

## Configuration
Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` as environment variables. Alternatively copy `src/main/resources/application.properties.example` to a local ignored `application.properties` and fill in local values. Never commit secrets. Staff use local AMS credentials; Students use verified Google authentication when implemented.

## Build Instructions
Run `mvn clean test` and `mvn clean package` with JDK 17. The generated WAR is `target/ams.war` and is ignored by Git.

## Tomcat Deployment
Copy `target/ams.war` to Tomcat 10.1 `webapps`. The authentication workflow is not implemented yet, so protected routes return 401 without a valid session. This is intentional for the initial skeleton.

## Pending Modules / Future Iterations
Complete authentication, transactional JDBC, Service business rules, ownership checks, data-backed JSP rendering, CSV parsing, XLSX import if retained by the SRS, and meaningful tests. The class Teacher assignment flow must verify active Expert responsibility and an active TEACHER before updating; any email notification must occur after a successful commit. Student import must lock/check capacity, reuse a unique Subject Registration, and prevent a second active Class Enrollment. Additional modules will be added after the remaining SDS Detailed Code Design is finalized. Submission, Practice execution, Evaluation, Student self-registration, and Student class change are outside the current implemented scope. Payment and time tracking are not AMS modules.
