package vn.edu.fpt.ams.dto;

import java.time.Instant;
import vn.edu.fpt.ams.model.AssignmentOfferStatus;

/** Teacher-facing offer, distinguishing the overflow's source from its additional class. */
public record AssignmentOfferView(long offerId, long requestId, String sourceClassCode,
        String subjectCode, String subjectName, String termCode, String offeredByName,
        String overflowContext, AssignmentOfferStatus status, Instant sentAt, Instant expiresAt,
        Instant respondedAt, String responseNote, Long createdClassId, String createdClassCode,
        int proposedCapacity) {}
