package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.TeacherSubmissionFilter;
import vn.edu.fpt.ams.model.Question;
import vn.edu.fpt.ams.model.SubmissionStatus;

class TeacherSubmissionViewTest {
    private final List<Question> questions = List.of(new Question(1,10,1,"Question <script> & text",BigDecimal.TEN,
            "[{\"option_id\":\"A\",\"option_order\":1,\"option_text\":\"Choice <b>\",\"is_correct\":true},{\"option_id\":\"B\",\"option_order\":2,\"option_text\":\"Other\",\"is_correct\":false}]"));

    @Test void onlyAbsoluteHttpLinksWithoutControlsOrEmbeddedCredentialsAreClickable() {
        assertEquals("https://example.test/work?x=1&y=2",TeacherSubmissionView.safeLink(" https://example.test/work?x=1&y=2 "));
        assertEquals("http://localhost:8081/work",TeacherSubmissionView.safeLink("http://localhost:8081/work"));
        for (String link : List.of("javascript:alert(1)","data:text/html,script","//example.test/file","/local/file",
                "https://user:password@example.test/","https://example.test/\nscript","https:///nohost","file:///C:/file"))
            assertEquals("",TeacherSubmissionView.safeLink(link));
        assertEquals("",TeacherSubmissionView.safeLink(null));
    }
    @Test void linksRetainUnicodeAndLiteralFilterCharactersAcrossViewBack() {
        var filter = TeacherSubmissionFilter.from(" Tiếng Việt & 100%_ "," graded ");
        String list = TeacherSubmissionView.listUrl("/ams",10,filter);
        assertTrue(list.contains("search=Ti%E1%BA%BFng+Vi%E1%BB%87t+%26+100%25_"));
        assertTrue(list.endsWith("&status=GRADED"));
        assertEquals(list+"&mode=view&submissionId=55",TeacherSubmissionView.detailUrl("/ams",10,55,filter));
    }
    @Test void nullAndZeroScoreAreDifferentAndDatesUseVietnamTime() {
        assertEquals("Not graded",TeacherSubmissionView.score(null));
        assertEquals("0",TeacherSubmissionView.score(BigDecimal.ZERO));
        assertEquals("10.5",TeacherSubmissionView.score(new BigDecimal("10.50")));
        assertEquals("Not submitted",TeacherSubmissionView.submittedAt(null));
        assertEquals("05 Oct 2026, 00:00",TeacherSubmissionView.submittedAt(Instant.parse("2026-10-04T17:00:00Z")));
        assertEquals("In progress",TeacherSubmissionView.statusLabel(SubmissionStatus.IN_PROGRESS));
    }
    @Test void savedAnswersResolveIdsToTextWithoutRegradingOrInjectingHtml() {
        var result = TeacherSubmissionView.answers("[{\"question_id\":1,\"selected_option_id\":\"A\",\"awarded_points\":0}]",questions);
        assertFalse(result.invalid()); assertEquals(1,result.items().size());
        assertEquals("Choice <b>",result.items().get(0).selectedOption());
        assertEquals("0",result.items().get(0).awardedPoints());
        assertTrue(result.items().get(0).question().contains("<script>"));
        assertTrue(AssignmentListView.escape(result.items().get(0).question()).contains("&lt;script&gt;"));
        assertEquals(List.of(new TeacherSubmissionView.Choice("A","Choice <b>",true,true),
                new TeacherSubmissionView.Choice("B","Other",false,false)),result.items().get(0).choices());
        assertTrue(AssignmentListView.escape(result.items().get(0).choices().get(0).text()).contains("&lt;b&gt;"));
    }
    @Test void unfinishedAnswerStaysUnselectedAndUngraded() {
        var result = TeacherSubmissionView.answers("[{\"question_id\":1,\"selected_option_id\":null,\"awarded_points\":null}]",questions);
        assertFalse(result.invalid()); assertEquals("No answer selected",result.items().get(0).selectedOption());
        assertEquals("Not graded",result.items().get(0).awardedPoints());
        assertEquals(2,result.items().get(0).choices().size());
        assertTrue(result.items().get(0).choices().stream().noneMatch(TeacherSubmissionView.Choice::selected));
    }
    @Test void malformedMissingOrDuplicateAnswerDataWarnsAndKeepsValidEntries() {
        for (String json : List.of("not json","{}","[false]","[{\"question_id\":1.5}]","[{\"question_id\":true}]"))
            assertTrue(TeacherSubmissionView.answers(json,questions).invalid());
        var result = TeacherSubmissionView.answers("[{\"question_id\":1,\"selected_option_id\":\"B\"},{\"question_id\":1},{\"question_id\":99,\"selected_option_id\":\"Z\"}]",questions);
        assertTrue(result.invalid()); assertEquals(2,result.items().size());
        assertEquals("Other",result.items().get(0).selectedOption());
        assertEquals("Question #99 (unavailable)",result.items().get(1).question());
        assertEquals("Option #Z (unavailable)",result.items().get(1).selectedOption());
    }

