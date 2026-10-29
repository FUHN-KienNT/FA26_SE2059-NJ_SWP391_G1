package vn.edu.fpt.ams.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import vn.edu.fpt.ams.dao.EnrollmentDAO;
import vn.edu.fpt.ams.dao.SubjectClassDAO;
import vn.edu.fpt.ams.dao.SubjectRegistrationDAO;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.dto.EnrollmentDTO;
import vn.edu.fpt.ams.dto.ImportResultDTO;
import vn.edu.fpt.ams.dto.ImportLineResult;
import vn.edu.fpt.ams.dto.ImportLineResult.Outcome;
import vn.edu.fpt.ams.model.AccountStatus;
import vn.edu.fpt.ams.model.EnrollmentMethod;
import vn.edu.fpt.ams.model.EnrollmentStatus;
import vn.edu.fpt.ams.model.StudentInvitation;
import vn.edu.fpt.ams.model.UserAccount;
import vn.edu.fpt.ams.model.UserRole;
import vn.edu.fpt.ams.util.ValidationUtil;

/** Teacher-owned roster and import operations from SDS section 5. */
public class EnrollmentService {
    private final EnrollmentDAO enrollmentDAO;
    private final SubjectRegistrationDAO registrationDAO = new SubjectRegistrationDAO();
    private final SubjectClassDAO classDAO = new SubjectClassDAO();
    private final UserDAO userDAO = new UserDAO();

    /** Use the application's JDBC enrollment implementation. */
    public EnrollmentService() { this(new EnrollmentDAO()); }

    /** Supply the enrollment boundary for isolated import tests. */
    EnrollmentService(EnrollmentDAO enrollmentDAO) { this.enrollmentDAO = enrollmentDAO; }

    public List<EnrollmentDTO> listStudents(long teacherId, long classId) {
        requireOwnedClass(teacherId, classId);
        return enrollmentDAO.listRoster(classId);
    }

    public List<StudentInvitation> listInvitations(long teacherId, long classId) {
        requireOwnedClass(teacherId, classId);
        return enrollmentDAO.findInvitationsByClass(classId);
    }

    public long addStudent(long teacherId, long classId, long studentId) {
        requirePositive(teacherId, "Mã giảng viên");
        requirePositive(classId, "Mã lớp");
        requirePositive(studentId, "Mã sinh viên");
        return enrollmentDAO.placeExistingStudent(teacherId, classId, studentId, EnrollmentMethod.MANUAL);
    }

    public long addStudentByEmail(long teacherId, long classId, String email) {
        requirePositive(teacherId, "Mã giảng viên");
        requirePositive(classId, "Mã lớp");
        ValidationUtil.required(email, "Email sinh viên");
        UserAccount student = userDAO.findByEmail(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("Không có tài khoản sử dụng email này"));
        if (student.role() != UserRole.STUDENT || student.accountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Cần tài khoản sinh viên đang hoạt động");
        }
        return addStudent(teacherId, classId, student.userId());
    }

    /** Import existing accounts and report each data row with its outcome and reason. */
    public ImportResultDTO importStudentsFromCsv(long teacherId, long classId, InputStream fileStream) {
        requirePositive(teacherId, "Mã giảng viên");
        requirePositive(classId, "Mã lớp");
        ValidationUtil.required(fileStream, "Tệp CSV");
        requireOwnedClass(teacherId, classId);
        int imported = 0;
        int skipped = 0;
        int rejected = 0;
        var results = new ArrayList<ImportLineResult>();
        var seen = new HashSet<String>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(fileStream, StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith("\uFEFF")) line = line.substring(1);
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                String email = trimmed.split(",", 2)[0].trim();
                if (email.length() >= 2 && email.startsWith("\"") && email.endsWith("\"")) {
                    email = email.substring(1, email.length() - 1).trim();
                }
                if (!headerSkipped && email.equalsIgnoreCase("email")) {
                    headerSkipped = true;
                    continue;
                }
                headerSkipped = true;
                if (email.length() > 100 || !email.matches("[^\\s@,\"]+@[^\\s@,\"]+\\.[^\\s@,\"]+")) {
                    rejected++;
                    results.add(new ImportLineResult(lineNumber, email, Outcome.REJECTED, "Email không hợp lệ."));
                    continue;
                }
                if (!seen.add(email.toLowerCase(Locale.ROOT))) {
                    skipped++;
                    results.add(new ImportLineResult(lineNumber, email, Outcome.SKIPPED, "Email bị trùng trong tệp CSV."));
                    continue;
                }
                try {
                    addStudentByEmail(teacherId, classId, email);
                    imported++;
                    results.add(new ImportLineResult(lineNumber, email, Outcome.IMPORTED, "Đã thêm sinh viên vào lớp."));
                } catch (IllegalArgumentException ex) {
                    boolean alreadyEnrolled = ex.getMessage() != null && ex.getMessage().contains("already has an active enrollment");
                    if (alreadyEnrolled) skipped++;
                    else rejected++;
                    results.add(new ImportLineResult(lineNumber, email,
                            alreadyEnrolled ? Outcome.SKIPPED : Outcome.REJECTED, importReason(ex.getMessage())));
                }
            }
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Không thể đọc tệp CSV");
        }
        return new ImportResultDTO(imported, skipped, rejected, results);
    }

    /** Explain expected account and placement failures in the import result language. */
    private static String importReason(String reason) {
        if (reason == null) return "Không thể thêm sinh viên vào lớp.";
        return switch (reason) {
            case "The class has no available seat" -> "Lớp đã đủ sĩ số.";
            case "This student already has an active enrollment for the subject term" -> "Sinh viên đã có ghi danh đang hoạt động cho môn học trong học kỳ này.";
            case "An active Student account is required" -> "Cần tài khoản sinh viên đang hoạt động.";
            case "Students can be added only to an active class" -> "Chỉ có thể thêm sinh viên vào lớp đang hoạt động.";
            case "This class is not assigned to the current teacher" -> "Lớp này không thuộc phân công của giảng viên hiện tại.";
            default -> reason;
        };
    }

    public void updateEnrollmentStatus(long teacherId, long enrollmentId, EnrollmentStatus status) {
        requirePositive(teacherId, "Mã giảng viên");
        requirePositive(enrollmentId, "Mã tham gia lớp");
        ValidationUtil.required(status, "Trạng thái sinh viên trong lớp");
        enrollmentDAO.updateOwnedStatus(teacherId, enrollmentId, status);
    }

    private void requireOwnedClass(long teacherId, long classId) {
        requirePositive(teacherId, "Mã giảng viên");
        requirePositive(classId, "Mã lớp");
        if (!enrollmentDAO.teacherOwnsClass(teacherId, classId)) {
            throw new IllegalArgumentException("Lớp này không thuộc phân công của giảng viên hiện tại");
        }
    }

    private static void requirePositive(long id, String name) {
        if (id <= 0) throw new IllegalArgumentException(name + " phải là số nguyên dương");
    }

    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement authorized, transactional Enrollment workflow");
    }
}
