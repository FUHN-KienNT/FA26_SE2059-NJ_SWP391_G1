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

    /** Chỉ trả về tài khoản khi id thuộc giảng viên đang hoạt động. */
    public UserAccount requireActiveTeacher(long teacherId) {
        if (teacherId <= 0) throw new IllegalArgumentException("Teacher ID must be positive");
        UserAccount teacher = userDAO.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher account was not found"));
        if (teacher.role() != UserRole.TEACHER || teacher.accountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("An active Teacher account is required");
        }
        return teacher;
    }

    /** Trả các lớp được giao cho giảng viên này, kèm sĩ số, lời mời còn hạn và số bài tập. */
    public List<TeacherClassSummary> assignedClasses(long teacherId) {
        requireActiveTeacher(teacherId);
        return dashboardDAO.listAssignedClasses(teacherId);
    }

    /** Đếm mọi bài nộp thuộc các lớp của giảng viên này. */
    public long submissionCount(long teacherId) {
        requireActiveTeacher(teacherId);
        return dashboardDAO.countSubmissions(teacherId);
    }
}
