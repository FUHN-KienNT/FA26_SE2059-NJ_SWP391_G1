package vn.edu.fpt.ams.dao;

import java.util.Optional;
import java.util.List;
import java.sql.ResultSet;
import java.sql.SQLException;
import vn.edu.fpt.ams.model.SubjectRegistration;
import vn.edu.fpt.ams.model.RegistrationStatus;

/** JDBC boundary for the unique Student + Subject Term registration. */
public class SubjectRegistrationDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement Subject Registration JDBC access");
    }

    public Optional<SubjectRegistration> findById(long registrationId) { throw pending(); }
    public Optional<SubjectRegistration> findByStudentAndTerm(long studentId, long subjectTermId) { throw pending(); }
    public List<SubjectRegistration> findBySubjectTerm(long subjectTermId, RegistrationStatus status) { throw pending(); }
    public long insert(SubjectRegistration registration) { throw pending(); }
    public void reactivate(long registrationId) { throw pending(); }
    public void deactivate(long registrationId) { throw pending(); }

    static SubjectRegistration mapRegistration(ResultSet rs) throws SQLException {
        return new SubjectRegistration(rs.getLong("registration_id"), rs.getLong("student_id"),
                rs.getLong("subject_term_id"), RegistrationStatus.valueOf(rs.getString("status")),
                ResultSetSupport.instant(rs, "registered_at"),
                ResultSetSupport.instant(rs, "deactivated_at"));
    }
}
