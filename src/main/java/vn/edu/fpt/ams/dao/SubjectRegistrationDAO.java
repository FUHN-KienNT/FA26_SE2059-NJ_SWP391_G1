package vn.edu.fpt.ams.dao;

import java.util.Optional;
import vn.edu.fpt.ams.model.SubjectRegistration;

/** JDBC boundary for the unique Student + Subject Term registration. */
public class SubjectRegistrationDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement Subject Registration JDBC access");
    }

    public Optional<SubjectRegistration> findById(long registrationId) { throw pending(); }
    public Optional<SubjectRegistration> findByStudentAndTerm(long studentId, long subjectTermId) { throw pending(); }
    public long insert(SubjectRegistration registration) { throw pending(); }
    public void reactivate(long registrationId) { throw pending(); }
    public void deactivate(long registrationId) { throw pending(); }
}
