package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class EssaySubmissionViewTest {
    private static final String TASKS = """
            [{"task_order":2,"title":"Explain permissions","description":"Teacher and Student"},
             {"task_order":1,"title":"Describe the Web flow","description":"Servlet → Service → DAO"}]
            """;

    @Test void pairsByFrozenTaskIdentityRatherThanArrayPosition() {
        var result = EssaySubmissionView.read(TASKS, """
                {"format":"ams-essay-v1","answers":[
                  {"task_order":2,"text":"Only the assigned Teacher may view work."},
                  {"task_order":1,"text":"The Servlet calls Service, which reads through DAO."}]}
                """);
        assertTrue(result.structured());
        assertFalse(result.invalid());
        assertNull(result.legacyContent());
        assertEquals(List.of(1,2), result.items().stream().map(EssaySubmissionView.Item::order).toList());
        assertEquals("Describe the Web flow", result.items().get(0).title());
        assertEquals("Servlet → Service → DAO", result.items().get(0).description());
        assertEquals("The Servlet calls Service, which reads through DAO.", result.items().get(0).answer());
        assertEquals("Only the assigned Teacher may view work.", result.items().get(1).answer());
        assertTrue(result.unmatched().isEmpty());
    }

    @Test void missingAnswerIsNullAndBlankSavedAnswerRemainsBlank() {
        var result = EssaySubmissionView.read(TASKS,
                "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":2,\"text\":\"\"}]}");
        assertFalse(result.invalid());
        assertNull(result.items().get(0).answer());
        assertEquals("", result.items().get(1).answer());
        var empty = EssaySubmissionView.read(TASKS,"{\"format\":\"ams-essay-v1\",\"answers\":[]}");
        assertTrue(empty.structured());
        assertFalse(empty.invalid());
        assertTrue(empty.items().stream().allMatch(item -> item.answer() == null));
    }

    @Test void eachAnswerPreservesMultipleParagraphsWhitespaceAndUnicode() {
        var result = EssaySubmissionView.read(TASKS,
                "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":1,\"text\":\"  Dòng 1.\\n\\nDòng 2.\\n  \"}]}");
        assertEquals("  Dòng 1.\n\nDòng 2.\n  ",result.items().get(0).answer());
    }

    @Test void plaintextIsNeverSplitIntoQuestionsIncludingASingleTask() {
        String content = "  Câu 1: the first paragraph.\n\nCâu 2: another paragraph.\n ";
        for (String tasks : List.of(TASKS,"[{\"task_order\":1,\"title\":\"Only task\"}]","[]")) {
            var result = EssaySubmissionView.read(tasks,content);
            assertFalse(result.structured());
            assertFalse(result.invalid());
            assertEquals(content,result.legacyContent());
            assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
            assertTrue(result.unmatched().isEmpty());
        }
    }

    @Test void jsonLookingLegacyWorkNeedsTheExactVersionedEnvelope() {
        for (String content : List.of("{\"answers\":[{\"task_order\":1,\"text\":\"A\"}]}",
                "{\"format\":\"ams-essay-v2\",\"answers\":[]}","{\"format\":true,\"answers\":[]}",
                "[\"first paragraph\",\"second paragraph\"]","\"This is a JSON example.\"","null")) {
            var result = EssaySubmissionView.read(TASKS,content);
            assertFalse(result.structured());
            assertFalse(result.invalid());
            assertEquals(content,result.legacyContent());
            assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
        }
    }

    @Test void malformedOrInvalidEnvelopePreservesTheWholeOriginalWork() {
        for (String content : List.of("{\"format\":\"ams-essay-v1\",\"answers\":[",
                "{\"format\":\"ams-essay-v1\"}","{\"format\":\"ams-essay-v1\",\"answers\":null}",
                "{\"format\":\"ams-essay-v1\",\"answers\":{}}", "{format:'ams-essay-v1',answers:[]}",
                "{\"format\":\"ams-essay-v1\",\"answers\":[]} trailing")) {
            var result = EssaySubmissionView.read(TASKS,content);
            assertTrue(result.invalid());
            assertFalse(result.structured());
            assertEquals(content,result.legacyContent());
            assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
        }
    }

    @Test void unknownTaskAnswerStaysVisibleButIsNotAttachedToAnotherQuestion() {
        var result = EssaySubmissionView.read(TASKS,
                "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":99,\"text\":\"Original work\"}]}");
        assertTrue(result.structured());
        assertTrue(result.invalid());
        assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
        assertEquals(1,result.unmatched().size());
        assertTrue(result.unmatched().get(0).reference().contains("99"));
        assertEquals("Original work",result.unmatched().get(0).text());
    }

    @Test void duplicateAnswerReferencesKeepBothTextsUnmatchedRatherThanTakingFirstOrLast() {
        var result = EssaySubmissionView.read(TASKS,"""
                {"format":"ams-essay-v1","answers":[{"task_order":1,"text":"First answer"},
                  {"task_order":2,"text":"Unambiguous answer"},{"task_order":1,"text":"Second answer"}]}
                """);
        assertTrue(result.invalid());
        assertNull(result.items().get(0).answer());
        assertEquals("Unambiguous answer",result.items().get(1).answer());
        assertEquals(List.of("First answer","Second answer"),result.unmatched().stream()
                .map(EssaySubmissionView.UnmatchedAnswer::text).toList());
    }

    @Test void invalidDuplicateAnswerStillMakesTheIdentityAmbiguous() {
        var result = EssaySubmissionView.read(TASKS,"""
                {"format":"ams-essay-v1","answers":[{"task_order":1,"text":"Keep me"},
                  {"task_order":1,"text":false}]}
                """);
        assertTrue(result.invalid());
        assertNull(result.items().get(0).answer());
        assertEquals(2,result.unmatched().size());
        assertTrue(result.unmatched().stream().anyMatch(answer -> answer.text().equals("Keep me")));
    }

    @Test void duplicateTaskReferencesKeepReadablePromptsButDoNotPairAnAnswer() {
        var result = EssaySubmissionView.read("""
                [{"task_order":1,"title":"First prompt"},{"task_order":1,"title":"Another prompt"}]
                """, "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":1,\"text\":\"Original work\"}]}");
        assertTrue(result.invalid());
        assertEquals(2,result.items().size());
        assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
        assertEquals("Original work",result.unmatched().get(0).text());
        // A duplicate whose title is corrupt cannot make the other task appear unambiguous.
        var corrupt = EssaySubmissionView.read("[{\"task_order\":1,\"title\":\"Valid title\"},{\"task_order\":1}]",
                "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":1,\"text\":\"Original work\"}]}");
        assertTrue(corrupt.invalid());
        assertNull(corrupt.items().get(0).answer());
        assertEquals("Original work",corrupt.unmatched().get(0).text());
    }

    @Test void legacyTasksStayReadableWithoutInventingAnIdentityFromTheirPosition() {
        var result = EssaySubmissionView.read("[\"Legacy task\",{\"title\":\"Another task\",\"description\":\"Details\"}]",
                "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":1,\"text\":\"Work\"}]}");
        assertTrue(result.invalid());
        assertEquals(List.of(0,0),result.items().stream().map(EssaySubmissionView.Item::order).toList());
        assertEquals("Legacy task",result.items().get(0).title());
        assertEquals("Details",result.items().get(1).description());
        assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
        assertEquals("Work",result.unmatched().get(0).text());
    }

    @Test void invalidIdentitiesCannotCoerceIntoAValidTaskReference() {
        for (String id : List.of("0","-1","1.0","1e0","true","\"1\"","2147483648","null")) {
            var result = EssaySubmissionView.read(TASKS,
                    "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":"+id+",\"text\":\"Work\"}]}");
            assertTrue(result.invalid());
            assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
            assertEquals("Work",result.unmatched().get(0).text());
        }
    }

    @Test void duplicateJsonPropertiesAreRejectedBeforeTheyCanChangeAReference() {
        String content = """
                {"format":"ams-essay-v1","answers":[{"task_order":1,"task_order":2,"text":"Work"}]}
                """;
        var result = EssaySubmissionView.read(TASKS,content);
        assertTrue(result.invalid());
        assertFalse(result.structured());
        assertEquals(content,result.legacyContent());
        assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
        var tasks = EssaySubmissionView.read("[{\"task_order\":1,\"task_order\":2,\"title\":\"Prompt\"}]",
                "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":2,\"text\":\"Work\"}]}");
        assertTrue(tasks.invalid());
        assertTrue(tasks.items().isEmpty());
        assertEquals("Work",tasks.unmatched().get(0).text());
    }

    @Test void malformedEntriesAndTaskListsDoNotDiscardStoredAnswerText() {
        var result = EssaySubmissionView.read("[", """
                {"format":"ams-essay-v1","answers":[{"task_order":1,"text":"Keep this"},false,{"task_order":2,"text":5}]}
                """);
        assertTrue(result.invalid());
        assertTrue(result.items().isEmpty());
        assertEquals(3,result.unmatched().size());
        assertTrue(result.unmatched().stream().anyMatch(answer -> answer.text().equals("Keep this")));
        assertTrue(result.unmatched().stream().anyMatch(answer -> answer.text().equals("false")));
        assertTrue(result.unmatched().stream().anyMatch(answer -> answer.text().contains("\"text\":5")));
    }

    @Test void blankWorkRemainsBlankAndReturnedListsAreImmutable() {
        for (String content : new String[] {null,"","  \n"}) {
            var result = EssaySubmissionView.read(TASKS,content);
            assertFalse(result.structured());
            assertFalse(result.invalid());
            assertEquals(content,result.legacyContent());
            assertEquals(2,result.items().size());
            assertThrows(UnsupportedOperationException.class,() -> result.items().clear());
            assertThrows(UnsupportedOperationException.class,() -> result.unmatched().clear());
        }
    }

    @Test void htmlIsUntrustedPlainTextForTheViewToEscape() {
        var result = EssaySubmissionView.read("[{\"task_order\":1,\"title\":\"<script> & prompt\",\"description\":\"<b>\"}]",
                "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":1,\"text\":\"<img onerror=alert(1)> & work\"}]}");
        assertFalse(result.invalid());
        var item = result.items().get(0);
        assertEquals("<img onerror=alert(1)> & work",item.answer());
        assertEquals("&lt;script&gt; &amp; prompt",AssignmentListView.escape(item.title()));
        assertEquals("&lt;b&gt;",AssignmentListView.escape(item.description()));
        assertEquals("&lt;img onerror=alert(1)&gt; &amp; work",AssignmentListView.escape(item.answer()));
    }
}
