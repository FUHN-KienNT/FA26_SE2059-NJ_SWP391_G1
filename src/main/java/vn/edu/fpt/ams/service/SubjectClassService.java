package vn.edu.fpt.ams.service;

import java.util.List;
import vn.edu.fpt.ams.dao.EnrollmentDAO;
import vn.edu.fpt.ams.dao.SubjectClassDAO;
import vn.edu.fpt.ams.dao.SubjectExpertDAO;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.dto.SubjectClassDTO;
import vn.edu.fpt.ams.dto.SubjectClassFormDTO;
import vn.edu.fpt.ams.integration.EmailService;
import vn.edu.fpt.ams.model.SubjectClass;
import vn.edu.fpt.ams.util.ValidationUtil;

/** Expert-owned class operations from SDS section 5. */
public class SubjectClassService {
    private final SubjectClassDAO classDAO = new SubjectClassDAO();
    private final SubjectExpertDAO expertDAO = new SubjectExpertDAO();
    private final EnrollmentDAO enrollmentDAO = new EnrollmentDAO();
    private final UserDAO userDAO = new UserDAO();
    private final EmailService emailService = new EmailService();

    public List<SubjectClassDTO> listClasses(long expertId, Long subjectId, Long termId) {
        requirePositive(expertId, "Expert ID");
        // TODO: scope by an active SubjectExpert responsibility before returning class data.
        throw pending();
    }

    public SubjectClassDTO getClassById(long expertId, long classId) {
        requirePositive(expertId, "Expert ID");
        requirePositive(classId, "Class ID");
        // TODO: load class, Subject Term, active Expert responsibility, roster and Teacher.
        throw pending();
    }

    public long createClass(long expertId, SubjectClassFormDTO form) {
        requirePositive(expertId, "Expert ID");
        validateForm(form);
        // TODO: atomically verify active Expert responsibility and unique class_code, then insert.
        throw pending();
    }

    public void updateClass(long expertId, SubjectClassFormDTO form) {
        requirePositive(expertId, "Expert ID");
        validateForm(form);
        requirePositive(form.classId(), "Class ID");
        // These counts and the eventual update must run while the class row is locked
        // in the same transaction.
        long activeEnrollments = enrollmentDAO.countActiveStudents(form.classId());
        long reservedInvitationSeats = enrollmentDAO.countActiveReservedInvitationSeats(form.classId());
        validateCapacityReduction(form.capacity(), activeEnrollments, reservedInvitationSeats);
        throw pending();
    }

    public void assignTeacherToClass(long expertId, long classId, long teacherId) {
        requirePositive(expertId, "Expert ID");
        requirePositive(classId, "Class ID");
        requirePositive(teacherId, "Teacher ID");
        // TODO: follow SDS sequence: load class via classDAO, verify active Expert ownership
        // and active TEACHER via userDAO, then updateTeacher. Notify only after commit.
        // Do not write first and validate later. Reuse emailService if notification is enabled.
        throw pending();
    }

    public List<SubjectClass> listAssignedClasses(long teacherId) {
        requirePositive(teacherId, "Teacher ID");
        return classDAO.findAssignedToTeacher(teacherId);
    }

    private static void validateForm(SubjectClassFormDTO form) {
        ValidationUtil.required(form, "Class form");
        requirePositive(form.subjectTermId(), "Subject Term ID");
        ValidationUtil.required(form.classCode(), "Class code");
        ValidationUtil.required(form.className(), "Class name");
        if (form.capacity() < 10 || form.capacity() > 40) {
            throw new IllegalArgumentException("Capacity must be between 10 and 40");
        }
    }

    static void validateCapacityReduction(int capacity, long activeEnrollments,
                                          long activeReservedInvitationSeats) {
        long committedSeats = Math.addExact(activeEnrollments, activeReservedInvitationSeats);
        if (capacity < committedSeats) {
            throw new IllegalArgumentException(
                    "Capacity cannot be lower than active enrollments plus reserved invitation seats");
        }
    }

    private static void requirePositive(long id, String name) {
        if (id <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement authorized, transactional Subject Class workflow");
    }
}
