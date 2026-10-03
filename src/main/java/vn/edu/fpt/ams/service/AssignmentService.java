package vn.edu.fpt.ams.service;

import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.ValidationUtil;
import vn.edu.fpt.ams.util.DBConnection;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/** Workflow boundary; unfinished writes fail closed instead of claiming success. */
public class AssignmentService {
    private final AssignmentDAO dao;
    private final ConnectionProvider connections;

    @FunctionalInterface
    public interface ConnectionProvider { Connection open() throws SQLException; }

    public AssignmentService() { this(new AssignmentDAO(), DBConnection::getConnection); }

    public AssignmentService(AssignmentDAO dao, ConnectionProvider connections) {
        this.dao = Objects.requireNonNull(dao);
        this.connections = Objects.requireNonNull(connections);
    }

    public long previewTeacherId() {
        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            return dao.findPreviewTeacherId(connection);
        } catch (SQLException ex) {
            throw new IllegalStateException("Local preview Teacher could not be loaded.", ex);
        }
    }

    public AssignmentListPage listPage(long teacherId, Long classId, AssignmentListFilter filter) {
        if (teacherId <= 0) throw new SecurityException("An active Teacher account is required.");
        if (classId != null && classId <= 0) throw new IllegalArgumentException("Class ID must be a positive number.");
        Objects.requireNonNull(filter, "Assignment filters are required.");
        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                if (!dao.isActiveTeacher(connection, teacherId))
                    throw new SecurityException("An active Teacher account is required.");
                var classes = dao.findClassesForTeacher(connection, teacherId);
                if (classId != null && classes.stream().noneMatch(c -> c.classId() == classId))
                    throw new SecurityException("You cannot access assignments for this class.");
                Long selected = classId != null ? classId : classes.isEmpty() ? null : classes.get(0).classId();
                List<Assignment> assignments = selected == null ? List.of()
                        : dao.findForTeacher(connection, teacherId, selected, filter);
                connection.commit();
                return new AssignmentListPage(classes, selected, filter, assignments);
            } catch (SQLException | RuntimeException ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Assignments could not be loaded.", ex);
        }
    }

    private static UnsupportedOperationException pending() { return new UnsupportedOperationException("TODO: implement SDS workflow and authorization"); }
    public java.util.List<Assignment> listAssignments(long teacherId, long classId) {
        return listPage(teacherId, classId, AssignmentListFilter.from(null, null, null, null)).assignments();
    }
    public Assignment findAssignment(long teacherId, long assignmentId) { throw pending(); }
    public long createAssignment(long teacherId, Assignment assignment) { throw pending(); }
    public void updateAssignment(long teacherId, Assignment assignment) { throw pending(); }
    public void publishAssignment(long teacherId, long assignmentId) { throw pending(); }
    public void cancelAssignment(long teacherId, long assignmentId) { throw pending(); }
}
