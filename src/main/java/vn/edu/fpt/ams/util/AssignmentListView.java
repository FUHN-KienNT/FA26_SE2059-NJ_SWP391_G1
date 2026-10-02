package vn.edu.fpt.ams.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Output helpers restricted to the Assignment List JSP. */
public final class AssignmentListView {
    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private AssignmentListView() {}

    public static String escape(Object value) {
        if (value == null) return "";
        return value.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    public static String label(Enum<?> value) {
        if (value == null) return "All";
        String name = value.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    public static String deadline(Instant instant) { return instant == null ? "Not set" : DEADLINE.format(instant); }
}
