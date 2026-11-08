package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaginationTest {
    @Test
    void smallPageCountsExposeEveryDestination() {
        assertEquals(List.of(1), Pagination.pages(1, 1));
        assertEquals(List.of(1, 2, 3, 4, 5), Pagination.pages(1, 5));
        assertEquals(List.of(1, 2, 3, 4, 5), Pagination.pages(5, 5));
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), Pagination.pages(4, 7));
    }

    @Test
    void largePageWindowsMatchTheMockupAndRetainTheLastTwoPages() {
        assertEquals(List.of(1, 2, 3, 0, 9, 10), Pagination.pages(1, 10));
        assertEquals(List.of(4, 5, 6, 0, 9, 10), Pagination.pages(5, 10));
        assertEquals(List.of(6, 7, 8, 9, 10), Pagination.pages(10, 10));
        assertEquals(List.of(499, 500, 501, 0, 999, 1000), Pagination.pages(500, 1000));
    }

    @Test
    void aSingleMissingPageIsClickableRatherThanHiddenBehindAnEllipsis() {
        assertEquals(List.of(3, 4, 5, 6, 7, 8), Pagination.pages(4, 8));
        assertEquals(List.of(5, 6, 7, 8, 9, 10), Pagination.pages(6, 10));
    }

    @Test
    void maximumPageNumbersStayBoundedWithoutOverflowing() {
        int last = Integer.MAX_VALUE;
        assertEquals(
                List.of(last - 4, last - 3, last - 2, last - 1, last),
                Pagination.pages(last, last));
        assertEquals(List.of(1, 2, 3, 0, last - 1, last), Pagination.pages(1, last));
        assertEquals(
                List.of(1_000_000_000 - 1, 1_000_000_000, 1_000_000_000 + 1, 0, last - 1, last),
                Pagination.pages(1_000_000_000, last));
    }

    @Test
    void invalidPageBoundsFailInsteadOfProducingImpossibleDestinations() {
        for (int[] bounds : List.of(
                new int[] {0, 1},
                new int[] {-1, 10},
                new int[] {1, 0},
                new int[] {1, -1},
                new int[] {2, 1},
                new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE})) {
            assertThrows(IllegalArgumentException.class,
                    () -> Pagination.pages(bounds[0], bounds[1]));
        }
    }

    @Test
    void everyWindowContainsTheCurrentPageAndOnlyOrderedReachableDestinations() {
        for (int total = 1; total <= 300; total++) {
            for (int current = 1; current <= total; current++) {
                List<Integer> items = Pagination.pages(current, total);
                assertEquals(items, Pagination.pages(current, total));
                assertTrue(items.size() <= 7);
                assertTrue(items.contains(current));
                assertEquals(total, items.get(items.size() - 1).intValue());
                assertNotEquals(0, items.get(0).intValue());

                var destinations = new ArrayList<Integer>();
                int previous = 0;
                boolean afterGap = false;
                for (int i = 0; i < items.size(); i++) {
                    int item = items.get(i);
                    if (item == 0) {
                        assertFalse(afterGap);
                        assertTrue(i > 0 && i + 1 < items.size());
                        assertTrue(items.get(i + 1) > previous + 2);
                        afterGap = true;
                    } else {
                        assertTrue(item >= 1 && item <= total);
                        assertTrue(item > previous);
                        if (previous > 0 && !afterGap) assertEquals(previous + 1, item);
                        destinations.add(item);
                        previous = item;
                        afterGap = false;
                    }
                }
                assertEquals(destinations.size(), destinations.stream().distinct().count());
            }
        }
    }

    @Test
    void pageLinksPreserveEncodedVietnameseSearchAndAllAssignmentFilters() {
        String base = "/ams/teacher/assignments?classId=9"
                + "&search=C%C3%A2u+%26+%3D+100%25_"
                + "&purpose=REGULAR&responseType=ESSAY&status=DRAFT&page=2&sort=DUE_ASC";

        assertEquals(
                "/ams/teacher/assignments?classId=9"
                        + "&search=C%C3%A2u+%26+%3D+100%25_"
                        + "&purpose=REGULAR&responseType=ESSAY&status=DRAFT&sort=DUE_ASC&page=7",
                Pagination.url(base, "page", 7));
    }

    @Test
    void onlyTheExactParameterIsReplacedAndDuplicateValuesAreRemoved() {
        assertEquals(
                "/ams/student/classes?classId=9&oldpage=6&historyPage=8&PAGE=5&page=3",
                Pagination.url(
                        "/ams/student/classes?classId=9&page=1&oldpage=6&page=2&historyPage=8&PAGE=5",
                        "page", 3));
    }

    @Test
    void historyPagingKeepsTheSubmissionListPageAndItsFragment() {
        assertEquals(
                "/ams/teacher/submissions?assignmentId=85&submissionId=44&page=2&status=GRADED"
                        + "&historyPage=7#evaluation-history",
                Pagination.url(
                        "/ams/teacher/submissions?assignmentId=85&submissionId=44&page=2"
                                + "&historyPage=3&status=GRADED#evaluation-history",
                        "historyPage", 7));
    }

    @Test
    void linksWithoutAnExistingQueryOrWithOnlyAFragmentRemainValid() {
        assertEquals("/ams/student/results?page=1",
                Pagination.url("/ams/student/results", "page", 1));
        assertEquals("/ams/student/results?page=2",
                Pagination.url("/ams/student/results?", "page", 2));
        assertEquals("/ams/student/results?page=3#submitted-work",
                Pagination.url("/ams/student/results#submitted-work", "page", 3));
        assertEquals("http://localhost:8080/ams/student/results?page=4",
                Pagination.url("http://localhost:8080/ams/student/results", "page", 4));
    }

    @Test
    void queryValuesAndFragmentTextThatLookLikePageParametersAreNotRewritten() {
        assertEquals(
                "/ams/student/classes?classId=9&q=page=2%26page%3D99&my_page=4&page=6"
                        + "#section?page=12",
                Pagination.url(
                        "/ams/student/classes?classId=9&q=page=2%26page%3D99&my_page=4&page=3"
                                + "#section?page=12",
                        "page", 6));
    }

    @Test
    void pageLinkValidationRejectsInvalidPagesAndParameterInjection() {
        assertThrows(IllegalArgumentException.class,
                () -> Pagination.url("/ams/student/results", "page", 0));
        assertThrows(IllegalArgumentException.class,
                () -> Pagination.url("/ams/student/results", "page", -1));
        for (String parameter : List.of("", "_page", "page-name", "page?", "page=2&role=ADMIN", "trangộ")) {
            assertThrows(IllegalArgumentException.class,
                    () -> Pagination.url("/ams/student/results", parameter, 2));
        }
    }

    @Test
    void pageLinksSupportPositiveIntegerBoundariesAndNamedHistoryParameters() {
        assertEquals("/ams/student/results?page=" + Integer.MAX_VALUE,
                Pagination.url("/ams/student/results?page=2", "page", Integer.MAX_VALUE));
        assertEquals("/ams/student/results?page_index=1",
                Pagination.url("/ams/student/results", "page_index", 1));
    }
}
