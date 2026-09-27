package vn.edu.fpt.ams.model;

import java.time.Instant;

public record TeacherSubject(long teacherSubjectId, long teacherId, long subjectId,
                             boolean active, Instant assignedAt, Instant deactivatedAt) {}
