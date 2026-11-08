package vn.edu.fpt.ams.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import vn.edu.fpt.ams.dto.StudentClassFilter;
import vn.edu.fpt.ams.dto.StudentClassPage.AssignmentRow;
import vn.edu.fpt.ams.dto.StudentClassPage.ClassInfo;
import vn.edu.fpt.ams.dto.StudentClassPage.Counts;
import vn.edu.fpt.ams.util.Jdbc;

/** Read-only, student-scoped queries for My Class Detail. */
public final class StudentClassDAO {
    // Each query scopes both the visible Assignment and the latest attempt to this student.
    // Historical attempts may belong to a former enrollment in this same Class/Subject Term.
    private static final String VISIBLE_ASSIGNMENTS =
            """
            WITH visible AS (
                SELECT a.assignment_id,a.title,a.assignment_purpose,a.response_type,a.maximum_score,
                    a.available_from,a.due_at,a.status,
                    latest.submission_id,latest.attempt_number,latest.submitted_at,
                    CASE WHEN latest.status='RELEASED'
                              AND (latest.released_at IS NULL OR latest.released_at>?) THEN 'GRADED'
                         ELSE COALESCE(latest.status,'NOT_STARTED') END AS work_status
                FROM assignments a
                JOIN subject_classes c ON c.class_id=a.class_id
                LEFT JOIN LATERAL (
                    SELECT sub.submission_id,sub.attempt_number,sub.status,sub.submitted_at,sub.released_at
                    FROM submissions sub
                    JOIN class_enrollments e ON e.enrollment_id=sub.enrollment_id AND e.class_id=a.class_id
                    JOIN subject_registrations r ON r.registration_id=e.registration_id
                        AND r.subject_term_id=c.subject_term_id
                    WHERE sub.assignment_id=a.assignment_id AND r.student_id=?
                    ORDER BY sub.attempt_number DESC,sub.submission_id DESC LIMIT 1
                ) latest ON TRUE
                WHERE a.class_id=? AND a.status='PUBLISHED' AND a.published_at IS NOT NULL
                    AND a.due_at>CURRENT_TIMESTAMP AND a.closed_at IS NULL
                    AND a.assignment_purpose IN ('REGULAR','PRACTICE') AND a.response_type IN ('ESSAY','OBJECTIVE')
                    AND c.status='ACTIVE' AND EXISTS (
                        SELECT 1 FROM class_enrollments e
                        JOIN subject_registrations r ON r.registration_id=e.registration_id
                            AND r.subject_term_id=c.subject_term_id
                        WHERE e.class_id=c.class_id AND r.student_id=?
                            AND e.status='ACTIVE' AND r.status='ACTIVE')
            )
            """;

    public boolean activeStudent(Connection c, long studentId) throws SQLException {
        return Jdbc.scalar(
                        c,
                        "SELECT COUNT(*) FROM users WHERE user_id=?"
                                + " AND role='STUDENT' AND account_status='ACTIVE'",
                        studentId)
                == 1;
    }

    public Instant currentTime(Connection c) throws SQLException {
        try (var p = c.prepareStatement("SELECT CURRENT_TIMESTAMP");
                var r = p.executeQuery()) {
            r.next();

            return r.getObject(1, OffsetDateTime.class).toInstant();
        }
    }

    public List<ClassInfo> classes(Connection c, long studentId) throws SQLException {
        String sql =
                """
                SELECT DISTINCT c.class_id,c.subject_term_id,c.class_code,c.class_name,
                    s.subject_code,s.subject_name,t.term_code,t.term_name,u.full_name,t.starts_at
                FROM class_enrollments e
                JOIN subject_registrations r ON r.registration_id=e.registration_id
                JOIN subject_classes c ON c.class_id=e.class_id AND c.subject_term_id=r.subject_term_id
                JOIN subject_terms st ON st.subject_term_id=c.subject_term_id
                JOIN subjects s ON s.subject_id=st.subject_id
                JOIN academic_terms t ON t.term_id=st.term_id
                LEFT JOIN users u ON u.user_id=c.teacher_id
                WHERE r.student_id=? AND r.status='ACTIVE' AND e.status='ACTIVE' AND c.status='ACTIVE'
                ORDER BY t.starts_at DESC,s.subject_code,c.class_code,c.class_id
                """;

        try (var p = Jdbc.statement(c, sql, studentId);
                var r = p.executeQuery()) {
            var out = new ArrayList<ClassInfo>();

            while (r.next())
                out.add(
                        new ClassInfo(
                                r.getLong(1),
                                r.getLong(2),
                                r.getString(3),
                                r.getString(4),
                                r.getString(5),
                                r.getString(6),
                                r.getString(7),
                                r.getString(8),
                                r.getString(9)));

            return out;
        }
    }

