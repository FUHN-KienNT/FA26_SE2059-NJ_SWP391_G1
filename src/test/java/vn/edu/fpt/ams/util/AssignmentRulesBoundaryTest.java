package vn.edu.fpt.ams.util;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.AssignmentForm;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentRulesBoundaryTest {
    private Map<String,String> essay() {
        return new HashMap<>(Map.of("classId","1","templateId","1","title","Title",
                "maximumScore","10","purpose","REGULAR","responseType","ESSAY",
                "tasksJson","[{\"title\":\"Task\",\"maximum_points\":\"10\"}]"));
    }
    private Map<String,String> objective(String... points) {
        var form=essay(); form.put("responseType","OBJECTIVE");
        JsonArray questions=new JsonArray();
        for(String value:points) {
            JsonObject q=new JsonObject();q.addProperty("question_text","Q");q.addProperty("maximum_points",value);
            q.add("options",JsonParser.parseString("[{\"option_text\":\"A\",\"is_correct\":true},{\"option_text\":\"B\",\"is_correct\":true}]"));
            questions.add(q);
        }
        form.put("questionsJson",questions.toString());return form;
    }
    private AssignmentDraftValidator.Draft validate(Map<String,String> form) {
        return AssignmentDraftValidator.validate(new AssignmentForm(form));
    }
    private void invalid(Map<String,String> form,String field) {
        assertTrue(assertThrows(AssignmentDraftValidator.ValidationException.class,()->validate(form)).errors().containsKey(field));
    }
    @Test void maximumAllowsOnlyWholeNumbersOneToOneHundred() {
        for(String value:List.of("0","101","1.00","1.5","5+5","1e1","-1","+1","abc","")) {
            var form=essay();form.put("maximumScore",value);invalid(form,"maximumScore");
        }
        for(String value:List.of("1","99","100")) {
            var form=essay();form.put("maximumScore",value);
            form.put("tasksJson","[{\"title\":\"Task\",\"maximum_points\":\""+value+"\"}]");
            assertEquals(0,validate(form).assignment().maximumScore().compareTo(new BigDecimal(value)));
        }
    }
    @Test void textLimitsCountUnicodeCodePoints() {
        var form=essay();form.put("instructions","🚀".repeat(500));
        assertDoesNotThrow(()->validate(form));
        form.put("instructions","🚀".repeat(501));invalid(form,"instructions");
        form.put("instructions","");form.put("scopeText","ă".repeat(1001));
        assertEquals("", validate(form).assignment().scopeText(), "Scope is no longer part of the Essay form");
    }
    @Test void browserCrLfCountsAsOneNewlineAndIsStoredConsistently() {
        var form=essay();
        form.put("instructions", "a".repeat(498) + "\r\nb");
        form.put("scopeText", "a".repeat(998) + "\r\nb");
        var saved=validate(form).assignment();
        assertEquals(500, saved.instructions().length());
        assertEquals("", saved.scopeText());
        assertFalse(saved.instructions().contains("\r"));
        form.put("instructions", form.get("instructions") + "c");
        invalid(form,"instructions");
    }
    @Test void timeAndPracticeAttemptsRejectExpressionsAndInvalidBounds() {
        for(String value:List.of("19","721","20.0","20+20","2e1","abc","0")) {
            var form=essay();form.put("timeLimitMinutes",value);invalid(form,"timeLimitMinutes");
        }
        for(String value:List.of("","20","720")) {
            var form=essay();form.put("timeLimitMinutes",value);assertDoesNotThrow(()->validate(form));
        }
        for(String value:List.of("","0","1.0","1+1","abc","2147483648")) {
            var form=objective("10");form.put("purpose","PRACTICE");form.put("attemptLimit",value);invalid(form,"attemptLimit");
        }
        var form=objective("10");form.put("purpose","PRACTICE");form.put("attemptLimit","1");
        assertEquals(1,validate(form).assignment().attemptLimit());
    }
    @Test void exactlyOneBlankObjectiveScoreReceivesExactRemainingPoints() {
        var result=validate(objective("3.33","3.33",""));
        assertEquals(new BigDecimal("3.34"),result.questions().get(2).maximumPoints());
        assertEquals(new BigDecimal("10.00"),validate(objective("")).questions().get(0).maximumPoints().setScale(2));
        assertEquals(new BigDecimal("0.01"),validate(objective("9.99","")).questions().get(1).maximumPoints());
        invalid(objective("10",""),"questionsJson");
        invalid(objective("10.01",""),"questionsJson");
        invalid(objective("",""),"questionsJson");
        invalid(objective("3.333",""),"questionsJson");
        invalid(objective("5+5"),"questionsJson");
        invalid(objective("9.99"),"questionsJson");
        assertEquals(2,JsonParser.parseString(validate(objective("10")).questions().get(0).options()).getAsJsonArray().size());
    }
    @Test void essayPointsAreSavedAsDecimalsAndMustSumExactly() {
        var form=essay();form.put("tasksJson","[{\"title\":\"A\",\"maximum_points\":\"0.01\"},{\"title\":\"B\",\"maximum_points\":\"9.99\"}]");
        var tasks=JsonParser.parseString(validate(form).assignment().taskChecklistJson()).getAsJsonArray();
        assertEquals(new BigDecimal("0.01"),tasks.get(0).getAsJsonObject().get("maximum_points").getAsBigDecimal());
        form.put("tasksJson",form.get("tasksJson").replace("9.99","9.98"));invalid(form,"tasksJson");
    }
    @Test void activeContentCountsAndOptionLimitAreEnforcedOnServer() {
        for(int count:List.of(0,1,200,201)) {
            var form=essay();form.put("maximumScore","100"); JsonArray tasks=new JsonArray();
            for(int i=0;i<count;i++){JsonObject task=new JsonObject();task.addProperty("title","Task");task.addProperty("maximum_points",count==1?"100":"0.50");tasks.add(task);}
            form.put("tasksJson",tasks.toString());
            if(count==1||count==200) assertDoesNotThrow(()->validate(form));else invalid(form,"tasksJson");
        }
        for(int count:List.of(0,1,500,501)) {
            String[] points=new String[count];Arrays.fill(points,count==1?"100":"0.20");
            var form=objective(points);form.put("maximumScore","100");
            if(count==1||count==500) assertDoesNotThrow(()->validate(form));else invalid(form,"questionsJson");
        }
        for(int count:List.of(10,11)) {
            var form=objective("10");var qs=JsonParser.parseString(form.get("questionsJson")).getAsJsonArray();JsonArray options=new JsonArray();
            for(int i=0;i<count;i++){JsonObject option=new JsonObject();option.addProperty("option_text","Answer");option.addProperty("is_correct",i<2);options.add(option);}
            qs.get(0).getAsJsonObject().add("options",options);form.put("questionsJson",qs.toString());
            if(count==10) assertDoesNotThrow(()->validate(form));else invalid(form,"questionsJson");
        }
    }
}
