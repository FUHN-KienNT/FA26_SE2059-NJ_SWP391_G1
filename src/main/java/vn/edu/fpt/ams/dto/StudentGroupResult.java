package vn.edu.fpt.ams.dto;

import java.math.BigDecimal;

/** Only the authenticated member's released weighted result, without other members' ratings. */
public record StudentGroupResult(
        String groupName, BigDecimal teacherScore, BigDecimal peerScore,
        BigDecimal teacherWeight, BigDecimal peerWeight,
        int receivedReviews, int eligibleReviews) {}