    public Counts counts(Connection c, long studentId, long classId, Instant now)
            throws SQLException {
        String sql =
                VISIBLE_ASSIGNMENTS
                        + """
                SELECT COUNT(*),
                    COUNT(*) FILTER (WHERE status='PUBLISHED' AND available_from<=? AND due_at>?),
                    COUNT(*) FILTER (WHERE work_status='NOT_STARTED'),
                    COUNT(*) FILTER (WHERE work_status IN ('SUBMITTED','GRADED','RELEASED'))
                FROM visible
                """;

        try (var p = Jdbc.statement(c, sql, now, studentId, classId, studentId, now, now);
                var r = p.executeQuery()) {
            r.next();

            return new Counts(r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4));
        }
    }

    public long count(
            Connection c, long studentId, long classId, Instant now, StudentClassFilter filter)
            throws SQLException {
        var args = arguments(studentId, classId, now);
        String sql = VISIBLE_ASSIGNMENTS + "SELECT COUNT(*) FROM visible" + where(filter, args);

        return Jdbc.scalar(c, sql, args.toArray());
    }

    public List<AssignmentRow> assignments(
            Connection c, long studentId, long classId, Instant now, StudentClassFilter filter)
            throws SQLException {
        var args = arguments(studentId, classId, now);
        String sql =
                VISIBLE_ASSIGNMENTS
                        + """
                SELECT assignment_id,title,assignment_purpose,response_type,maximum_score,available_from,due_at,
                    status,submission_id,attempt_number,work_status,submitted_at
                FROM visible
                """
                        + where(filter, args)
                        + orderBy(filter)
                        + " LIMIT ? OFFSET ?";

        args.add(StudentClassFilter.PAGE_SIZE);
        args.add((long) (filter.page() - 1) * StudentClassFilter.PAGE_SIZE);

        try (var p = Jdbc.statement(c, sql, args.toArray());
                var r = p.executeQuery()) {
            var out = new ArrayList<AssignmentRow>();

            while (r.next())
                out.add(
                        new AssignmentRow(
                                r.getLong(1),
                                r.getString(2),
                                r.getString(3),
                                r.getString(4),
                                r.getBigDecimal(5),
                                instant(r, 6),
                                instant(r, 7),
                                r.getString(8),
                                r.getLong(9),
                                r.getInt(10),
                                r.getString(11),
                                instant(r, 12)));

            return out;
        }
    }

    private static ArrayList<Object> arguments(long studentId, long classId, Instant now) {
        return new ArrayList<>(List.of(now, studentId, classId, studentId));
    }

    private static String orderBy(StudentClassFilter filter) {
        return switch (filter.sort()) {
            case "OPEN_DESC" -> " ORDER BY available_from DESC NULLS LAST,assignment_id DESC";
            case "OPEN_ASC" -> " ORDER BY available_from ASC NULLS LAST,assignment_id DESC";
            case "DUE_DESC" -> " ORDER BY due_at DESC NULLS LAST,assignment_id DESC";
            case "DUE_ASC" -> " ORDER BY due_at ASC NULLS LAST,assignment_id DESC";
            default -> " ORDER BY assignment_id DESC";
        };
    }

    private static String where(StudentClassFilter filter, List<Object> args) {
        var sql = new StringBuilder(" WHERE 1=1");

        if (!filter.search().isEmpty()) {
            // POSITION treats %, _ and backslashes literally instead of as LIKE wildcards.
            sql.append(" AND POSITION(LOWER(?) IN LOWER(title))>0");
            args.add(filter.search());
        }

        if (!filter.responseType().isEmpty()) {
            sql.append(" AND response_type=?");
            args.add(filter.responseType());
        }

        if ("DONE".equals(filter.status())) {
            sql.append(" AND work_status IN ('SUBMITTED','GRADED','RELEASED')");
        } else if (!filter.status().isEmpty()) {
            sql.append(" AND work_status=?");
            args.add(filter.status());
        }

        return sql.toString();
    }

    private static Instant instant(ResultSet rows, int column) throws SQLException {
        var value = rows.getObject(column, OffsetDateTime.class);

        return value == null ? null : value.toInstant();
    }
}
