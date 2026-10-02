package vn.edu.fpt.ams.dto;

import java.util.Locale;
import vn.edu.fpt.ams.model.AssignmentPurpose;
import vn.edu.fpt.ams.model.AssignmentStatus;
import vn.edu.fpt.ams.model.ResponseType;

/** Search inputs for the Teacher Assignment List only. Null enums mean All. */
public record AssignmentListFilter(String search, AssignmentPurpose purpose,
                                   ResponseType responseType, AssignmentStatus status) {
    public AssignmentListFilter {
        search = search == null ? "" : search.strip();
        if (search.length() > 200) throw new IllegalArgumentException("Search must be 200 characters or fewer.");
    }

    public static AssignmentListFilter from(String search, String purpose, String responseType, String status) {
        return new AssignmentListFilter(search, parse(AssignmentPurpose.class, purpose),
                parse(ResponseType.class, responseType), parse(AssignmentStatus.class, status));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid assignment filter.");
        }
    }
}
