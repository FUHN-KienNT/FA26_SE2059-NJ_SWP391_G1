package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;

/** JDBC operations for Assignment; implementations are reserved for the next iteration. */
public class AssignmentDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement transactional JDBC access according to SDS");
    }

public java.util.List<Assignment> findByClassId(long classId) { throw pending(); }
public java.util.Optional<Assignment> findById(long assignmentId) { throw pending(); }
public long insertWithContent(Assignment assignment, long teacherId) { throw pending(); }
public void updateDraftWithContent(Assignment assignment, long teacherId) { throw pending(); }
public boolean publishIfUnchanged(long assignmentId, long teacherId) { throw pending(); }
public void cancelAndVoidInProgress(long assignmentId, long teacherId) { throw pending(); }

}
