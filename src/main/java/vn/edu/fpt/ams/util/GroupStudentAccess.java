package vn.edu.fpt.ams.util;

/** Internal SQL fragments: all identifiers are application constants, never request parameters. */
public final class GroupStudentAccess {
    private GroupStudentAccess() {}

    public static String audience(String assignment, String student, String enrollment) {
        return "(NOT EXISTS (SELECT 1 FROM assignment_group_settings ga_settings WHERE ga_settings.assignment_id="
                + assignment + ".assignment_id AND ga_settings.delivery_mode='GROUP') OR EXISTS ("
                + "SELECT 1 FROM assignment_group_targets ga_target JOIN assignment_group_members ga_member"
                + " ON ga_member.target_id=ga_target.target_id WHERE ga_target.assignment_id="
                + assignment + ".assignment_id AND ga_target.frozen_at IS NOT NULL"
                + " AND ga_target.owner_enrollment_id IS NOT NULL AND ga_member.student_id=" + student
                + (enrollment == null ? "" : " AND ga_member.enrollment_id=" + enrollment) + "))";
    }

    public static String owns(String submission, String originalStudent, String actor) {
        return "((" + submission + ".assignment_group_target_id IS NULL AND " + originalStudent + "=" + actor
                + ") OR EXISTS (SELECT 1 FROM assignment_group_targets ga_target"
                + " JOIN assignment_group_members ga_member ON ga_member.target_id=ga_target.target_id"
                + " JOIN assignment_group_settings ga_settings ON ga_settings.assignment_id=ga_target.assignment_id"
                + " AND ga_settings.delivery_mode='GROUP' WHERE ga_target.target_id="
                + submission + ".assignment_group_target_id AND ga_target.assignment_id="
                + submission + ".assignment_id AND ga_target.frozen_at IS NOT NULL AND ga_member.student_id="
                + actor + "))";
    }
}
