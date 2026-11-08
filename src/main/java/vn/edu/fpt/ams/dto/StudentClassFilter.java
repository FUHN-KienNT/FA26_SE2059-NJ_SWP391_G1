package vn.edu.fpt.ams.dto;

import java.util.Locale;
import java.util.Set;

/** Validated search and pagination inputs for the student's own class. */
public record StudentClassFilter(String search, String responseType, String status, int page, String sort) {
    public static final int PAGE_SIZE = 10;
    private static final Set<String> RESPONSE_TYPES = Set.of("ESSAY", "OBJECTIVE");
    private static final Set<String> SORTS = Set.of("DEFAULT", "OPEN_DESC", "OPEN_ASC", "DUE_DESC", "DUE_ASC");
    private static final Set<String> WORK_STATUSES =
            Set.of(
                    "NOT_STARTED",
                    "IN_PROGRESS",
                    "SUBMITTED",
                    "GRADED",
                    "RELEASED",
                    "DONE",
                    "EXPIRED",
                    "VOID");

    public StudentClassFilter(String search, String responseType, String status, int page) {
        this(search, responseType, status, page, "DEFAULT");
    }

    public StudentClassFilter {
        search = search == null ? "" : search.strip();

        if (search.length() > 200)
            throw new IllegalArgumentException("Từ khóa tìm kiếm không được quá 200 ký tự.");

        responseType = option(responseType, RESPONSE_TYPES);
        status = option(status, WORK_STATUSES);
        sort = sort == null || sort.isBlank() ? "DEFAULT" : sort.strip().toUpperCase(Locale.ROOT);
        if (!SORTS.contains(sort)) throw new IllegalArgumentException("Thứ tự sắp xếp không hợp lệ.");

        if (page <= 0) throw new IllegalArgumentException("Trang phải là số nguyên dương.");
    }

    public static StudentClassFilter from(
            String search, String responseType, String status, String page) {
        return from(search, responseType, status, page, null);
    }

    public static StudentClassFilter from(
            String search, String responseType, String status, String page, String sort) {
        int number = 1;

        if (page != null && !page.isBlank()) {
            String value = page.strip();

            if (!value.matches("[0-9]+"))
                throw new IllegalArgumentException("Trang phải là số nguyên dương.");

            try {
                number = Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Trang phải là số nguyên dương.", ex);
            }
        }

        return new StudentClassFilter(search, responseType, status, number, sort);
    }

    public StudentClassFilter withPage(int number) {
        return new StudentClassFilter(search, responseType, status, number, sort);
    }

    public StudentClassFilter withSort(String value) {
        return new StudentClassFilter(search, responseType, status, 1, value);
    }

    public StudentClassFilter withStatus(String value) {
        return new StudentClassFilter(search, responseType, value, 1, sort);
    }

    public String nextOpenSort() {
        return "OPEN_DESC".equals(sort) ? "OPEN_ASC" : "OPEN_DESC";
    }

    public String nextDeadlineSort() {
        return "DUE_DESC".equals(sort) ? "DUE_ASC" : "DUE_DESC";
    }

    public String sortDirection() {
        return sort.endsWith("_ASC") ? "ascending" : sort.endsWith("_DESC") ? "descending" : "none";
    }

    private static String option(String value, Set<String> allowed) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);

        if (!normalized.isEmpty() && !allowed.contains(normalized))
            throw new IllegalArgumentException("Bộ lọc bài tập không hợp lệ.");

        return normalized;
    }
}
