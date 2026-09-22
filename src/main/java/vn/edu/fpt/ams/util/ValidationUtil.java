package vn.edu.fpt.ams.util;

import java.time.Instant;
import java.util.Objects;
public final class ValidationUtil {
    private ValidationUtil() {}
    public static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
    public static void laterThan(Instant end, Instant start, String name) {
        if (start == null || end == null || !end.isAfter(start)) throw new IllegalArgumentException(name + " must be later than start");
    }
    public static <T> T required(T value, String name) { return Objects.requireNonNull(value, name + " is required"); }
}
