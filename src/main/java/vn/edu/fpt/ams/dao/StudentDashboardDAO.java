package vn.edu.fpt.ams.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import vn.edu.fpt.ams.dto.StudentDashboardDTO.*;
import vn.edu.fpt.ams.util.Jdbc;
import vn.edu.fpt.ams.util.GroupAssignmentSchema;
import vn.edu.fpt.ams.util.GroupStudentAccess;

/** Read-only queries. Every academic query is restricted to the authenticated Student. */
public final class StudentDashboardDAO {
    public static final int PREVIEW_LIMIT = 5;
    public record Student(String fullName, String email) {}

    private static final String ACTIVE_CLASSES = """
            FROM class_enrollments e
            JOIN subject_registrations r ON r.registration_id=e.registration_id
            JOIN subject_classes c ON c.class_id=e.class_id AND c.subject_term_id=r.subject_term_id
            WHERE r.student_id=? AND r.status='ACTIVE' AND e.status='ACTIVE' AND c.status='ACTIVE'
            """;

    private static final String ASSIGNMENT_ACCESS = """
            EXISTS (SELECT 1 FROM class_enrollments e
                JOIN subject_registrations r ON r.registration_id=e.registration_id
                JOIN subject_classes c ON c.class_id=e.class_id AND c.subject_term_id=r.subject_term_id
                WHERE r.student_id=? AND r.status='ACTIVE' AND e.status='ACTIVE'
                    AND c.status='ACTIVE' AND c.class_id=a.class_id)
            """;

    // Saved work remains visible after enrollment, registration or Class deactivation.
    // Match the original enrollment to both the Assignment's Class and Subject Term.
    private static final String OWNED_SUBMISSIONS = """
            FROM submissions sub
            JOIN assignments a ON a.assignment_id=sub.assignment_id
            JOIN class_enrollments e ON e.enrollment_id=sub.enrollment_id AND e.class_id=a.class_id
            JOIN subject_classes c ON c.class_id=e.class_id
            JOIN subject_registrations r ON r.registration_id=e.registration_id
                AND r.subject_term_id=c.subject_term_id
            WHERE r.student_id=? AND a.assignment_purpose='REGULAR'
            """;

    public Optional<Student> activeStudent(Connection c, long studentId) throws SQLException {
        try (var p = Jdbc.statement(c, "SELECT full_name,email FROM users WHERE user_id=?"
                + " AND role='STUDENT' AND account_status='ACTIVE'", studentId);
             var r = p.executeQuery()) {
            return r.next() ? Optional.of(new Student(r.getString(1), r.getString(2))) : Optional.empty();
        }
    }

    public Instant currentTime(Connection c) throws SQLException {
        try (var p = c.prepareStatement("SELECT CURRENT_TIMESTAMP"); var r = p.executeQuery()) {
            r.next();
            return r.getObject(1, OffsetDateTime.class).toInstant();
        }
    }

