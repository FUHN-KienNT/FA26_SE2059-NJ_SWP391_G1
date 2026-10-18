package vn.edu.fpt.ams.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Output helpers restricted to the Assignment List JSP. */
public final class AssignmentListView {
    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"))
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private AssignmentListView() {}

    public static String escape(Object value) {
        if (value == null) return "";
        return value.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    public static String label(Enum<?> value) {
        return value == null ? "Tất cả" : Vietnamese.label(value.name());
    }
    public static String deadline(Instant instant) { return instant == null ? "Chưa đặt" : DEADLINE.format(instant); }
}