    @Test void wrongSelectionAndUnselectedCorrectOptionRemainDistinctWithoutChangingStoredPoints() {
        var answer = TeacherSubmissionView.answers(
                "[{\"question_id\":1,\"selected_option_id\":\"B\",\"awarded_points\":7.5}]",questions).items().get(0);
        assertEquals(new TeacherSubmissionView.Choice("A","Choice <b>",false,true),answer.choices().get(0));
        assertEquals(new TeacherSubmissionView.Choice("B","Other",true,false),answer.choices().get(1));
        assertEquals("7.5",answer.awardedPoints());
    }

    @Test void noSavedAnswersStillShowsEveryQuestionAndChoice() {
        for (String json : new String[] {null,""," ","[]"}) {
            var result = TeacherSubmissionView.answers(json,questions);
            assertFalse(result.invalid());
            assertEquals(1,result.items().size());
            assertEquals("No answer selected",result.items().get(0).selectedOption());
            assertEquals("Not graded",result.items().get(0).awardedPoints());
            assertEquals(2,result.items().get(0).choices().size());
            assertTrue(result.items().get(0).choices().stream().noneMatch(TeacherSubmissionView.Choice::selected));
        }
    }

    @Test void corruptedAnswerJsonWarnsButStillShowsTheFrozenQuestionsAndOptions() {
        for (String json : List.of("not json","{}","[false]")) {
            var result = TeacherSubmissionView.answers(json,questions);
            assertTrue(result.invalid());
            assertEquals(1,result.items().size());
            assertEquals(2,result.items().get(0).choices().size());
            assertTrue(result.items().get(0).choices().stream().noneMatch(TeacherSubmissionView.Choice::selected));
        }
    }

    @Test void questionAndOptionOrderAreIndependentOfSavedAnswerOrder() {
        String options = """
                [{"option_id":"last","option_order":3,"option_text":"Third","is_correct":false},
                 {"option_id":"first","option_order":1,"option_text":"First","is_correct":true},
                 {"option_id":"middle","option_order":2,"option_text":"Second","is_correct":false}]
                """;
        var unordered = List.of(new Question(8,10,2,"Q8",BigDecimal.TEN,options),
                new Question(7,10,2,"Q7",BigDecimal.TEN,options),questions.get(0));
        var result = TeacherSubmissionView.answers(
                "[{\"question_id\":8,\"selected_option_id\":\"middle\"},{\"question_id\":1,\"selected_option_id\":\"A\"}]",unordered);
        assertFalse(result.invalid());
        assertTrue(result.items().get(0).question().startsWith("Question 1"));
        assertEquals("Question 2 — Q7",result.items().get(1).question());
        assertEquals("Question 2 — Q8",result.items().get(2).question());
        assertEquals(List.of(new TeacherSubmissionView.Choice("A","First",false,true),
                new TeacherSubmissionView.Choice("B","Second",true,false),
                new TeacherSubmissionView.Choice("C","Third",false,false)),result.items().get(2).choices());
    }

    @Test void missingOrInvalidOptionOrderUsesStableArrayPosition() {
        String options = """
                [{"option_id":"x","option_text":"First","is_correct":true},
                 {"option_id":"y","option_order":-1,"option_text":"Second","is_correct":false},
                 {"option_id":"z","option_order":99999999999999999999,"option_text":"Third","is_correct":false}]
                """;
        var result = TeacherSubmissionView.answers("[]",List.of(question(options)));
        assertFalse(result.invalid());
        assertEquals(List.of("First","Second","Third"),result.items().get(0).choices().stream()
                .map(TeacherSubmissionView.Choice::text).toList());
        assertEquals(List.of("A","B","C"),result.items().get(0).choices().stream()
                .map(TeacherSubmissionView.Choice::label).toList());
    }

