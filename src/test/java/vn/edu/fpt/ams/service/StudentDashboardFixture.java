package vn.edu.fpt.ams.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import vn.edu.fpt.ams.util.Jdbc;

/** Minimal isolated tables shared by H2 and PostgreSQL TEMP tests; never seeds application tables. */
final class StudentDashboardFixture {
    static void create(Connection c, boolean temporary) throws SQLException {
        String prefix = temporary ? "CREATE TEMP TABLE " : "CREATE TABLE ";
        for (String ddl : List.of(
                "users(user_id BIGINT PRIMARY KEY,full_name VARCHAR(100),email VARCHAR(100),role VARCHAR(20),account_status VARCHAR(20))",
                "subjects(subject_id BIGINT PRIMARY KEY,subject_code VARCHAR(30),subject_name VARCHAR(150))",
                "academic_terms(term_id BIGINT PRIMARY KEY,term_code VARCHAR(30),term_name VARCHAR(100),starts_at TIMESTAMP WITH TIME ZONE)",
                "subject_terms(subject_term_id BIGINT PRIMARY KEY,subject_id BIGINT,term_id BIGINT)",
                "subject_classes(class_id BIGINT PRIMARY KEY,subject_term_id BIGINT,class_code VARCHAR(30),teacher_id BIGINT,status VARCHAR(20))",
                "subject_registrations(registration_id BIGINT PRIMARY KEY,student_id BIGINT,subject_term_id BIGINT,status VARCHAR(20))",
                "class_enrollments(enrollment_id BIGINT PRIMARY KEY,registration_id BIGINT,class_id BIGINT,status VARCHAR(20))",
                "assignments(assignment_id BIGINT PRIMARY KEY,class_id BIGINT,title VARCHAR(200),assignment_purpose VARCHAR(20),response_type VARCHAR(20),status VARCHAR(20),available_from TIMESTAMP WITH TIME ZONE,due_at TIMESTAMP WITH TIME ZONE)",
                "submissions(submission_id BIGINT PRIMARY KEY,assignment_id BIGINT,enrollment_id BIGINT,attempt_number INTEGER,status VARCHAR(20),score DECIMAL(10,2),released_at TIMESTAMP WITH TIME ZONE)")) {
            try (var s = c.createStatement()) { s.execute(prefix + ddl); }
        }
        if (temporary) try (var s = c.createStatement()) { s.execute("SET search_path TO pg_temp"); }
        var now = Instant.now();
        Jdbc.update(c, "INSERT INTO users VALUES (1,'Student One','one@test.edu','STUDENT','ACTIVE'),"
                + "(2,'Student Two','two@test.edu','STUDENT','ACTIVE'),(3,'Teacher','teacher@test.edu','TEACHER','ACTIVE'),"
                + "(4,'Inactive','inactive@test.edu','STUDENT','INACTIVE'),(5,'New Student','new@test.edu','STUDENT','ACTIVE'),"
                + "(6,'Admin','admin@test.edu','ADMIN','ACTIVE'),(7,'Expert','expert@test.edu','EXPERT','ACTIVE')");
        Jdbc.update(c, "INSERT INTO subjects VALUES (101,'SWP391','Software Project'),(102,'DBI202','Databases')");
        Jdbc.update(c, "INSERT INTO academic_terms VALUES (11,'TERM_A','Term A',?),(12,'TERM_OLD','Previous Term',?)",
                now.minusSeconds(86400), now.minusSeconds(86400 * 100));
        Jdbc.update(c, "INSERT INTO subject_terms VALUES (1001,101,11),(1002,102,11),(1003,101,12)");
        Jdbc.update(c, "INSERT INTO subject_classes VALUES (10,1001,'OWN_CLASS',3,'ACTIVE'),"
                + "(20,1002,'FOREIGN_CLASS',3,'ACTIVE'),(30,1003,'HISTORICAL_CLASS',3,'INACTIVE'),"
                + "(40,1002,'INACTIVE_ENROLLMENT',3,'ACTIVE')");
        Jdbc.update(c, "INSERT INTO subject_registrations VALUES (1,1,1001,'ACTIVE'),(2,2,1002,'ACTIVE'),"
                + "(3,1,1003,'INACTIVE'),(4,1,1002,'INACTIVE')");
        Jdbc.update(c, "INSERT INTO class_enrollments VALUES (11,1,10,'ACTIVE'),(22,2,20,'ACTIVE'),"
                + "(33,3,30,'INACTIVE'),(44,4,40,'INACTIVE'),(55,1,20,'ACTIVE')");
        assignment(c, 100, 10, "Open Objective", "PUBLISHED", "OBJECTIVE", now.minusSeconds(86400), now.plusSeconds(86400));
        assignment(c, 101, 10, "Open Essay", "PUBLISHED", "ESSAY", now.minusSeconds(86400), now.plusSeconds(172800));
        assignment(c, 102, 10, "Future", "PUBLISHED", "OBJECTIVE", now.plusSeconds(86400), now.plusSeconds(172800));
        assignment(c, 103, 10, "Past", "PUBLISHED", "ESSAY", now.minusSeconds(259200), now.minusSeconds(172800));
        assignment(c, 104, 10, "Hidden Draft", "DRAFT", "OBJECTIVE", null, null);
        assignment(c, 105, 10, "Cancelled", "CANCELLED", "OBJECTIVE", now.minusSeconds(86400), now.plusSeconds(86400));
        assignment(c, 200, 20, "Foreign Work", "PUBLISHED", "OBJECTIVE", now.minusSeconds(86400), now.plusSeconds(86400));
        assignment(c, 300, 30, "Historical Released", "CANCELLED", "ESSAY", now.minusSeconds(86400 * 80), now.minusSeconds(86400 * 70));
        assignment(c, 400, 40, "Inactive Enrollment Work", "PUBLISHED", "OBJECTIVE", now.minusSeconds(86400), now.plusSeconds(86400));
        Jdbc.update(c, "INSERT INTO submissions VALUES (1,100,11,1,'SUBMITTED',NULL,NULL),(2,101,11,1,'GRADED',8,NULL),"
                + "(3,103,11,1,'RELEASED',7,?),(4,300,33,1,'RELEASED',9,?),"
                + "(5,100,11,2,'IN_PROGRESS',NULL,NULL),(6,100,11,3,'VOID',NULL,NULL),"
                + "(7,200,22,1,'RELEASED',99,?),(8,100,11,4,'RELEASED',4,?),"
                + "(9,200,55,1,'RELEASED',88,?),(10,200,11,1,'RELEASED',77,?)",
                now.minusSeconds(600), now.minusSeconds(300), now.minusSeconds(60),
                now.plusSeconds(86400), now.minusSeconds(60), now.minusSeconds(60));
    }

    static void assignment(Connection c, long id, long classId, String title, String status,
            String response, Instant available, Instant due) throws SQLException {
        Jdbc.update(c, "INSERT INTO assignments VALUES (?,?,?,'REGULAR',?,?,?,?)",
                id, classId, title, response, status, available, due);
    }

    static String snapshot(Connection c) throws SQLException {
        var out = new StringBuilder();
        for (String table : List.of("users", "subject_registrations", "class_enrollments", "assignments", "submissions")) {
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT * FROM " + table + " ORDER BY 1")) {
                out.append(table);
                while (r.next()) for (int col = 1; col <= r.getMetaData().getColumnCount(); col++)
                    out.append('|').append(r.getString(col));
            }
        }
        return out.toString();
    }
}
