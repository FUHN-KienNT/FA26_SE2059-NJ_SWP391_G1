package vn.edu.fpt.ams.model;

import java.time.Instant;

/** One Student registration for one Subject Term. */
public record SubjectRegistration(long registrationId, long studentId, long subjectTermId,
                                  RegistrationStatus status, Instant registeredAt,
                                  Instant deactivatedAt) {}
