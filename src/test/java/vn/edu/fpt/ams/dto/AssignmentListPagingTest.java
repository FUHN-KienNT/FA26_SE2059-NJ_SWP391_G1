package vn.edu.fpt.ams.dto;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.util.AssignmentDetailView;

class AssignmentListPagingTest {
    @Test
    void timeHeadersStartNewestFirstAndToggleWithoutLosingFilters() {
        var original = AssignmentListFilter.from("Tình huống", "REGULAR", "ESSAY", "DRAFT",
                "2", null);
        var newest = original.withSort(original.nextCreationSort());
        assertEquals("NEWEST", newest.sort());
        assertEquals(1, newest.page());
        assertEquals("Tình huống", newest.search());
        assertEquals(original.purpose(), newest.purpose());
        assertEquals(original.responseType(), newest.responseType());
        assertEquals(original.status(), newest.status());
        assertEquals("OLDEST", newest.nextCreationSort());
        assertEquals("NEWEST", newest.withSort(newest.nextCreationSort()).nextCreationSort());

        var deadline = original.withSort(original.nextDeadlineSort());
        assertEquals("DUE_DESC", deadline.sort());
        assertEquals("DUE_ASC", deadline.nextDeadlineSort());
        assertEquals("DUE_DESC", deadline.withSort(deadline.nextDeadlineSort()).nextDeadlineSort());
    }

    @Test
    void validatesPageSortAndRetainsNavigationContext() {
        var filter = AssignmentListFilter.from("Tình huống", "REGULAR", "ESSAY", "DRAFT",
                "2", "DUE_ASC");
        assertEquals(10, AssignmentListFilter.PAGE_SIZE);
        assertEquals(10, filter.offset());
        assertEquals("DUE_ASC", filter.withPage(3).sort());
        String url = AssignmentDetailView.listUrl("/ams", 9, filter);
        assertTrue(url.contains("&page=2"));
        assertTrue(url.contains("&sort=DUE_ASC"));

        for (String page : new String[] {"0", "-1", "1.5", "abc", "2147483648"})
            assertThrows(IllegalArgumentException.class,
                    () -> AssignmentListFilter.from(null, null, null, null, page, null));

        assertThrows(IllegalArgumentException.class,
                () -> AssignmentListFilter.from(null, null, null, null, "1", "SQL"));
    }
}
