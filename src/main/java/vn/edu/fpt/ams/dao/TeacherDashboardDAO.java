package vn.edu.fpt.ams.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import vn.edu.fpt.ams.dto.TeacherClassSummary;
import vn.edu.fpt.ams.util.DBConnection;

/** Read models for the teacher overview. Writes stay on the enrollment workflow. */
public class TeacherDashboardDAO {
    private static final String CLASS_SQL = """
            SELECT c.class_id, c.class_code, c.class_name, c.capacity,
                   s.subject_code, s.subject_name, t.term_code,
                   (SELECT count(*) FROM class_enrollments e
                     WHERE e.class_id = c.class_id AND e.status = 'ACTIVE') AS active_roster,
                   (SELECT count(*) FROM student_invitations i
                     WHERE i.class_id = c.class_id AND i.status = 'PENDING'
                       AND i.expires_at > CURRENT_TIMESTAMP) AS pending_invitations,
                   (SELECT count(*) FROM assignments a WHERE a.class_id = c.class_id) AS assignment_count
            FROM subject_classes c
            JOIN subject_terms st ON st.subject_term_id = c.subject_term_id
            JOIN subjects s ON s.subject_id = st.subject_id
            JOIN academic_terms t ON t.term_id = st.term_id
            WHERE c.teacher_id = ?
            ORDER BY t.term_code, c.class_code
            """;

    /** Đọc các lớp có teacher_id trùng giảng viên, kèm sĩ số active, lời mời còn hạn và số bài tập. */
    public List<TeacherClassSummary> listAssignedClasses(long teacherId) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(CLASS_SQL)) {
            statement.setLong(1, teacherId);
            try (ResultSet rows = statement.executeQuery()) {
                List<TeacherClassSummary> classes = new ArrayList<>();
                while (rows.next()) {
                    classes.add(new TeacherClassSummary(
                            rows.getLong("class_id"), rows.getString("class_code"),
                            rows.getString("class_name"), rows.getString("subject_code"),
                            rows.getString("subject_name"), rows.getString("term_code"),
                            rows.getInt("capacity"), rows.getLong("active_roster"),
                            rows.getLong("pending_invitations"), rows.getLong("assignment_count")));
                }
                return classes;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot load assigned classes", ex);
        }
    }

    /** Đếm bài nộp trên mọi bài tập của các lớp thuộc giảng viên này. */
    public long countSubmissions(long teacherId) {
        String sql = """
                SELECT count(*) FROM submissions sub
                JOIN assignments a ON a.assignment_id = sub.assignment_id
                JOIN subject_classes c ON c.class_id = a.class_id
                WHERE c.teacher_id = ?
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacherId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot count submissions", ex);
        }
    }
}
