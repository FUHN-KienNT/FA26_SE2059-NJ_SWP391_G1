package vn.edu.fpt.ams.service;

import java.io.InputStream;
import java.util.List;
import vn.edu.fpt.ams.dao.EnrollmentDAO;
import vn.edu.fpt.ams.dao.SubjectClassDAO;
import vn.edu.fpt.ams.dao.SubjectRegistrationDAO;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.dto.EnrollmentDTO;
import vn.edu.fpt.ams.dto.ImportResultDTO;
import vn.edu.fpt.ams.model.EnrollmentStatus;
import vn.edu.fpt.ams.util.ValidationUtil;

/** Teacher-owned roster and import operations from SDS section 5. */
public class EnrollmentService {
    private final EnrollmentDAO enrollmentDAO = new EnrollmentDAO();
    private final SubjectRegistrationDAO registrationDAO = new SubjectRegistrationDAO();
    private final SubjectClassDAO classDAO = new SubjectClassDAO();
    private final UserDAO userDAO = new UserDAO();

    public List<EnrollmentDTO> listStudents(long teacherId, long classId) {
        requirePositive(teacherId, "Teacher ID");
        requirePositive(classId, "Class ID");
        // TODO: verify that teacherId is the current Teacher and join roster with User.
        throw pending();
    }

    public long addStudent(long teacherId, long classId, long studentId) {
        requirePositive(teacherId, "Teacher ID");
        requirePositive(classId, "Class ID");
        requirePositive(studentId, "Student ID");
        // TODO: in one transaction, lock class and registration; verify active Teacher,
        // class status, Subject Term registration window, capacity, Student role and matching Subject Term.
        // Reuse the unique Student + Subject Term registration and avoid a second active placement.
        throw pending();
    }

    public ImportResultDTO importStudentsFromCsv(long teacherId, long classId, InputStream fileStream) {
        requirePositive(teacherId, "Teacher ID");
        requirePositive(classId, "Class ID");
        ValidationUtil.required(fileStream, "CSV upload");
        // TODO: parse and validate rows, then follow SDS: count active students,
        // find/create Student through UserDAO, find/create SubjectRegistration,
        // and insert ClassEnrollment atomically without overflow or duplicate placement.
        // A new Student remains Google-only; do not create a local password.
        throw pending();
    }

    public void updateEnrollmentStatus(long teacherId, long enrollmentId, EnrollmentStatus status) {
        requirePositive(teacherId, "Teacher ID");
        requirePositive(enrollmentId, "Enrollment ID");
        ValidationUtil.required(status, "Enrollment status");
        // TODO: verify current Teacher, active registration and class eligibility on reactivation.
        // Preserve prior placement episodes and related academic history.
        throw pending();
    }

    private static void requirePositive(long id, String name) {
        if (id <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement authorized, transactional Enrollment workflow");
    }
}
