package vn.edu.fpt.ams.dto;

/** Eligible active group and its current class context, shown only to its class Teacher. */
public record AssignmentGroupOption(long groupId, long classId, String classCode,
                                    String name, long memberCount) {}