    @Test void invalidCorrectnessKeysRemainNeutralAndDoNotHideReadableOptions() {
        for (String options : List.of(
                "[{\"option_id\":\"A\",\"option_text\":\"First\"},{\"option_id\":\"B\",\"option_text\":\"Second\",\"is_correct\":false}]",
                "[{\"option_id\":\"A\",\"option_text\":\"First\",\"is_correct\":\"true\"},{\"option_id\":\"B\",\"option_text\":\"Second\",\"is_correct\":false}]",
                "[{\"option_id\":\"A\",\"option_text\":\"First\",\"is_correct\":true},{\"option_id\":\"B\",\"option_text\":\"Second\",\"is_correct\":true}]",
                "[{\"option_id\":\"A\",\"option_text\":\"First\",\"is_correct\":false},{\"option_id\":\"B\",\"option_text\":\"Second\",\"is_correct\":false}]",
                "[{\"option_id\":\"A\",\"option_text\":\"First\",\"is_correct\":true},{\"option_id\":\"B\",\"option_text\":\"Second\",\"is_correct\":false},false]")) {
            var result = TeacherSubmissionView.answers("[{\"question_id\":1,\"selected_option_id\":\"B\"}]",List.of(question(options)));
            assertTrue(result.invalid());
            assertEquals(2,result.items().get(0).choices().size());
            assertTrue(result.items().get(0).choices().get(1).selected());
            assertTrue(result.items().get(0).choices().stream().allMatch(choice -> choice.correct() == null));
        }
    }

    @Test void duplicateOptionIdsAreNeutralAndCannotPretendToResolveAnAmbiguousSelection() {
        String options = """
                [{"option_id":"A","option_text":"First","is_correct":true},
                 {"option_id":"A","option_text":"Second","is_correct":false}]
                """;
        var result = TeacherSubmissionView.answers("[{\"question_id\":1,\"selected_option_id\":\"A\"}]",List.of(question(options)));
        assertTrue(result.invalid());
        assertEquals("Option #A (unavailable)",result.items().get(0).selectedOption());
        assertEquals(2,result.items().get(0).choices().size());
        assertTrue(result.items().get(0).choices().stream().allMatch(choice -> !choice.selected() && choice.correct() == null));
    }

    @Test void unknownSelectionPreservesAllOptionsAndTheUnavailableIdentity() {
        var result = TeacherSubmissionView.answers("[{\"question_id\":1,\"selected_option_id\":\"Z\",\"awarded_points\":5}]",questions);
        assertTrue(result.invalid());
        assertEquals("Option #Z (unavailable)",result.items().get(0).selectedOption());
        assertEquals("5",result.items().get(0).awardedPoints());
        assertEquals(2,result.items().get(0).choices().size());
        assertTrue(result.items().get(0).choices().stream().noneMatch(TeacherSubmissionView.Choice::selected));
    }

    @Test void badPointsWarnWithoutDroppingTheQuestionOrValidSelection() {
        for (String points : List.of("true","\"5\"","-1","1e999999999","1e-999999999","999999999999999999999999999")) {
            var result = TeacherSubmissionView.answers("[{\"question_id\":1,\"selected_option_id\":\"A\",\"awarded_points\":"+points+"}]",questions);
            assertTrue(result.invalid());
            assertEquals("Not graded",result.items().get(0).awardedPoints());
            assertEquals("Choice <b>",result.items().get(0).selectedOption());
            assertTrue(result.items().get(0).choices().get(0).selected());
        }
    }

    @Test void invalidQuestionIdsCannotResolveToAnotherQuestion() {
        for (String id : List.of("0","-1","1.5","true","9223372036854775808")) {
            var result = TeacherSubmissionView.answers("[{\"question_id\":"+id+",\"selected_option_id\":\"A\"}]",questions);
            assertTrue(result.invalid());
            assertEquals(1,result.items().size());
            assertEquals("No answer selected",result.items().get(0).selectedOption());
        }
    }

    @Test void answerAndChoiceListsAreImmutable() {
        var result = TeacherSubmissionView.answers("[]",questions);
        assertThrows(UnsupportedOperationException.class,() -> result.items().clear());
        assertThrows(UnsupportedOperationException.class,() -> result.items().get(0).choices().clear());
    }

    private Question question(String options) {
        return new Question(1,10,1,"Question",BigDecimal.TEN,options);
    }
}
