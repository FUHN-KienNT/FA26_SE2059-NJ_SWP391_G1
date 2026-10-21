package vn.edu.fpt.ams.service;

import java.sql.Connection;
import vn.edu.fpt.ams.dao.StudentDashboardDAO;
import vn.edu.fpt.ams.dto.StudentDashboardDTO;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.Jdbc;

public class StudentDashboardService {
    private final Jdbc.Connections connections;
    private final StudentDashboardDAO dao = new StudentDashboardDAO();

    public StudentDashboardService() { this(DBConnection::getConnection); }
    public StudentDashboardService(Jdbc.Connections connections) { this.connections = connections; }

    public java.util.List<vn.edu.fpt.ams.model.AcademicTerm> termOptions(long studentId) {
        if (studentId <= 0) throw new SecurityException("Vui lòng đăng nhập bằng tài khoản sinh viên đang hoạt động.");
        return Jdbc.transaction(connections,c->{
            c.setReadOnly(true);
            dao.activeStudent(c,studentId).orElseThrow(()->new SecurityException("Vui lòng đăng nhập bằng tài khoản sinh viên đang hoạt động."));
            return new vn.edu.fpt.ams.dao.MasterDataDAO().findTerms(c,null,null);
        });
    }

    public StudentDashboardDTO load(long studentId) {
        if (studentId <= 0) throw new SecurityException("Vui lòng đăng nhập bằng tài khoản sinh viên đang hoạt động.");
        return Jdbc.transaction(connections, c -> {
            c.setReadOnly(true);
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            var student = dao.activeStudent(c, studentId)
                    .orElseThrow(() -> new SecurityException("Vui lòng đăng nhập bằng tài khoản sinh viên đang hoạt động."));
            var now = dao.currentTime(c);
            return new StudentDashboardDTO(student.fullName(), student.email(), now,
                    dao.subjectCount(c, studentId), dao.classCount(c, studentId),
                    dao.assignmentCount(c, studentId), dao.submittedCount(c, studentId),
                    dao.releasedCount(c, studentId, now), dao.classes(c, studentId),
                    dao.openAssignments(c, studentId, now), dao.results(c, studentId, now));
        });
    }
}
