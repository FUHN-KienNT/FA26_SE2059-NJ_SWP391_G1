package vn.edu.fpt.ams.model;

import java.time.Instant;

public record AdditionalClassAssignmentOffer(long offerId, long requestId,
                                             long teacherId, long offeredBy,
                                             AssignmentOfferStatus status, Instant sentAt,
                                             Instant expiresAt, Instant respondedAt,
                                             String responseNote) {}
