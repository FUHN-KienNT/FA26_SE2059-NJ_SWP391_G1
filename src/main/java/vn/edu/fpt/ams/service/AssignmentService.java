package vn.edu.fpt.ams.service;

import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.ValidationUtil;

/** Workflow boundary; unfinished writes fail closed instead of claiming success. */
public class AssignmentService {
    private final AssignmentDAO dao = new AssignmentDAO();

    private static UnsupportedOperationException pending() { return new UnsupportedOperationException("TODO: implement SDS workflow and authorization"); }
    public java.util.List<Assignment> listAssignments(long teacherId, long classId) { throw pending(); }
    public Assignment findAssignment(long teacherId, long assignmentId) { throw pending(); }
    public long createAssignment(long teacherId, Assignment assignment) { throw pending(); }
    public void updateAssignment(long teacherId, Assignment assignment) { throw pending(); }
    public void publishAssignment(long teacherId, long assignmentId) { throw pending(); }
    public void cancelAssignment(long teacherId, long assignmentId) { throw pending(); }
}
