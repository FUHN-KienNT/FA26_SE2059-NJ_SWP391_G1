package vn.edu.fpt.ams.dao;

import java.util.List;
import java.util.Optional;
import java.sql.ResultSet;
import java.sql.SQLException;
import vn.edu.fpt.ams.model.ClassExpert;
import vn.edu.fpt.ams.model.ClassStatus;
import vn.edu.fpt.ams.model.SubjectClass;

/** JDBC boundary for class identity, capacity and Teacher assignment. */
public class SubjectClassDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement Subject Class JDBC access");
    }

    public Optional<SubjectClass> findById(long classId) { throw pending(); }
    public Optional<SubjectClass> findByCode(String classCode) { throw pending(); }
    public List<SubjectClass> findBySubjectTerm(long subjectTermId) { throw pending(); }
    public List<SubjectClass> findManagedByExpert(long expertId, Long subjectId, Long termId) { throw pending(); }
    public List<SubjectClass> findAssignedToTeacher(long teacherId) { throw pending(); }
    public long insert(SubjectClass subjectClass) { throw pending(); }
    public void update(SubjectClass subjectClass) { throw pending(); }
    public void updateStatus(long classId, ClassStatus status) { throw pending(); }
    /** Run under an authorization and transaction boundary; Teacher must be active and eligible. */
    public void updateTeacher(long classId, Long teacherId) { throw pending(); }
    public List<ClassExpert> findExpertsByClassId(long classId, Boolean active) { throw pending(); }
    public long assignExpert(long classId, long subjectId, long expertId) { throw pending(); }
    public void deactivateExpert(long classExpertId) { throw pending(); }

    static SubjectClass mapSubjectClass(ResultSet rs) throws SQLException {
        return new SubjectClass(rs.getLong("class_id"), rs.getLong("subject_term_id"),
                ResultSetSupport.nullableLong(rs, "teacher_id"), rs.getString("class_code"),
                rs.getString("class_name"), rs.getInt("capacity"),
                rs.getBoolean("registration_enabled"),
                ClassStatus.valueOf(rs.getString("status")));
    }

    static ClassExpert mapClassExpert(ResultSet rs) throws SQLException {
        return new ClassExpert(rs.getLong("class_expert_id"), rs.getLong("class_id"),
                rs.getLong("subject_id"), rs.getLong("expert_id"), rs.getBoolean("is_active"),
                ResultSetSupport.instant(rs, "assigned_at"),
                ResultSetSupport.instant(rs, "deactivated_at"));
    }
}
