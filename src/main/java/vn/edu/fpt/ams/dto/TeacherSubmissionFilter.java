package vn.edu.fpt.ams.dto;

import java.util.Locale;
import vn.edu.fpt.ams.model.SubmissionStatus;

/** Filters for submissions of one assignment. A null status means All. */
public record TeacherSubmissionFilter(String search, SubmissionStatus status) {
    public TeacherSubmissionFilter {
        search = search == null ? "" : search.strip();
        if (search.codePointCount(0, search.length()) > 200)
            throw new IllegalArgumentException("Search must be 200 characters or fewer.");
    }

    public static TeacherSubmissionFilter from(String search, String status) {
        String value = status == null ? "" : status.strip();
        if (value.isEmpty() || "ALL".equalsIgnoreCase(value))
            return new TeacherSubmissionFilter(search, null);
        SubmissionStatus parsed;
        try {
            parsed = SubmissionStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid submission filter.");
        }
        return new TeacherSubmissionFilter(search, parsed);
    }
}
