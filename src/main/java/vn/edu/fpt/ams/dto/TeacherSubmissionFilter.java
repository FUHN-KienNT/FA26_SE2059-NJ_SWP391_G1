package vn.edu.fpt.ams.dto;

import java.util.Locale;
import vn.edu.fpt.ams.model.SubmissionStatus;

/** Filters for one assignment's roster and attempts. Not started is a display-only state. */
public record TeacherSubmissionFilter(String search, SubmissionStatus status, int page,
        boolean notStartedOnly) {
    public static final int PAGE_SIZE = 12;

    public TeacherSubmissionFilter(String search, SubmissionStatus status) {
        this(search, status, 1, false);
    }

    public TeacherSubmissionFilter(String search, SubmissionStatus status, int page) {
        this(search, status, page, false);
    }

    public TeacherSubmissionFilter {
        search = search == null ? "" : search.strip();
        if (search.codePointCount(0, search.length()) > 200)
            throw new IllegalArgumentException("Từ khóa tìm kiếm tối đa 200 ký tự.");
        if (page < 1) throw new IllegalArgumentException("Số trang phải là số nguyên dương.");
        if (notStartedOnly && status != null)
            throw new IllegalArgumentException("Bộ lọc bài nộp không hợp lệ.");
    }

    public long offset() { return ((long) page - 1) * PAGE_SIZE; }

    public TeacherSubmissionFilter withPage(int value) {
        return new TeacherSubmissionFilter(search, status, value, notStartedOnly);
    }

    public String statusValue() {
        return notStartedOnly ? "NOT_STARTED" : status == null ? "" : status.name();
    }

    public static TeacherSubmissionFilter from(String search, String status) {
        return from(search, status, null);
    }

    public static TeacherSubmissionFilter from(String search, String status, String page) {
        int pageNumber = 1;
        if (page != null && !page.isBlank()) {
            if (!page.matches("[0-9]+"))
                throw new IllegalArgumentException("Số trang phải là số nguyên dương.");
            try { pageNumber = Integer.parseInt(page); }
            catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Số trang phải là số nguyên dương.");
            }
        }
        String value = status == null ? "" : status.strip();
        if (value.isEmpty() || "ALL".equalsIgnoreCase(value))
            return new TeacherSubmissionFilter(search, null, pageNumber);
        if ("NOT_STARTED".equalsIgnoreCase(value))
            return new TeacherSubmissionFilter(search, null, pageNumber, true);
        SubmissionStatus parsed;
        try {
            parsed = SubmissionStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Bộ lọc bài nộp không hợp lệ.");
        }
        return new TeacherSubmissionFilter(search, parsed, pageNumber);
    }
}
