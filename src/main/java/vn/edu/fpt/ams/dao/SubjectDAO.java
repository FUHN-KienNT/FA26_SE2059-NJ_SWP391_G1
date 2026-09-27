package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;
import java.sql.ResultSet;
import java.sql.SQLException;

/** JDBC operations for Subject; implementations are reserved for the next iteration. */
public class SubjectDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement transactional JDBC access according to SDS");
    }

public java.util.List<Subject> findAll(String keyword, SubjectStatus status) { throw pending(); }
public java.util.Optional<Subject> findById(long id) { throw pending(); }
public java.util.Optional<Subject> findByCode(String code) { throw pending(); }
public long insert(Subject subject) { throw pending(); }
public void update(Subject subject) { throw pending(); }
public void updateStatus(long id, SubjectStatus status) { throw pending(); }
public java.util.List<TeacherSubject> findTeacherSubjects(long teacherId, Boolean active) { throw pending(); }
public long assignTeacherToSubject(long teacherId, long subjectId) { throw pending(); }
public void deactivateTeacherSubject(long teacherSubjectId) { throw pending(); }

static Subject mapSubject(ResultSet rs) throws SQLException {
    return new Subject(rs.getLong("subject_id"), rs.getString("subject_code"),
            rs.getString("subject_name"), rs.getString("description"),
            SubjectStatus.valueOf(rs.getString("status")));
}

static TeacherSubject mapTeacherSubject(ResultSet rs) throws SQLException {
    return new TeacherSubject(rs.getLong("teacher_subject_id"), rs.getLong("teacher_id"),
            rs.getLong("subject_id"), rs.getBoolean("is_active"),
            ResultSetSupport.instant(rs, "assigned_at"),
            ResultSetSupport.instant(rs, "deactivated_at"));
}

}
