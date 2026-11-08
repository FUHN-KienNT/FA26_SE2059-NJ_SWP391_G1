package vn.edu.fpt.ams.dto;

import java.util.Locale;
import vn.edu.fpt.ams.model.AssignmentPurpose;
import vn.edu.fpt.ams.model.AssignmentStatus;
import vn.edu.fpt.ams.model.ResponseType;

/** Search inputs for the Teacher Assignment List only. Null enums mean All. */
public record AssignmentListFilter(
        String search,
        AssignmentPurpose purpose,
        ResponseType responseType,
        AssignmentStatus status,
        int page,
        String sort) {
    public static final int PAGE_SIZE = 10;

    public AssignmentListFilter(String search, AssignmentPurpose purpose,
            ResponseType responseType, AssignmentStatus status) {
        this(search, purpose, responseType, status, 1, "DEFAULT");
    }

    public AssignmentListFilter {
        search = search == null ? "" : search.strip();

        if (search.length() > 200)
            throw new IllegalArgumentException("Từ khóa tìm kiếm không được quá 200 ký tự.");

        if (page < 1) throw new IllegalArgumentException("Trang phải là số nguyên dương.");

        sort = sort == null || sort.isBlank() ? "DEFAULT" : sort.strip().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("DEFAULT", "NEWEST", "OLDEST", "DUE_ASC", "DUE_DESC").contains(sort))
            throw new IllegalArgumentException("Thứ tự sắp xếp không hợp lệ.");
    }

    public static AssignmentListFilter from(
            String search, String purpose, String responseType, String status) {
        return from(search, purpose, responseType, status, null, null);
    }

    public static AssignmentListFilter from(String search, String purpose, String responseType,
            String status, String page, String sort) {
        int number = 1;
        if (page != null && !page.isBlank()) {
            if (!page.matches("[0-9]{1,10}"))
                throw new IllegalArgumentException("Trang phải là số nguyên dương.");

            try {
                number = Integer.parseInt(page);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Trang phải là số nguyên dương.", ex);
            }
        }

        return new AssignmentListFilter(
                search,
                parse(AssignmentPurpose.class, purpose),
                parse(ResponseType.class, responseType),
                parse(AssignmentStatus.class, status), number, sort);
    }

    public AssignmentListFilter withPage(int number) {
        return new AssignmentListFilter(search, purpose, responseType, status, number, sort);
    }

    public AssignmentListFilter withSort(String value) {
        return new AssignmentListFilter(search, purpose, responseType, status, 1, value);
    }

    public String nextCreationSort() {
        return "NEWEST".equals(sort) ? "OLDEST" : "NEWEST";
    }

    public String nextDeadlineSort() {
        return "DUE_DESC".equals(sort) ? "DUE_ASC" : "DUE_DESC";
    }

    public long offset() {
        return ((long) page - 1) * PAGE_SIZE;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;

        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Bộ lọc bài tập không hợp lệ.");
        }
    }
}
