package vn.edu.fpt.ams.service;

import java.util.List;
import vn.edu.fpt.ams.dao.TeacherDashboardDAO;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.dto.TeacherClassSummary;
import vn.edu.fpt.ams.model.AccountStatus;
import vn.edu.fpt.ams.model.UserAccount;
import vn.edu.fpt.ams.model.UserRole;

/** Overview for the teacher currently assigned to classes. */
public class TeacherDashboardService {
    private final TeacherDashboardDAO dashboardDAO = new TeacherDashboardDAO();
    private final UserDAO userDAO = new UserDAO();

    public UserAccount requireActiveTeacher(long teacherId) {
        if (teacherId <= 0) throw new IllegalArgumentException("Mã giảng viên phải là số nguyên dương");
        UserAccount teacher = userDAO.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản giảng viên"));
        if (teacher.role() != UserRole.TEACHER || teacher.accountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Cần tài khoản giảng viên đang hoạt động");
        }
        return teacher;
    }

    public List<TeacherClassSummary> assignedClasses(long teacherId) {
        requireActiveTeacher(teacherId);
        return dashboardDAO.listAssignedClasses(teacherId);
    }

    /** Resolve one class within the current Teacher's assignments. */
    public TeacherClassSummary requireAssignedClass(long teacherId, long classId) {
        return assignedClasses(teacherId).stream()
                .filter(item -> item.classId() == classId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Lớp này không thuộc phân công của giảng viên hiện tại."));
    }

    public long submissionCount(long teacherId) {
        requireActiveTeacher(teacherId);
        return dashboardDAO.countSubmissions(teacherId);
    }
}
