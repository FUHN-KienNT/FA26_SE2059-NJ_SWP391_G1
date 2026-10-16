package vn.edu.fpt.ams.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Limits shared by draft saving and publication. */
public final class AssignmentRules {
    public static final int MAX_SCORE = 100;
    public static final int MAX_INSTRUCTIONS = 500;
    public static final int MAX_TASKS = 200;
    public static final int MAX_QUESTIONS = 500;
    public static final int MAX_OPTIONS = 10;
    public static final int MIN_MINUTES = 20;
    public static final int MAX_MINUTES = 720;
    private AssignmentRules() { }

    public static boolean validMaximum(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ONE) >= 0
                && value.compareTo(BigDecimal.valueOf(MAX_SCORE)) <= 0
                && value.stripTrailingZeros().scale() <= 0;
    }

    public static boolean validPoints(BigDecimal value) {
        if (value == null || value.compareTo(new BigDecimal("0.01")) < 0
                || value.compareTo(BigDecimal.valueOf(MAX_SCORE)) > 0) return false;
        try { value.setScale(2, RoundingMode.UNNECESSARY); return true; }
        catch (ArithmeticException ex) { return false; }
    }

    public static int length(String value) {
        String normalized = normalizeLines(value);
        return normalized.codePointCount(0, normalized.length());
    }

    public static String normalizeLines(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
