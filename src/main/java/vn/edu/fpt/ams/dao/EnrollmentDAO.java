package vn.edu.fpt.ams.dao;

import java.util.List;
import java.util.Optional;
import vn.edu.fpt.ams.model.ClassEnrollment;
import vn.edu.fpt.ams.model.EnrollmentStatus;

/** JDBC boundary for class placement episodes and active roster counts. */
public class EnrollmentDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement Enrollment JDBC access");
    }

    public Optional<ClassEnrollment> findById(long enrollmentId) { throw pending(); }
    public List<ClassEnrollment> findByClassId(long classId) { throw pending(); }
    public Optional<ClassEnrollment> findActiveByRegistrationId(long registrationId) { throw pending(); }
    public long countActiveStudents(long classId) { throw pending(); }
    public long insertEnrollment(ClassEnrollment enrollment) { throw pending(); }
    public void updateStatus(long enrollmentId, EnrollmentStatus status) { throw pending(); }
}
