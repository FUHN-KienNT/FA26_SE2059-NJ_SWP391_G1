package vn.edu.fpt.ams.dto;

import java.math.BigDecimal;

/** Frozen assignment recipient group; live group membership never changes this work's ownership. */
public record StudentGroupContext(
        long targetId, String groupName, int memberCount,
        BigDecimal teacherWeight, BigDecimal peerWeight) {}
