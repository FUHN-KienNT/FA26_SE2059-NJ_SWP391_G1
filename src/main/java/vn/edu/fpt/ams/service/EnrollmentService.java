package vn.edu.fpt.ams.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import vn.edu.fpt.ams.dao.EnrollmentDAO;
import vn.edu.fpt.ams.dao.SubjectClassDAO;
import vn.edu.fpt.ams.dao.SubjectRegistrationDAO;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.dto.EnrollmentDTO;
import vn.edu.fpt.ams.dto.ImportResultDTO;
import vn.edu.fpt.ams.model.AccountStatus;
import vn.edu.fpt.ams.model.EnrollmentMethod;
import vn.edu.fpt.ams.model.EnrollmentStatus;
import vn.edu.fpt.ams.model.StudentInvitation;
import vn.edu.fpt.ams.model.UserAccount;
import vn.edu.fpt.ams.model.UserRole;
import vn.edu.fpt.ams.util.ValidationUtil;

/** Teacher-owned roster and import operations from SDS section 5. */
public class EnrollmentService {
    private final EnrollmentDAO enrollmentDAO = new EnrollmentDAO();
    private final SubjectRegistrationDAO registrationDAO = new SubjectRegistrationDAO();
    private final SubjectClassDAO classDAO = new SubjectClassDAO();
    private final UserDAO userDAO = new UserDAO();

    public List<EnrollmentDTO> listStudents(long teacherId, long classId) {
        requireOwnedClass(teacherId, classId);
        return enrollmentDAO.listRoster(classId);
    }

    public List<StudentInvitation> listInvitations(long teacherId, long classId) {
        requireOwnedClass(teacherId, classId);
        return enrollmentDAO.findInvitationsByClass(classId);
    }

    public long addStudent(long teacherId, long classId, long studentId) {
        requirePositive(teacherId, "Teacher ID");
        requirePositive(classId, "Class ID");
        requirePositive(studentId, "Student ID");
        return enrollmentDAO.placeExistingStudent(teacherId, classId, studentId, EnrollmentMethod.MANUAL);
    }

    public long addStudentByEmail(long teacherId, long classId, String email) {
        requirePositive(teacherId, "Teacher ID");
        requirePositive(classId, "Class ID");
        ValidationUtil.required(email, "Student email");
        UserAccount student = userDAO.findByEmail(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("No account uses that email"));
        if (student.role() != UserRole.STUDENT || student.accountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("An active Student account is required");
        }
        return addStudent(teacherId, classId, student.userId());
    }

    public ImportResultDTO importStudentsFromCsv(long teacherId, long classId, InputStream fileStream) {
        requirePositive(teacherId, "Teacher ID");
        requirePositive(classId, "Class ID");
        ValidationUtil.required(fileStream, "CSV upload");
        requireOwnedClass(teacherId, classId);
        int imported = 0;
        int skipped = 0;
        int rejected = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(fileStream, StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (!headerSkipped && trimmed.toLowerCase().startsWith("email")) {
                    headerSkipped = true;
                    continue;
                }
                headerSkipped = true;
                String email = trimmed.split(",", 2)[0].trim();
                if (email.isEmpty() || !email.contains("@")) {
                    rejected++;
                    continue;
                }
                try {
                    addStudentByEmail(teacherId, classId, email);
                    imported++;
                } catch (IllegalArgumentException ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("already has an active enrollment")) skipped++;
                    else rejected++;
                }
            }
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("The CSV file could not be read");
        }
        return new ImportResultDTO(imported, skipped, rejected);
    }

    public void updateEnrollmentStatus(long teacherId, long enrollmentId, EnrollmentStatus status) {
        requirePositive(teacherId, "Teacher ID");
        requirePositive(enrollmentId, "Enrollment ID");
        ValidationUtil.required(status, "Enrollment status");
        enrollmentDAO.updateOwnedStatus(teacherId, enrollmentId, status);
    }

    private void requireOwnedClass(long teacherId, long classId) {
        requirePositive(teacherId, "Teacher ID");
        requirePositive(classId, "Class ID");
        if (!enrollmentDAO.teacherOwnsClass(teacherId, classId)) {
            throw new IllegalArgumentException("This class is not assigned to the current teacher");
        }
    }

    private static void requirePositive(long id, String name) {
        if (id <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement authorized, transactional Enrollment workflow");
    }
}
