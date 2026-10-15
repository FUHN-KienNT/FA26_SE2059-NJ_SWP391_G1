package vn.edu.fpt.ams.util;

import java.util.Locale;

public final class TextMatch {
    private TextMatch() {}

    /** Đúng khi từ khóa trống, hoặc một trong các trường chứa từ khóa không phân biệt hoa thường. */
    public static boolean contains(String query, String... fields) {
        if (query == null || query.isBlank()) return true;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (fields == null) return false;
        for (String field : fields) {
            if (field != null && field.toLowerCase(Locale.ROOT).contains(needle)) return true;
        }
        return false;
    }
}