    public long subjectCount(Connection c, long student) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(DISTINCT st.subject_id) FROM subject_registrations r"
                + " JOIN subject_terms st ON st.subject_term_id=r.subject_term_id"
                + " WHERE r.student_id=? AND r.status='ACTIVE'", student);
    }

    public long classCount(Connection c, long student) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(DISTINCT c.class_id) " + ACTIVE_CLASSES, student);
    }

    public long assignmentCount(Connection c, long student) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM assignments a WHERE a.status='PUBLISHED' AND a.assignment_purpose='REGULAR' AND "
                + assignmentAccess(c), student);
    }

    public long submittedCount(Connection c, long student) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) " + ownedSubmissions(c)
                + " AND sub.status IN ('SUBMITTED','GRADED','RELEASED')", student);
    }

    public long releasedCount(Connection c, long student, Instant now) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) " + ownedSubmissions(c)
                + " AND sub.status='RELEASED' AND sub.released_at<=? AND " + score(c)
                + " IS NOT NULL" + groupReleaseGate(c), student, now);
    }

    public List<ClassOverview> classes(Connection c, long student) throws SQLException {
        String sql = """
                SELECT DISTINCT s.subject_code,s.subject_name,c.class_code,t.term_code,t.term_name,
                    u.full_name,t.starts_at,c.class_id
                FROM class_enrollments e
                JOIN subject_registrations r ON r.registration_id=e.registration_id
                JOIN subject_classes c ON c.class_id=e.class_id AND c.subject_term_id=r.subject_term_id
                JOIN subject_terms st ON st.subject_term_id=c.subject_term_id
                JOIN subjects s ON s.subject_id=st.subject_id
                JOIN academic_terms t ON t.term_id=st.term_id
                LEFT JOIN users u ON u.user_id=c.teacher_id
                WHERE r.student_id=? AND r.status='ACTIVE' AND e.status='ACTIVE' AND c.status='ACTIVE'
                ORDER BY t.starts_at DESC,s.subject_code,c.class_code
                """;
        try (var p = Jdbc.statement(c, sql, student)) {
            p.setMaxRows(PREVIEW_LIMIT);
            try (var r = p.executeQuery()) {
                var out = new ArrayList<ClassOverview>();
                while (r.next()) out.add(new ClassOverview(r.getLong("class_id"), r.getString(1), r.getString(2),
                        r.getString(3), r.getString(4), r.getString(5), r.getString(6)));
                return out;
            }
        }
    }

    public List<OpenAssignment> openAssignments(Connection c, long student, Instant now) throws SQLException {
        String sql = "SELECT a.assignment_id,a.title,c.class_code,a.response_type,a.due_at"
                + " FROM assignments a JOIN subject_classes c ON c.class_id=a.class_id"
                + " WHERE a.status='PUBLISHED' AND a.assignment_purpose='REGULAR' AND " + assignmentAccess(c)
                + " AND a.available_from<=? AND a.due_at>? ORDER BY a.due_at,a.assignment_id";
        try (var p = Jdbc.statement(c, sql, student, now, now)) {
            p.setMaxRows(PREVIEW_LIMIT);
            try (var r = p.executeQuery()) {
                var out = new ArrayList<OpenAssignment>();
                while (r.next()) out.add(new OpenAssignment(r.getLong(1), r.getString(2),
                        r.getString(3), r.getString(4), r.getObject(5, OffsetDateTime.class).toInstant()));
                return out;
            }
        }
    }

    public List<ReleasedResult> results(Connection c, long student, Instant now) throws SQLException {
        String sql = "SELECT a.title,c.class_code,sub.attempt_number," + score(c) + ",sub.released_at,a.assignment_id,sub.submission_id "
                + ownedSubmissions(c)
                + " AND sub.status='RELEASED' AND sub.released_at<=? AND " + score(c) + " IS NOT NULL"
                + groupReleaseGate(c)
                + " ORDER BY sub.released_at DESC,sub.submission_id DESC";

        try (var p = Jdbc.statement(c, sql, student, now)) {
            p.setMaxRows(PREVIEW_LIMIT);

            try (var r = p.executeQuery()) {
                var out = new ArrayList<ReleasedResult>();

                while (r.next())
                    out.add(new ReleasedResult(
                            r.getString(1),
                            r.getString(2),
                            r.getInt(3),
                            r.getBigDecimal(4),
                            r.getObject(5, OffsetDateTime.class).toInstant(),
                            r.getLong(6),
                            r.getLong(7)));

                return out;
            }
        }
    }

    private static String assignmentAccess(Connection c) throws SQLException {
        if (!GroupAssignmentSchema.installed(c)) return ASSIGNMENT_ACCESS;
        return ASSIGNMENT_ACCESS.replace("AND c.class_id=a.class_id)", "AND c.class_id=a.class_id AND "
                + GroupStudentAccess.audience("a", "r.student_id", "e.enrollment_id") + ")");
    }

    private static String ownedSubmissions(Connection c) throws SQLException {
        if (!GroupAssignmentSchema.installed(c)) return OWNED_SUBMISSIONS;
        return OWNED_SUBMISSIONS.replace("WHERE r.student_id=?", """
                JOIN users viewer ON viewer.user_id=? AND viewer.role='STUDENT' AND viewer.account_status='ACTIVE'
                LEFT JOIN assignment_group_results group_result ON group_result.target_id=sub.assignment_group_target_id
                    AND group_result.submission_id=sub.submission_id AND group_result.student_id=viewer.user_id
                WHERE
                """ + GroupStudentAccess.owns("sub", "r.student_id", "viewer.user_id"));
    }

    private static String score(Connection c) throws SQLException {
        return GroupAssignmentSchema.installed(c)
                ? "CASE WHEN sub.assignment_group_target_id IS NULL THEN sub.score ELSE group_result.final_score END"
                : "sub.score";
    }

    private static String groupReleaseGate(Connection c) throws SQLException {
        return GroupAssignmentSchema.installed(c)
                ? " AND (sub.assignment_group_target_id IS NULL OR group_result.released_at<=CURRENT_TIMESTAMP)" : "";
    }
}
