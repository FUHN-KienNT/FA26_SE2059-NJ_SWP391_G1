package vn.edu.fpt.ams.dto;

import vn.edu.fpt.ams.model.*;

public record TermDTO(long termId, String termCode, String termName, java.time.Instant startsAt, java.time.Instant endsAt, boolean current, boolean active) {}
