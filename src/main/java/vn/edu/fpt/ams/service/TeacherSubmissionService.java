package vn.edu.fpt.ams.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.Objects;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.dao.TeacherSubmissionDAO;
import vn.edu.fpt.ams.dto.AssignmentDetailPage;
import vn.edu.fpt.ams.dto.TeacherSubmissionDetailPage;
import vn.edu.fpt.ams.dto.TeacherSubmissionFilter;
import vn.edu.fpt.ams.dto.TeacherSubmissionListPage;
import vn.edu.fpt.ams.model.ResponseType;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.AssignmentSubmissionAccess;
import vn.edu.fpt.ams.util.SubmissionContentSnapshot;

/** Loads a consistent read-only snapshot of an owned assignment's class roster and saved attempts. */
public class TeacherSubmissionService {
    private final TeacherSubmissionDAO dao;
    private final AssignmentDAO assignments;
    private final ConnectionProvider connections;

    @FunctionalInterface
    public interface ConnectionProvider { Connection open() throws SQLException; }

    public TeacherSubmissionService() {
        this(new TeacherSubmissionDAO(), new AssignmentDAO(), DBConnection::getConnection);
    }

    public TeacherSubmissionService(TeacherSubmissionDAO dao, AssignmentDAO assignments,
            ConnectionProvider connections) {
        this.dao = Objects.requireNonNull(dao);
        this.assignments = Objects.requireNonNull(assignments);
        this.connections = Objects.requireNonNull(connections);
    }

    public TeacherSubmissionListPage listPage(long teacherId, long assignmentId,
            TeacherSubmissionFilter filter) {
        validateActorAndAssignment(teacherId, assignmentId);
        Objects.requireNonNull(filter, "Cần có bộ lọc bài nộp.");
        return readPage(connection -> {
            AssignmentDetailPage assignment = ownedAssignment(connection, teacherId, assignmentId);
            long count = dao.countForTeacher(connection, teacherId, assignmentId, filter);
            long lastPage = Math.max(1, (count + TeacherSubmissionFilter.PAGE_SIZE - 1) / TeacherSubmissionFilter.PAGE_SIZE);
            TeacherSubmissionFilter effective = filter.withPage((int) Math.min(filter.page(), lastPage));
            return new TeacherSubmissionListPage(assignment, effective,
                    dao.findForTeacher(connection, teacherId, assignmentId, effective), count);
        });
    }

    public TeacherSubmissionDetailPage detailPage(long teacherId, long assignmentId, long submissionId) {
        validateActorAndAssignment(teacherId, assignmentId);
        if (submissionId <= 0) throw new IllegalArgumentException("Mã bài nộp phải là số nguyên dương.");
        return readPage(connection -> {
            AssignmentDetailPage assignment = ownedAssignment(connection, teacherId, assignmentId);
            var row = dao.findDetailForTeacher(connection, teacherId, assignmentId, submissionId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy bài nộp hoặc bạn không có quyền xem."));
            if (row.assignmentSnapshot() == null && assignment.assignment().responseType() == ResponseType.OBJECTIVE) {
                assignment = new AssignmentDetailPage(assignment.assignment(), assignment.classCode(),
                        assignment.className(), assignment.subjectCode(), assignment.subjectName(),
                        assignment.termCode(), assignment.templateTitle(),
                        assignments.findQuestionsForTeacher(connection, teacherId, assignmentId),
                        assignment.canViewSubmissions());
            }
            return new TeacherSubmissionDetailPage(SubmissionContentSnapshot.displayPage(assignment, row), row);
        });
    }

    private AssignmentDetailPage ownedAssignment(Connection connection, long teacherId,
            long assignmentId) throws SQLException {
        if (!assignments.isActiveTeacher(connection, teacherId))
            throw new SecurityException("Cần đăng nhập bằng tài khoản giảng viên đang hoạt động.");
        AssignmentDetailPage page = assignments.findDetailForTeacher(connection, teacherId, assignmentId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy bài tập hoặc bạn không có quyền xem."));
        boolean hasWork = page.assignment().publishedAt() != null
                && dao.hasSubmittedWork(connection, teacherId, assignmentId);
        if (!AssignmentSubmissionAccess.canView(page.assignment(), hasWork))
            throw new NoSuchElementException("Không tìm thấy bài tập hoặc bạn không có quyền xem.");
        return page;
    }

    private static void validateActorAndAssignment(long teacherId, long assignmentId) {
        if (teacherId <= 0) throw new SecurityException("Cần đăng nhập bằng tài khoản giảng viên đang hoạt động.");
        if (assignmentId <= 0) throw new IllegalArgumentException("Mã bài tập phải là số nguyên dương.");
    }

    @FunctionalInterface
    private interface PageReader<T> { T read(Connection connection) throws SQLException; }

    private <T> T readPage(PageReader<T> reader) {
        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                T page = reader.read(connection);
                connection.commit();
                return page;
            } catch (SQLException | RuntimeException ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Không thể tải danh sách bài nộp.", ex);
        }
    }
}
