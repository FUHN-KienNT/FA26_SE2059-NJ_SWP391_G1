package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.dao.GroupAssignmentDAO;
import vn.edu.fpt.ams.dto.AssignmentForm;
import vn.edu.fpt.ams.util.AssignmentDraftValidator.ValidationException;
import vn.edu.fpt.ams.util.AssignmentEditConflictException;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.Jdbc;

/** Exercises audience writes in TEMP tables, never persistent application records. */
class GroupAssignmentAuthoringDatabaseTest {
    Connection connection;
    AssignmentService service;

    @BeforeEach void setup() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
        connection = DBConnection.getConnection();
        try (var s = connection.createStatement()) {
            s.execute("CREATE TEMP TABLE users(user_id BIGINT PRIMARY KEY,role TEXT,account_status TEXT,full_name VARCHAR(100))");
            s.execute("CREATE TEMP TABLE subjects(subject_id BIGINT PRIMARY KEY,subject_code TEXT,subject_name TEXT)");
            s.execute("CREATE TEMP TABLE academic_terms(term_id BIGINT PRIMARY KEY,term_code TEXT)");
            s.execute("CREATE TEMP TABLE subject_terms(subject_term_id BIGINT PRIMARY KEY,subject_id BIGINT,term_id BIGINT)");
            s.execute("CREATE TEMP TABLE subject_classes(class_id BIGINT PRIMARY KEY,subject_term_id BIGINT,teacher_id BIGINT,class_code TEXT,class_name TEXT,status TEXT DEFAULT 'ACTIVE')");
            s.execute("CREATE TEMP TABLE subject_registrations(registration_id BIGINT PRIMARY KEY,student_id BIGINT REFERENCES users,subject_term_id BIGINT,status TEXT)");
            s.execute("CREATE TEMP TABLE class_enrollments(enrollment_id BIGINT PRIMARY KEY,registration_id BIGINT REFERENCES subject_registrations,class_id BIGINT REFERENCES subject_classes,status TEXT)");
            s.execute("CREATE TEMP TABLE assignment_templates(template_id BIGINT PRIMARY KEY,subject_id BIGINT,title TEXT,instructions TEXT,maximum_score NUMERIC,status TEXT)");
            s.execute("CREATE TEMP TABLE system_settings(setting_id BIGINT PRIMARY KEY,setting_type TEXT,setting_value TEXT,display_name TEXT,priority INTEGER,is_active BOOLEAN)");
            s.execute("CREATE TEMP TABLE assignments(LIKE public.assignments INCLUDING ALL)");
            s.execute("CREATE TEMP TABLE questions(LIKE public.questions INCLUDING ALL)");
            s.execute("ALTER TABLE assignments ALTER COLUMN assignment_id DROP IDENTITY IF EXISTS");
            s.execute("CREATE TEMP SEQUENCE group_authoring_assignment_ids");
            s.execute("ALTER TABLE assignments ALTER COLUMN assignment_id SET DEFAULT nextval('group_authoring_assignment_ids')");
            s.execute("ALTER TABLE questions ALTER COLUMN question_id DROP IDENTITY IF EXISTS");
            s.execute("CREATE TEMP SEQUENCE group_authoring_question_ids");
            s.execute("ALTER TABLE questions ALTER COLUMN question_id SET DEFAULT nextval('group_authoring_question_ids')");
            s.execute("CREATE TEMP TABLE submissions(submission_id BIGINT PRIMARY KEY,assignment_id BIGINT REFERENCES assignments,enrollment_id BIGINT,attempt_number INTEGER,status TEXT)");
            s.execute("SET search_path TO pg_temp");
            s.execute(Files.readString(Path.of("database/migrations/007-teacher-project-groups.sql"))
                    .replace("CREATE TABLE IF NOT EXISTS", "CREATE TEMP TABLE"));
            s.execute(Files.readString(Path.of("database/migrations/009-group-assignments-peer-grading.sql"))
                    .replace("CREATE TABLE IF NOT EXISTS", "CREATE TEMP TABLE"));
            s.execute("""
                    INSERT INTO users VALUES(1,'TEACHER','ACTIVE','Teacher'),(2,'TEACHER','ACTIVE','Other'),
                    (4,'STUDENT','ACTIVE','One'),(5,'STUDENT','ACTIVE','Two'),(6,'STUDENT','ACTIVE','Three'),(7,'STUDENT','ACTIVE','Foreign');
                    INSERT INTO subjects VALUES(100,'SWP','Project'),(200,'OTHER','Other');
                    INSERT INTO academic_terms VALUES(1,'CURRENT');
                    INSERT INTO subject_terms VALUES(1000,100,1),(2000,200,1);
                    INSERT INTO subject_classes VALUES(10,1000,1,'A','Owned','ACTIVE'),(11,1000,1,'A2','Second','ACTIVE'),(20,2000,2,'B','Foreign','ACTIVE');
                    INSERT INTO subject_registrations VALUES(400,4,1000,'ACTIVE'),(500,5,1000,'ACTIVE'),(600,6,1000,'ACTIVE'),(700,7,2000,'ACTIVE');
                    INSERT INTO class_enrollments VALUES(104,400,10,'ACTIVE'),(105,500,10,'ACTIVE'),(106,600,11,'ACTIVE'),(207,700,20,'ACTIVE');
                    INSERT INTO assignment_templates VALUES(1,100,'Template','',20,'ACTIVE');
                    INSERT INTO system_settings VALUES(1,'ASSIGNMENT_TYPE','REGULAR','Regular',1,TRUE),(2,'RESPONSE_TYPE','OBJECTIVE','Objective',1,TRUE),(3,'RESPONSE_TYPE','ESSAY','Essay',2,TRUE);
                    INSERT INTO project_groups(group_id,class_id,group_name) VALUES(1001,10,'First'),(1002,10,'Empty'),(2001,11,'Second'),(3001,20,'Foreign');
                    INSERT INTO project_group_members(group_id,class_id,student_id,enrollment_id) VALUES(1001,10,4,104),(1001,10,5,105),(2001,11,6,106),(3001,20,7,207);
                    """);
        }
        Connection borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (p, method, args) -> {
                    if (method.getName().equals("close")) {
                        if (!connection.getAutoCommit()) connection.rollback();
                        connection.setReadOnly(false);
                        connection.setAutoCommit(true);
                        return null;
                    }
                    try { return method.invoke(connection, args); }
                    catch (InvocationTargetException ex) { throw ex.getCause(); }
                });
        service = new AssignmentService(new AssignmentDAO(), () -> borrowed);
    }

    @AfterEach void close() throws Exception { if (connection != null) connection.close(); }

    AssignmentForm form(String classes, String groupIds) {
        var values = new HashMap<String, String>();
        values.putAll(Map.of("classId", "10", "targetClassIds", classes, "templateId", "1",
                "title", "Group scenario", "purpose", "REGULAR", "responseType", "OBJECTIVE",
                "maximumScore", "20", "questionsJson", "[{\"question_text\":\"Question\",\"maximum_points\":\"20\",\"options\":[{\"option_text\":\"A\",\"is_correct\":true},{\"option_text\":\"B\",\"is_correct\":false}]}]"));
        values.putAll(Map.of("deliveryMode", "GROUP", "groupTargetMode", "SELECTED", "selectedGroupIds", groupIds,
                "teacherWeight", "70", "peerWeight", "30", "groupRevision", "0",
                "availableFrom", java.time.LocalDateTime.now().minusDays(1).toString(),
                "dueAt", java.time.LocalDateTime.now().plusDays(2).toString()));
        return new AssignmentForm(values);
    }

    long count(String table) throws Exception {
        if (!connection.getAutoCommit()) connection.commit();
        long count = Jdbc.scalar(connection, "SELECT count(*) FROM " + table);
        if (!connection.getAutoCommit()) connection.commit();
        return count;
    }
    void mutate(String sql) throws Exception {
        if (!connection.getAutoCommit()) connection.commit();
        connection.setReadOnly(false);
        connection.setAutoCommit(true);
        try (var s = connection.createStatement()) { s.execute(sql); }
    }

    @Test void selectedPublicationFreezesEveryMemberAndCanonicalOwner() throws Exception {
        long id = service.createAssignment(1, form("10", "1001"), true);
        assertEquals(1, count("assignment_group_targets"));
        assertEquals(2, count("assignment_group_members"));
        assertEquals(104, Jdbc.scalar(connection, "SELECT owner_enrollment_id FROM assignment_group_targets"));
        assertEquals(1, Jdbc.scalar(connection, "SELECT count(*) FROM assignments WHERE assignment_id=? AND status='PUBLISHED'", id));
        assertEquals(1, Jdbc.scalar(connection, "SELECT count(*) FROM assignment_group_targets WHERE frozen_at IS NOT NULL"));
        mutate("UPDATE project_groups SET group_name='Changed' WHERE group_id=1001; UPDATE project_group_members SET left_at=CURRENT_TIMESTAMP WHERE student_id=5;");
        assertEquals(2, count("assignment_group_members"));
        assertEquals("First", new GroupAssignmentDAO().targets(connection, id).get(0).name());
    }

    @Test void multiclassSelectionDistributesGroupsAndRejectsMissingOrForeignTargetsAtomically() throws Exception {
        assertThrows(ValidationException.class, () -> service.createAssignment(1, form("10,11", "1001"), true));
        assertEquals(0, count("assignments"));
        assertThrows(ValidationException.class, () -> service.createAssignment(1, form("10", "3001"), true));
        assertEquals(0, count("assignments"));
        service.createAssignment(1, form("10,11", "1001,2001"), true);
        assertEquals(2, count("assignments"));
        assertEquals(2, count("assignment_group_targets"));
        assertEquals(3, count("assignment_group_members"));
    }

    @Test void allGroupsExpandAtPublicationAndEmptyGroupsBlockWithoutPartialSnapshots() throws Exception {
        var values = new HashMap<>(form("10", "1001").values());
        values.put("groupTargetMode", "ALL");
        long id = service.createAssignment(1, new AssignmentForm(values));
        assertEquals(0, count("assignment_group_targets"));
        var revision = service.lifecyclePage(1, id, "publish").revision();
        assertThrows(ValidationException.class, () -> service.publishAssignment(1, id, revision));
        assertEquals(0, count("assignment_group_targets"));
        assertEquals(0, count("assignment_group_members"));
        mutate("UPDATE project_groups SET status='INACTIVE' WHERE group_id=1002");
        service.publishAssignment(1, id, service.lifecyclePage(1, id, "publish").revision());
        assertEquals(1, count("assignment_group_targets"));
        assertEquals(2, count("assignment_group_members"));
    }

    @Test void configOnlyEditInvalidatesBothEditorAndLifecycleRevisions() throws Exception {
        long id = service.createAssignment(1, form("10", "1001"));
        var stalePublish = service.lifecyclePage(1, id, "publish").revision();
        var original = service.editPage(1, id).form();
        var values = new HashMap<>(original.values());
        values.put("teacherWeight", "61"); values.put("peerWeight", "39");
        service.updateAssignment(1, id, new AssignmentForm(values));
        assertThrows(AssignmentEditConflictException.class, () -> service.updateAssignment(1, id, original));
        assertThrows(AssignmentEditConflictException.class, () -> service.publishAssignment(1, id, stalePublish));
        assertEquals("1", service.editPage(1, id).form().value("groupRevision"));
        service.publishAssignment(1, id, service.lifecyclePage(1, id, "publish").revision());
        assertEquals(61, Jdbc.scalar(connection, "SELECT teacher_weight FROM assignment_group_settings WHERE assignment_id=?", id));
    }

    @Test void invalidWeightsAndEmptySelectedGroupsNeverCreatePartialAssignments() throws Exception {
        var values = new HashMap<>(form("10", "1002").values());
        assertThrows(ValidationException.class, () -> service.createAssignment(1, new AssignmentForm(values), true));
        values.put("selectedGroupIds", "1001"); values.put("teacherWeight", "70.01");
        assertThrows(ValidationException.class, () -> service.createAssignment(1, new AssignmentForm(values), true));
        assertEquals(0, count("assignments"));
        assertEquals(0, count("assignment_group_settings"));
    }
}
