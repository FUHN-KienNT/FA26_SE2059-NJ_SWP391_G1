package vn.edu.fpt.ams.dto;

import java.time.Instant;

public record SubjectTermViewDTO(
    long subjectTermId,
    long termId,
    String termCode,
    String termName,
    boolean registrationEnabled,
    Instant registrationOpensAt,
    Instant registrationClosesAt,
    String registrationStatus) {}
