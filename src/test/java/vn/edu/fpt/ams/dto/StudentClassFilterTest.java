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

        assertEquals(10, StudentClassFilter.PAGE_SIZE);
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

    @Test
    void timeSortIsValidatedAndSurvivesPaginationAndWorkTabs() {
        var original = StudentClassFilter.from("Câu hỏi", "essay", "not_started", "3", " due_desc ");
        assertEquals("DUE_DESC", original.sort());
        assertEquals("DUE_DESC", original.withPage(4).sort());
        assertEquals("DUE_DESC", original.withStatus("DONE").sort());
        assertEquals(1, original.withStatus("DONE").page());
        assertEquals("DUE_ASC", original.nextDeadlineSort());
        assertEquals("DUE_DESC", original.withSort(original.nextDeadlineSort()).nextDeadlineSort());
        var opening = original.withSort(original.nextOpenSort());
        assertEquals("OPEN_DESC", opening.sort());
        assertEquals(1, opening.page());
        assertEquals("Câu hỏi", opening.search());
        assertEquals("ESSAY", opening.responseType());
        assertEquals("NOT_STARTED", opening.status());
        assertEquals("OPEN_ASC", opening.nextOpenSort());
        assertEquals("descending", opening.sortDirection());
        assertEquals("ascending", opening.withSort("OPEN_ASC").sortDirection());
        assertEquals("none", opening.withSort("DEFAULT").sortDirection());
        for (String sort : new String[] {"DUE", "newest", "due_at DESC", "OPEN_DESC,DUE_ASC", "DUE_DESC;DROP TABLE assignments"})
            assertThrows(IllegalArgumentException.class,
                    () -> StudentClassFilter.from("", "", "", "1", sort));
    }
}
