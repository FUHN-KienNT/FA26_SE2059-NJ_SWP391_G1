package vn.edu.fpt.ams.dto;

/** Saved recipient label; published labels and membership counts refer to the frozen audience. */
public record AssignmentGroupTarget(long targetId, long groupId, String name,
                                    long memberCount, boolean frozen) {}
