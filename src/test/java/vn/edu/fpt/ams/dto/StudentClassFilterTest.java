package vn.edu.fpt.ams.dto;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class StudentClassFilterTest {
    @Test
    void defaultsAndNormalizesOnlySupportedOptions() {
        assertEquals(
                new StudentClassFilter("", "", "", 1),
                StudentClassFilter.from(null, null, null, null));
        assertEquals(
                new StudentClassFilter("Text", "ESSAY", "NOT_STARTED", 2),
                StudentClassFilter.from("  Text  ", " essay ", " not_started ", " 2 "));

        for (String status :
                new String[] {
                    "NOT_STARTED",
                    "IN_PROGRESS",
                    "SUBMITTED",
                    "GRADED",
                    "RELEASED",
                    "EXPIRED",
                    "VOID"
                })
            assertEquals(status, StudentClassFilter.from("", "OBJECTIVE", status, "1").status());

        assertEquals(12, StudentClassFilter.PAGE_SIZE);
    }

    @Test
    void acceptsSearchBoundaryAndPreservesLiteralSearchCharacters() {
        String search = "x".repeat(200);

        assertEquals(search, StudentClassFilter.from(search, "", "", "").search());
        assertThrows(
                IllegalArgumentException.class,
                () -> StudentClassFilter.from(search + "x", "", "", "1"));
        assertEquals(
                "50%_done\\review <script>",
                StudentClassFilter.from("50%_done\\review <script>", "", "", "1").search());
    }

    @Test
    void rejectsUnknownFilterValuesInsteadOfSilentlyShowingEverything() {
        for (String type : new String[] {"QUIZ", "ALL", "ESSAY,OBJECTIVE", "' OR 1=1 --"})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> StudentClassFilter.from("", type, "", "1"));

        for (String status : new String[] {"PUBLISHED", "DRAFT", "CANCELLED", "ALL", "NOT STARTED"})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> StudentClassFilter.from("", "", status, "1"));
    }

    @Test
    void paginationIsStrictAndCannotOverflow() {
        for (String page :
                new String[] {
                    "0", "-1", "+1", "1.5", "1e2", "NaN", "2147483648", "9999999999999999999999"
                })
            assertThrows(
                    IllegalArgumentException.class,
                    () -> StudentClassFilter.from("", "", "", page),
                    page);

        assertEquals(Integer.MAX_VALUE, StudentClassFilter.from("", "", "", "2147483647").page());
        assertThrows(IllegalArgumentException.class, () -> new StudentClassFilter("", "", "", 0));
        var filter = StudentClassFilter.from("Essay", "ESSAY", "SUBMITTED", "1");

        assertEquals(new StudentClassFilter("Essay", "ESSAY", "SUBMITTED", 3), filter.withPage(3));
        assertThrows(IllegalArgumentException.class, () -> filter.withPage(-1));
    }
}
