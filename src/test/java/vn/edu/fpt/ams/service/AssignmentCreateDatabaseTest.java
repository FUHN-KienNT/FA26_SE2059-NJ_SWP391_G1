package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import com.google.gson.JsonParser;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.dto.AssignmentForm;
import vn.edu.fpt.ams.model.Question;
import vn.edu.fpt.ams.util.AssignmentDraftValidator.ValidationException;
import vn.edu.fpt.ams.util.DBConnection;

/** Real PostgreSQL, isolated TEMP tables and sequences; never writes application data. */
class AssignmentCreateDatabaseTest {
    Connection connection;
    @BeforeEach void setup() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
        connection=DBConnection.getConnection();
        try(var s=connection.createStatement()) {
            s.execute("CREATE TEMP TABLE users(user_id BIGINT PRIMARY KEY,role TEXT,account_status TEXT)");
            s.execute("CREATE TEMP TABLE subjects(subject_id BIGINT PRIMARY KEY,subject_code TEXT,subject_name TEXT)");
            s.execute("CREATE TEMP TABLE subject_terms(subject_term_id BIGINT PRIMARY KEY,subject_id BIGINT)");
            s.execute("CREATE TEMP TABLE subject_classes(class_id BIGINT PRIMARY KEY,subject_term_id BIGINT,teacher_id BIGINT,class_code TEXT,class_name TEXT)");
            s.execute("CREATE TEMP TABLE assignment_templates(template_id BIGINT PRIMARY KEY,subject_id BIGINT,title TEXT,instructions TEXT,maximum_score NUMERIC,status TEXT)");
            s.execute("CREATE TEMP TABLE system_settings(setting_id BIGINT PRIMARY KEY,setting_type TEXT,setting_value TEXT,display_name TEXT,priority INTEGER,is_active BOOLEAN)");
            s.execute("CREATE TEMP TABLE assignments(LIKE public.assignments INCLUDING ALL)");
            s.execute("CREATE TEMP TABLE questions(LIKE public.questions INCLUDING ALL)");
            // Explicit local identity sequences also isolate schemas using serial defaults.
            s.execute("ALTER TABLE assignments ALTER COLUMN assignment_id DROP IDENTITY IF EXISTS");
            s.execute("CREATE TEMP SEQUENCE create_assignment_test_ids");
            s.execute("ALTER TABLE assignments ALTER COLUMN assignment_id SET DEFAULT nextval('create_assignment_test_ids')");
            s.execute("ALTER TABLE questions ALTER COLUMN question_id DROP IDENTITY IF EXISTS");
            s.execute("CREATE TEMP SEQUENCE create_question_test_ids");
            s.execute("ALTER TABLE questions ALTER COLUMN question_id SET DEFAULT nextval('create_question_test_ids')");
            s.execute("SET search_path TO pg_temp");
            s.execute("INSERT INTO system_settings VALUES (1,'ASSIGNMENT_TYPE','REGULAR','Regular',1,TRUE),(2,'ASSIGNMENT_TYPE','PRACTICE','Practice',2,TRUE),(3,'RESPONSE_TYPE','ESSAY','Essay',1,TRUE),(4,'RESPONSE_TYPE','OBJECTIVE','Objective',2,TRUE)");
            s.execute("INSERT INTO users VALUES(1,'TEACHER','ACTIVE'),(2,'TEACHER','ACTIVE'),(3,'TEACHER','INACTIVE'),(4,'STUDENT','ACTIVE')");
            s.execute("INSERT INTO subjects VALUES(100,'SWP391','Project'),(200,'PRJ301','Java')");
            s.execute("INSERT INTO subject_terms VALUES(1000,100),(2000,200)");
            s.execute("INSERT INTO subject_classes VALUES(10,1000,1,'A','Owned'),(20,2000,2,'B','Foreign')");
            s.execute("INSERT INTO assignment_templates VALUES(1,100,'Template title','Template instructions',10,'ACTIVE'),(2,100,'Inactive','Instructions',10,'INACTIVE'),(3,200,'Foreign','Instructions',10,'ACTIVE')");
        }
    }
    @AfterEach void close() throws Exception { if(connection!=null) connection.close(); }
    AssignmentService service(AssignmentDAO dao) {
        // Keep this test connection open to inspect committed data / rollback afterwards.
        Connection borrowed=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("close")) return null;
            try{return m.invoke(connection,a);}catch(InvocationTargetException ex){throw ex.getCause();}
        });
        return new AssignmentService(dao,()->borrowed);
    }
    AssignmentForm form(String template) {
        return new AssignmentForm(Map.of("classId","10","templateId",template,"title","Teacher edited title",
                "instructions","Teacher edited instructions","maximumScore","20","purpose","REGULAR","responseType","OBJECTIVE",
                "scopeText","Inactive Essay scope",
                "tasksJson","[{\"title\":\"Task\",\"description\":\"Details\"}]",
                "questionsJson","[{\"question_text\":\"Question\",\"maximum_points\":\"20\",\"options\":[{\"option_text\":\"A\",\"is_correct\":true}]}]"));
    }
    int count(String table) throws Exception {
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT count(*) FROM "+table)){r.next();return r.getInt(1);}
    }
    @Test void inactiveAssignmentTypeCannotBeUsedForNewAssignment() throws Exception {
        try (var s = connection.createStatement()) {
            s.executeUpdate("UPDATE system_settings SET is_active=FALSE WHERE setting_type='ASSIGNMENT_TYPE' AND setting_value='PRACTICE'");
        }
        var values = new HashMap<>(form("1").values());
        values.put("purpose", "PRACTICE");
        values.put("attemptLimit", "1");
        var error = assertThrows(ValidationException.class,
                () -> service(new AssignmentDAO()).createAssignment(1, new AssignmentForm(values)));
        assertTrue(error.errors().containsKey("purpose"));
        assertEquals(0, count("assignments"));
    }
    @Test void inactiveResponseTypeCannotBeUsedForNewAssignment() throws Exception {
        try (var s = connection.createStatement()) {
            s.executeUpdate("UPDATE system_settings SET is_active=FALSE WHERE setting_type='RESPONSE_TYPE' AND setting_value='OBJECTIVE'");
        }
        var error = assertThrows(ValidationException.class,
                () -> service(new AssignmentDAO()).createAssignment(1, form("1")));
        assertTrue(error.errors().containsKey("responseType"));
        assertEquals(0, count("assignments"));
        assertEquals(0, count("questions"));
    }
    @Test void abnormalConfigurationWithoutAnyPairRejectsDirectCreate() throws Exception {
        try (var s = connection.createStatement()) {
            s.executeUpdate("UPDATE system_settings SET is_active=FALSE WHERE setting_type='RESPONSE_TYPE'");
        }
        assertThrows(ValidationException.class,
                () -> service(new AssignmentDAO()).createAssignment(1, form("1")));
        assertEquals(0, count("assignments"));
    }
    @Test void savesEditedTemplateContentAndNestedQuestionsAsObjectiveDraft() throws Exception {
        long id=service(new AssignmentDAO()).createAssignment(1,form("1"));
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT a.*,q.question_order,q.options FROM assignments a JOIN questions q USING(assignment_id) WHERE assignment_id="+id)) {
            assertTrue(r.next()); assertEquals("Teacher edited title",r.getString("title"));
            assertEquals("Teacher edited instructions",r.getString("instructions")); assertEquals("DRAFT",r.getString("status"));
            assertEquals(20,r.getBigDecimal("maximum_score").intValue()); assertNull(r.getObject("published_at"));
            assertEquals("",r.getString("scope_text")); assertEquals("[]",r.getString("task_checklist"));
            assertTrue(r.getString("options").contains("option_id"));
            assertEquals(1,r.getInt("question_order")); assertFalse(r.next());
        }
        assertEquals(1,count("assignments")); assertEquals(1,count("questions"));
    }
    @Test void savesObjectiveDraftWithMalformedInactiveEssayTasks() throws Exception {
        var values=new HashMap<>(form("1").values());
        values.put("purpose","PRACTICE"); values.put("attemptLimit","1"); values.put("tasksJson","{broken");
        long id=service(new AssignmentDAO()).createAssignment(1,new AssignmentForm(values));
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT scope_text,task_checklist,status,assignment_purpose FROM assignments WHERE assignment_id="+id)) {
            assertTrue(r.next()); assertEquals("",r.getString("scope_text"));
            assertEquals("[]",r.getString("task_checklist")); assertEquals("DRAFT",r.getString("status"));
            assertEquals("PRACTICE",r.getString("assignment_purpose")); assertFalse(r.next());
        }
        assertEquals(1,count("assignments")); assertEquals(1,count("questions"));
    }
    @Test void savesEssayScopeAndTasksWhileIgnoringInactiveObjectiveQuestions() throws Exception {
        for(String questions:List.of(form("1").value("questionsJson"),"{broken")) {
            var values=new HashMap<>(form("1").values());
            values.put("responseType","ESSAY"); values.put("scopeText","UML & Java");
            values.put("tasksJson","""
                    [{"title":"Task one","description":"Full description","task_order":99,"maximum_points":"10"},
                     {"title":"Task two","description":"Second description","task_order":7,"maximum_points":"10"}]
                    """);
            values.put("questionsJson",questions);
            long id=service(new AssignmentDAO()).createAssignment(1,new AssignmentForm(values));
            try(var s=connection.createStatement();var r=s.executeQuery("SELECT scope_text,task_checklist,status,response_type FROM assignments WHERE assignment_id="+id)) {
                assertTrue(r.next()); assertEquals("",r.getString("scope_text"));
                assertEquals("DRAFT",r.getString("status")); assertEquals("ESSAY",r.getString("response_type"));
                var tasks=JsonParser.parseString(r.getString("task_checklist")).getAsJsonArray();
                assertEquals(2,tasks.size());
                assertEquals(1,tasks.get(0).getAsJsonObject().get("task_order").getAsInt());
                assertEquals("Task one\nFull description",tasks.get(0).getAsJsonObject().get("title").getAsString());
                assertEquals("",tasks.get(0).getAsJsonObject().get("description").getAsString());
                assertEquals(2,tasks.get(1).getAsJsonObject().get("task_order").getAsInt());
                assertEquals("Task two\nSecond description",tasks.get(1).getAsJsonObject().get("title").getAsString());
                assertEquals("",tasks.get(1).getAsJsonObject().get("description").getAsString());
                assertFalse(r.next());
            }
        }
        assertEquals(2,count("assignments")); assertEquals(0,count("questions"));
    }
    @Test void rejectsEssayAndObjectiveDraftsWithoutAnyTaskOrQuestion() throws Exception {
        for(String response:List.of("ESSAY","OBJECTIVE")) {
            var values=new HashMap<>(form("1").values());
            values.put("responseType",response); values.put("instructions",""); values.put("scopeText","");
            values.put("tasksJson","[]"); values.put("questionsJson","[]");
            var error=assertThrows(ValidationException.class,
                    ()->service(new AssignmentDAO()).createAssignment(1,new AssignmentForm(values)));
            assertTrue(error.errors().containsKey(response.equals("ESSAY")?"tasksJson":"questionsJson"));
        }
        assertEquals(0,count("assignments")); assertEquals(0,count("questions"));
    }
    @Test void rejectsForeignClassInactiveOrWrongRoleTeacherAndInvalidTemplates() throws Exception {
        var service=service(new AssignmentDAO());
        for(long actor:new long[]{2,3,4,999}) assertThrows(SecurityException.class,()->service.createAssignment(actor,form("1")));
        for(String template:List.of("2","3","999")) assertThrows(ValidationException.class,()->service.createAssignment(1,form(template)));
        assertEquals(0,count("assignments")); assertEquals(0,count("questions"));
    }
    @Test void questionFailureRollsBackParentInsert() throws Exception {
        var dao=new AssignmentDAO(){
            @Override public void insertDraftQuestions(Connection c,long id,List<Question> questions)throws SQLException {
                super.insertDraftQuestions(c,id,questions);
                throw new SQLException("Injected failure after nested insert");
            }
        };
        assertThrows(IllegalStateException.class,()->service(dao).createAssignment(1,form("1")));
        assertEquals(0,count("assignments")); assertEquals(0,count("questions"));
    }
    @Test void formListsOnlyActiveTemplatesForOwnedSubject() {
        var service=service(new AssignmentDAO());
        var page=service.createPage(1,10);
        assertEquals(1,page.templates().size()); assertEquals(1,page.templates().get(0).templateId());
        assertThrows(SecurityException.class,()->service.createPage(2,10));
    }
}
