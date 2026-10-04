package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.AssignmentListFilter;

class AssignmentDetailViewTest {
    @Test void checklistReadsStringsAndSupportedTaskObjectFields() {
        var checklist = AssignmentDetailView.checklist("""
                ["Plain task", {"title":"Title task"}, {"task":"Task field"},
                 {"text":"Text field"}, {"description":"Description task"},
                 {"title":" ","task":"Fallback task"}]
                """);
        assertEquals(List.of("Plain task", "Title task", "Task field", "Text field",
                "Description task", "Fallback task"), checklist.items());
        assertFalse(checklist.invalid());
    }

    @Test void checklistPreservesSupportedItemsAndFlagsUnsupportedValues() {
        var checklist = AssignmentDetailView.checklist("""
                ["Valid task", 7, true, null, {}, {"title":false}, {"task":" "}]
                """);
        assertEquals(List.of("Valid task"), checklist.items());
        assertTrue(checklist.invalid());
        for (String json : List.of("{broken", "[", "{}", "null", "42", "\"task\"")) {
            var invalid = AssignmentDetailView.checklist(json);
            assertTrue(invalid.invalid(), json);
            assertTrue(invalid.items().isEmpty(), json);
        }
    }

    @Test void emptySavedContentIsValidForChecklistAndOptions() {
        for (String json : new String[]{null, "", " ", "[]"}) {
            assertTrue(AssignmentDetailView.checklist(json).items().isEmpty());
            assertFalse(AssignmentDetailView.checklist(json).invalid());
            assertTrue(AssignmentDetailView.options(json).items().isEmpty());
            assertFalse(AssignmentDetailView.options(json).invalid());
        }
    }

    @Test void optionsReadStringAndNumericIdsWithBooleanCorrectness() {
        var options = AssignmentDetailView.options("""
                [{"option_id":"A","option_text":"First answer","is_correct":true},
                 {"option_id":2,"option_text":"Second answer","is_correct":false}]
                """);
        assertEquals(List.of(new AssignmentDetailView.Option("A", "First answer", true),
                new AssignmentDetailView.Option("2", "Second answer", false)), options.items());
        assertFalse(options.invalid());
    }

    @Test void optionsRejectStringAndNumericCorrectnessInsteadOfCoercingThem() {
        for (String correctness : List.of("\"true\"", "\"false\"", "1", "0", "null", "{}", "[]")) {
            var options = AssignmentDetailView.options("[{\"option_id\":\"A\","
                    + "\"option_text\":\"Answer\",\"is_correct\":" + correctness + "}]");
            assertTrue(options.invalid(), correctness);
            assertTrue(options.items().isEmpty(), correctness);
        }
        var missing = AssignmentDetailView.options("[{\"option_id\":\"A\",\"option_text\":\"Answer\"}]");
        assertTrue(missing.invalid());
        assertTrue(missing.items().isEmpty());
    }

    @Test void optionsKeepValidEntriesAndFlagMalformedOrUnsupportedContent() {
        var mixed = AssignmentDetailView.options("""
                [{"option_id":"A","option_text":"Good answer","is_correct":false},
                 "string option", null, {},
                 {"option_id":true,"option_text":"Wrong ID type","is_correct":true},
                 {"option_id":" ","option_text":"Blank ID","is_correct":true},
                 {"option_id":"B","option_text":7,"is_correct":true},
                 {"option_id":"C","option_text":" ","is_correct":true}]
                """);
        assertEquals(List.of(new AssignmentDetailView.Option("A", "Good answer", false)), mixed.items());
        assertTrue(mixed.invalid());
        for (String json : List.of("{broken", "[", "{}", "null", "42")) {
            var invalid = AssignmentDetailView.options(json);
            assertTrue(invalid.invalid(), json);
            assertTrue(invalid.items().isEmpty(), json);
        }
    }

    @Test void maliciousSavedTextIsEscapedForHtmlWithoutBecomingMarkup() {
        var checklist = AssignmentDetailView.checklist(
                "[\"<script>alert('x')</script> & \\\"quoted\\\"\"]");
        assertFalse(checklist.invalid());
        assertEquals("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;quoted&quot;",
                AssignmentListView.escape(checklist.items().get(0)));
        var options = AssignmentDetailView.options("""
                [{"option_id":"<svg/onload=alert(1)>",
                  "option_text":"<img src=x onerror=alert('x')>&", "is_correct":true}]
                """);
        assertFalse(options.invalid());
        assertEquals("&lt;svg/onload=alert(1)&gt;", AssignmentListView.escape(options.items().get(0).id()));
        assertEquals("&lt;img src=x onerror=alert(&#39;x&#39;)&gt;&amp;",
                AssignmentListView.escape(options.items().get(0).text()));
    }

    @Test void listUrlPreservesCombinedFiltersAndContainsInjectedSearchAsOneValue() {
        String search = "Quiz &classId=999&mode=cancel?x=a+b%_#<script>Đ";
        var filter = AssignmentListFilter.from(search, "practice", "objective", "published");
        String url = AssignmentDetailView.listUrl("/ams", 10, filter);
        URI uri = URI.create(url);
        assertEquals("/ams/teacher/assignments", uri.getPath());
        assertNull(uri.getRawFragment());
        assertEquals(Map.of("classId", "10", "search", search, "purpose", "PRACTICE",
                "responseType", "OBJECTIVE", "status", "PUBLISHED"), queryParameters(uri));
        assertFalse(url.contains("&classId=999"));
        assertFalse(url.contains("&mode=cancel"));
        assertFalse(AssignmentListView.escape(url).contains("<script>"));
    }

    @Test void listUrlOmitsEmptyFiltersAndSupportsRootDeployment() {
        var empty = AssignmentListFilter.from(null, "ALL", "ALL", "ALL");
        assertEquals("/ams/teacher/assignments?classId=10", AssignmentDetailView.listUrl("/ams", 10, empty));
        assertEquals("/teacher/assignments?classId=10", AssignmentDetailView.listUrl("", 10, empty));
    }

    private static Map<String, String> queryParameters(URI uri) {
        Map<String, String> parameters = new LinkedHashMap<>();
        for (String parameter : uri.getRawQuery().split("&")) {
            String[] pair = parameter.split("=", 2);
            assertEquals(2, pair.length);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            assertNull(parameters.put(key, value), "Duplicate query parameter: " + key);
        }
        return parameters;
    }
}
