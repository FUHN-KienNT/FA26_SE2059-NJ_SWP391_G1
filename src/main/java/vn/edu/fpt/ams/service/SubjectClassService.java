package vn.edu.fpt.ams.service;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import vn.edu.fpt.ams.dao.EnrollmentDAO;
import vn.edu.fpt.ams.dao.SubjectClassDAO;
import vn.edu.fpt.ams.dao.SubjectExpertDAO;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.dto.SubjectClassDTO;
import vn.edu.fpt.ams.dto.SubjectClassFormDTO;
import vn.edu.fpt.ams.dto.AssignedClassDTO;
import vn.edu.fpt.ams.dto.AssignedSubjectDTO;
import vn.edu.fpt.ams.dto.TermOptionDTO;
import vn.edu.fpt.ams.integration.EmailService;
import vn.edu.fpt.ams.model.SubjectClass;
import vn.edu.fpt.ams.model.ResponsibilityType;
import vn.edu.fpt.ams.model.SubjectStatus;
import vn.edu.fpt.ams.model.UserRole;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.Jdbc;
import vn.edu.fpt.ams.util.ValidationUtil;

/** Expert-owned class operations from SDS section 5. */
public class SubjectClassService {
    private final Jdbc.Connections connections;
    private final SubjectClassDAO classDAO = new SubjectClassDAO();
    private final SubjectExpertDAO expertDAO = new SubjectExpertDAO();
    private final EnrollmentDAO enrollmentDAO = new EnrollmentDAO();
    private final UserDAO userDAO = new UserDAO();
    private final EmailService emailService = new EmailService();

    public SubjectClassService() { this(DBConnection::getConnection); }
    public SubjectClassService(Jdbc.Connections source) { connections=source; }

    public List<SubjectClassDTO> listClasses(long expertId, Long subjectId, Long termId) {
        throw pending();
    }

    public List<AssignedSubjectDTO> listAssignedSubjects(long expertId) {
        requirePositive(expertId, "Expert ID");
        return Jdbc.transaction(connections,c->{ requireExpert(c,expertId); return classDAO.findAssignedSubjects(c,expertId); });
    }

    public List<AssignedSubjectDTO> listAssignedSubjects(long expertId, String keyword,
            ResponsibilityType role, SubjectStatus status) {
        if (keyword != null && keyword.length() > 50)
            throw new IllegalArgumentException("Từ khóa không được quá 50 ký tự.");
        String search = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        return listAssignedSubjects(expertId).stream()
                .filter(s -> role == null || s.responsibility() == role)
                .filter(s -> status == null || s.status() == status)
                .filter(s -> search.isEmpty() || s.subjectCode().toLowerCase(Locale.ROOT).contains(search)
                        || s.subjectName().toLowerCase(Locale.ROOT).contains(search))
                .toList();
    }

    public AssignedSubjectDTO assignedSubject(long expertId, long subjectId) {
        requirePositive(subjectId, "Subject ID");
        return listAssignedSubjects(expertId).stream()
                .filter(s -> s.subjectId() == subjectId)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy môn học được phân công."));
    }

    public List<AssignedClassDTO> listAssignedClasses(
            long expertId, Long subjectId, Long termId, String keyword) {
        requirePositive(expertId,"Expert ID");
        optionalPositive(subjectId,"Subject ID"); optionalPositive(termId,"Academic Term ID");
        if(keyword!=null&&keyword.length()>50) throw new IllegalArgumentException("Search maximum is 50 characters");
        return Jdbc.transaction(connections,c->{
            requireExpert(c,expertId);
            return classDAO.findManagedByExpert(c,expertId,subjectId,termId,keyword);
        });
    }

    public List<TermOptionDTO> accessibleTerms(long expertId) {
        requirePositive(expertId,"Expert ID");
        return Jdbc.transaction(connections,c->{ requireExpert(c,expertId); return classDAO.findAccessibleTerms(c,expertId); });
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

    private static void optionalPositive(Long id,String name){
        if(id!=null&&id<=0) throw new IllegalArgumentException(name+" must be positive");
    }

    private static void requireExpert(java.sql.Connection c,long actor) throws java.sql.SQLException {
        if(Access.active(c,actor)!=UserRole.EXPERT) throw new SecurityException("Expert access is required");
    }

    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement authorized, transactional Subject Class workflow");
    }
}
