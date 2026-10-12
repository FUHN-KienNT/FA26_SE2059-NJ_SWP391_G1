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

/** Loads a consistent read-only snapshot of one owned assignment's saved attempts. */
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
        Objects.requireNonNull(filter, "Submission filters are required.");
        return readPage(connection -> {
            AssignmentDetailPage assignment = ownedAssignment(connection, teacherId, assignmentId);
            return new TeacherSubmissionListPage(assignment, filter,
                    dao.findForTeacher(connection, teacherId, assignmentId, filter));
        });
    }

    public TeacherSubmissionDetailPage detailPage(long teacherId, long assignmentId, long submissionId) {
        validateActorAndAssignment(teacherId, assignmentId);
        if (submissionId <= 0) throw new IllegalArgumentException("Submission ID must be a positive number.");
        return readPage(connection -> {
            AssignmentDetailPage assignment = ownedAssignment(connection, teacherId, assignmentId);
            var row = dao.findDetailForTeacher(connection, teacherId, assignmentId, submissionId)
                    .orElseThrow(() -> new NoSuchElementException("Submission not found or unavailable."));
            if (assignment.assignment().responseType() == ResponseType.OBJECTIVE) {
                assignment = new AssignmentDetailPage(assignment.assignment(), assignment.classCode(),
                        assignment.className(), assignment.subjectCode(), assignment.subjectName(),
                        assignment.termCode(), assignment.templateTitle(),
                        assignments.findQuestionsForTeacher(connection, teacherId, assignmentId));
            }
            return new TeacherSubmissionDetailPage(assignment, row);
        });
    }

    private AssignmentDetailPage ownedAssignment(Connection connection, long teacherId,
            long assignmentId) throws SQLException {
        if (!assignments.isActiveTeacher(connection, teacherId))
            throw new SecurityException("An active Teacher account is required.");
        return assignments.findDetailForTeacher(connection, teacherId, assignmentId)
                .orElseThrow(() -> new NoSuchElementException("Assignment not found or unavailable."));
    }

    private static void validateActorAndAssignment(long teacherId, long assignmentId) {
        if (teacherId <= 0) throw new SecurityException("An active Teacher account is required.");
        if (assignmentId <= 0) throw new IllegalArgumentException("Assignment ID must be a positive number.");
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
            throw new IllegalStateException("Submissions could not be loaded.", ex);
        }
    }
}
