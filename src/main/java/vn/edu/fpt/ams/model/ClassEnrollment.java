package vn.edu.fpt.ams.model;

import java.time.Instant;

/** A placement episode; historical episodes remain after deactivation. */
public record ClassEnrollment(long enrollmentId, long registrationId, long classId,
                              EnrollmentStatus status, EnrollmentMethod enrollmentMethod,
                              Instant enrolledAt, Instant deactivatedAt) {}
