package vn.edu.fpt.ams.model;

import java.time.Instant;

public record StudentInvitation(long invitationId, long classId, long invitedBy,
                                String email, String fullName, String token,
                                InvitationStatus status, Instant sentAt, Instant expiresAt,
                                Instant acceptedAt, Long acceptedBy, Long enrollmentId) {}
