package vn.edu.fpt.ams.model;

import java.time.Instant;

public record ClassExpert(long classExpertId, long classId, long subjectId, long expertId,
                          boolean active, Instant assignedAt, Instant deactivatedAt) {}
