package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.AssignmentListFilter;

class AssignmentListViewTest {
    @Test void escapesUntrustedTitlesAndFilterValuesForHtmlAndAttributes() {
        assertEquals("&lt;script&gt;&amp;&quot;&#39;",AssignmentListView.escape("<script>&\"'"));
    }
    @Test void formatsUtcDeadlineAsVietnamTimeAndAllowsIncompleteDraft() {
        assertEquals("05/10/2026 23:59",AssignmentListView.deadline(Instant.parse("2026-10-05T16:59:00Z")));
        assertEquals("Chưa đặt",AssignmentListView.deadline(null));
    }
    @Test void rejectsOversizedSearch() {
        assertThrows(IllegalArgumentException.class,()->AssignmentListFilter.from("a".repeat(201),null,null,null));
    }
}
