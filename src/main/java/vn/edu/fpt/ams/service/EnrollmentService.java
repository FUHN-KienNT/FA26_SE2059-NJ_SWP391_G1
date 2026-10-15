package vn.edu.fpt.ams.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import vn.edu.fpt.ams.dao.EnrollmentDAO;
import vn.edu.fpt.ams.dao.SubjectClassDAO;
import vn.edu.fpt.ams.dao.SubjectRegistrationDAO;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.dto.EnrollmentDTO;
import vn.edu.fpt.ams.dto.ImportLineResult;
import vn.edu.fpt.ams.dto.ImportOutcome;
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

    /** Trả roster của một lớp sau khi kiểm tra lớp thuộc giảng viên này. */
    public List<EnrollmentDTO> listStudents(long teacherId, long classId) {
        requireOwnedClass(teacherId, classId);
        return enrollmentDAO.listRoster(classId);
    }

    /** Trả lời mời của một lớp sau khi kiểm tra lớp thuộc giảng viên này. */
    public List<StudentInvitation> listInvitations(long teacherId, long classId) {
        requireOwnedClass(teacherId, classId);
        return enrollmentDAO.findInvitationsByClass(classId);
    }

    /** Ghi danh một sinh viên đã có tài khoản vào lớp, theo cách thêm tay. */
    public long addStudent(long teacherId, long classId, long studentId) {
        requirePositive(teacherId, "Teacher ID");
        requirePositive(classId, "Class ID");
        requirePositive(studentId, "Student ID");
        return enrollmentDAO.placeExistingStudent(teacherId, classId, studentId, EnrollmentMethod.MANUAL);
    }

    /** Tìm sinh viên đang hoạt động theo email rồi ghi danh vào lớp. */
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

    /** Đọc từng email trong CSV và ghi danh. Mỗi dòng được giữ lại kèm lý do thêm, bỏ qua hoặc từ chối. */
    public ImportResultDTO importStudentsFromCsv(long teacherId, long classId, InputStream fileStream) {
        requirePositive(teacherId, "Teacher ID");
        requirePositive(classId, "Class ID");
        ValidationUtil.required(fileStream, "CSV upload");
        requireOwnedClass(teacherId, classId);
        int imported = 0;
        int skipped = 0;
        int rejected = 0;
        List<ImportLineResult> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(fileStream, StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (!headerSkipped && trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("email")) {
                    headerSkipped = true;
                    continue;
                }
                headerSkipped = true;
                String email = csvEmail(trimmed);
                if (email.isEmpty() || !email.contains("@")) {
                    rejected++;
                    lines.add(new ImportLineResult(lineNumber, email, ImportOutcome.REJECTED, "Email không hợp lệ"));
                    continue;
                }
                try {
                    addStudentByEmail(teacherId, classId, email);
                    imported++;
                    lines.add(new ImportLineResult(lineNumber, email, ImportOutcome.ADDED, "Đã ghi danh vào lớp"));
                } catch (IllegalArgumentException ex) {
                    String reason = importReason(ex);
                    if (ex.getMessage() != null && ex.getMessage().contains("already has an active enrollment")) {
                        skipped++;
                        lines.add(new ImportLineResult(lineNumber, email, ImportOutcome.SKIPPED, reason));
                    } else {
                        rejected++;
                        lines.add(new ImportLineResult(lineNumber, email, ImportOutcome.REJECTED, reason));
                    }
                }
            }
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("The CSV file could not be read");
        }
        return new ImportResultDTO(imported, skipped, rejected, lines);
    }

    /** Lấy ô email ở cột đầu, bỏ dấu ngoặc kép và ký tự BOM. */
    private static String csvEmail(String line) {
        String email = line.split(",", 2)[0].trim().replace("\"", "");
        if (email.startsWith("\uFEFF")) email = email.substring(1).trim();
        return email;
    }

    /** Đổi lỗi ghi danh sang câu tiếng Việt để hiện đúng dòng bị bỏ qua hoặc từ chối. */
    private static String importReason(IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("already has an active enrollment")) {
            return "Sinh viên đã có ghi danh đang hoạt động cho môn này trong kỳ";
        }
        if (message.contains("No account uses that email")) return "Không có tài khoản với email này";
        if (message.contains("active Student account")) return "Tài khoản không phải sinh viên đang hoạt động";
        if (message.contains("no available seat")) return "Lớp đã hết chỗ";
        if (message.contains("active class")) return "Chỉ thêm được sinh viên vào lớp đang hoạt động";
        return message.isBlank() ? "Dòng không hợp lệ" : message;
    }

    /** Câu lỗi tiếng Việt để hiện trên màn ghi danh. */
    public static String publicMessage(IllegalArgumentException ex) {
        return importReason(ex);
    }

    /** Bật hoặc tắt một ghi danh thuộc lớp của giảng viên này. */
    public void updateEnrollmentStatus(long teacherId, long enrollmentId, EnrollmentStatus status) {
        requirePositive(teacherId, "Teacher ID");
        requirePositive(enrollmentId, "Enrollment ID");
        ValidationUtil.required(status, "Enrollment status");
        enrollmentDAO.updateOwnedStatus(teacherId, enrollmentId, status);
    }

    /** Dừng lại nếu giảng viên này không phụ trách lớp. */
    private void requireOwnedClass(long teacherId, long classId) {
        requirePositive(teacherId, "Teacher ID");
        requirePositive(classId, "Class ID");
        if (!enrollmentDAO.teacherOwnsClass(teacherId, classId)) {
            throw new IllegalArgumentException("Bạn không phụ trách lớp này");
        }
    }

    /** Dừng lại nếu id nhỏ hơn hoặc bằng 0. */
    private static void requirePositive(long id, String name) {
        if (id <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement authorized, transactional Enrollment workflow");
    }
}
