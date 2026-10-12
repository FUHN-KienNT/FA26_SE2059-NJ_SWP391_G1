package vn.edu.fpt.ams.util;

/** The saved draft changed or is no longer editable. */
public final class AssignmentEditConflictException extends IllegalStateException {
    public AssignmentEditConflictException(String message) { super(message); }
}
