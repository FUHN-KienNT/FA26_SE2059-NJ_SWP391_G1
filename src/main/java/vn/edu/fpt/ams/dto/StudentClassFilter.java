package vn.edu.fpt.ams.dto;

import java.util.Locale;
import java.util.Set;

/** Validated search and pagination inputs for the student's own class. */
public record StudentClassFilter(String search, String responseType, String status, int page) {
    public static final int PAGE_SIZE = 12;
    private static final Set<String> RESPONSE_TYPES = Set.of("ESSAY", "OBJECTIVE");
    private static final Set<String> WORK_STATUSES = Set.of(
            "NOT_STARTED", "IN_PROGRESS", "SUBMITTED", "GRADED", "RELEASED", "EXPIRED", "VOID");

    public StudentClassFilter {
        search = search == null ? "" : search.strip();
        if (search.length() > 200)
            throw new IllegalArgumentException("Từ khóa tìm kiếm không được quá 200 ký tự.");
        responseType = option(responseType, RESPONSE_TYPES);
        status = option(status, WORK_STATUSES);
        if (page <= 0) throw new IllegalArgumentException("Trang phải là số nguyên dương.");
    }

    public static StudentClassFilter from(String search, String responseType, String status, String page) {
        int number = 1;
        if (page != null && !page.isBlank()) {
            String value = page.strip();
            if (!value.matches("[0-9]+"))
                throw new IllegalArgumentException("Trang phải là số nguyên dương.");
            try { number = Integer.parseInt(value); }
            catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Trang phải là số nguyên dương.", ex);
            }
        }
        return new StudentClassFilter(search, responseType, status, number);
    }

    public StudentClassFilter withPage(int number) {
        return new StudentClassFilter(search, responseType, status, number);
    }

    private static String option(String value, Set<String> allowed) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.isEmpty() && !allowed.contains(normalized))
            throw new IllegalArgumentException("Bộ lọc bài tập không hợp lệ.");
        return normalized;
    }
}
