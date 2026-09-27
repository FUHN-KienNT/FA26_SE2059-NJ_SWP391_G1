package vn.edu.fpt.ams.model;

import java.time.Instant;

public record AdditionalClassRequest(long requestId, long overflowBatchId,
                                     long requestedBy, Long preferredTeacherId,
                                     boolean willingToTeach, String overflowContext,
                                     String note, AdditionalClassRequestStatus status,
                                     Instant requestedAt, Instant reviewDueAt,
                                     Long reviewedBy, Instant reviewedAt,
                                     String decisionReason, Long createdClassId,
                                     Instant resolvedAt) {}
