package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import vn.edu.fpt.ams.dao.StudentResultDAO;
import vn.edu.fpt.ams.util.DBConnection;

/** SQL-redaction and historical access checks against connection-local TEMP tables. */
class StudentResultDatabaseTest {
    Connection connection;
    @BeforeEach void setup()throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
        connection=DBConnection.getConnection();
        sql("""
            CREATE TEMP TABLE users(user_id BIGINT PRIMARY KEY,role TEXT,account_status TEXT);
            CREATE TEMP TABLE subject_classes(class_id BIGINT PRIMARY KEY,subject_term_id BIGINT,class_code TEXT,status TEXT);
            CREATE TEMP TABLE subject_registrations(registration_id BIGINT PRIMARY KEY,student_id BIGINT,subject_term_id BIGINT,status TEXT);
            CREATE TEMP TABLE class_enrollments(enrollment_id BIGINT PRIMARY KEY,registration_id BIGINT,class_id BIGINT,status TEXT);
            CREATE TEMP TABLE assignments(LIKE public.assignments INCLUDING ALL);
            CREATE TEMP TABLE submissions(LIKE public.submissions INCLUDING ALL);
            CREATE TEMP TABLE submission_content_snapshots(submission_id BIGINT PRIMARY KEY,assignment_data JSONB,questions_data JSONB);
            SET search_path TO pg_temp;
            INSERT INTO users VALUES(1,'STUDENT','ACTIVE'),(2,'STUDENT','ACTIVE'),(3,'TEACHER','ACTIVE');
            INSERT INTO subject_classes VALUES(1,10,'QA-1','ACTIVE'),(2,20,'QA-2','ACTIVE');
            INSERT INTO subject_registrations VALUES(1,1,10,'ACTIVE'),(2,2,10,'ACTIVE'),(3,1,20,'ACTIVE');
            INSERT INTO class_enrollments VALUES(1,1,1,'ACTIVE'),(2,2,1,'ACTIVE'),(3,3,1,'ACTIVE'),(4,1,2,'ACTIVE');
            INSERT INTO assignments(assignment_id,class_id,template_id,title,assignment_purpose,response_type,instructions,maximum_score,status,available_from,due_at,published_at,task_checklist)
            VALUES(10,1,1,'Đề gốc','REGULAR','ESSAY','Hướng dẫn gốc',10,'PUBLISHED',now()-interval '2 days',now()+interval '1 day',now()-interval '2 days',
            '[{"task_order":1,"title":"Câu gốc","maximum_points":10,"requirements":[{"requirement_order":1,"title":"Yêu cầu gốc","maximum_points":10,"answer_type":"TEXT"}]}]');
            INSERT INTO submissions(submission_id,assignment_id,enrollment_id,attempt_number,status,started_at,deadline_at,submitted_at,content,score,feedback,graded_at,released_at,evaluation)
            VALUES(101,10,1,1,'RELEASED',now()-interval '2 hours',now()+interval '1 hour',now()-interval '1 hour','{"format":"ams-essay-v2","answers":[{"task_order":1,"requirement_order":1,"text":"Câu trả lời riêng"}]}',7.5,'Nhận xét đã công bố',now()-interval '30 minutes',now()-interval '20 minutes','{"format":"ams-evaluation-v1","marks":[{"task_order":1,"requirement_order":1,"score":7.5,"feedback":"Nhận xét yêu cầu"}]}'),
            (102,10,1,2,'GRADED',now()-interval '2 hours',now()+interval '1 hour',now()-interval '1 hour','secret-content',9,'secret-feedback',now()-interval '30 minutes',NULL,'{"format":"ams-evaluation-v1","marks":[{"task_order":1,"requirement_order":1,"score":9,"feedback":"secret-mark"}]}'),
            (103,10,2,1,'RELEASED',now()-interval '2 hours',now()+interval '1 hour',now()-interval '1 hour','Other student',8,'Other feedback',now()-interval '30 minutes',now()-interval '20 minutes','{"format":"ams-evaluation-v1","marks":[]}'),
            (104,10,3,1,'RELEASED',now()-interval '2 hours',now()+interval '1 hour',now()-interval '1 hour','Wrong term',8,'Wrong term',now()-interval '30 minutes',now()-interval '20 minutes','{"format":"ams-evaluation-v1","marks":[]}'),
            (105,10,4,1,'RELEASED',now()-interval '2 hours',now()+interval '1 hour',now()-interval '1 hour','Wrong class',8,'Wrong class',now()-interval '30 minutes',now()-interval '20 minutes','{"format":"ams-evaluation-v1","marks":[]}');
            INSERT INTO submission_content_snapshots SELECT sub.submission_id,to_jsonb(a),'[]'::jsonb FROM submissions sub JOIN assignments a ON a.assignment_id=sub.assignment_id;
            """);
    }
    @AfterEach void close()throws Exception{if(connection!=null)connection.close();}
    void sql(String sql)throws Exception{
        connection.setAutoCommit(true);connection.setReadOnly(false);
        try(var statement=connection.createStatement()){statement.execute(sql);}
    }
    StudentResultService service(){
        var borrowed=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("close"))return null;
            try{return m.invoke(connection,a);}catch(InvocationTargetException e){throw e.getCause();}
        });
        return new StudentResultService(new StudentResultDAO(),()->borrowed);
    }
    @Test void pendingScoresCommentsAnswersAndKeysAreRedactedAtSqlBoundary()throws Exception{
        var row=new StudentResultDAO().detail(connection,1,10,102).orElseThrow();
        assertFalse(row.attempt().released());assertNull(row.attempt().score());assertNull(row.attempt().releasedAt());
        assertNull(row.content());assertNull(row.answer());assertNull(row.feedback());assertNull(row.evaluation());
        assertNull(row.assignmentSnapshot());assertNull(row.questionsSnapshot());assertEquals(0,row.revision());
        var page=service().load(1,10,102L);assertNull(page.detail());assertNull(page.selected().score());
        assertFalse(page.toString().contains("secret-"));
        sql("UPDATE submissions SET status='SUBMITTED',graded_at=NULL WHERE submission_id=102");
        assertNull(service().load(1,10,102L).detail());
        sql("UPDATE submissions SET status='RELEASED',graded_at=now(),released_at=now()+interval '1 hour' WHERE submission_id=102");
        var future=service().load(1,10,102L);assertNull(future.detail());assertNull(future.selected().score());
    }
    @Test void onlyOriginalStudentAndMatchingEnrollmentContextCanSeeAcceptedAttempts(){
        var service=service();assertEquals(List.of(102L,101L),service.load(1,10,null).attempts().stream().map(x->x.submissionId()).toList());
        for(long id:List.of(103L,104L,105L,999L))assertThrows(NoSuchElementException.class,()->service.load(1,10,id));
        assertThrows(NoSuchElementException.class,()->service.load(2,10,101L));
        assertThrows(SecurityException.class,()->service.load(3,10,101L));
        assertThrows(SecurityException.class,()->service.load(999,10,101L));
    }
    @Test void releasedEssayUsesFrozenRubricAndSurvivesInactiveMembershipAndCancellation()throws Exception{
        sql("UPDATE assignments SET title='Changed title',maximum_score=100,response_type='OBJECTIVE',task_checklist='[]',status='CANCELLED',cancelled_at=now();UPDATE class_enrollments SET status='INACTIVE';UPDATE subject_registrations SET status='INACTIVE';UPDATE subject_classes SET status='INACTIVE'");
        var page=service().load(1,10,101L);assertNotNull(page.detail());assertEquals("Đề gốc",page.detail().title());
        assertEquals(0,new BigDecimal("10").compareTo(page.detail().maximumScore()));assertEquals(0,new BigDecimal("7.5").compareTo(page.detail().score()));
        assertEquals("Nhận xét yêu cầu",page.detail().evaluation().mark(1,1).feedback());
        assertEquals("Câu trả lời riêng",page.detail().essay().items().get(0).requirements().get(0).answer());
        sql("UPDATE users SET account_status='INACTIVE' WHERE user_id=1");assertThrows(SecurityException.class,()->service().load(1,10,101L));
    }
    @Test void invalidOrMissingHistoricalContentDoesNotInventAnswerPairings()throws Exception{
        sql("DELETE FROM submission_content_snapshots WHERE submission_id=101");
        var result=service().load(1,10,101L).detail();assertTrue(result.contentUnavailable());assertNull(result.essay());assertEquals("Nhận xét đã công bố",result.feedback());
        sql("INSERT INTO submission_content_snapshots SELECT 101,to_jsonb(a),'[]'::jsonb FROM assignments a WHERE assignment_id=10;UPDATE submissions SET evaluation='{\"format\":\"ams-evaluation-v1\",\"marks\":[{\"task_order\":1,\"requirement_order\":1,\"score\":10,\"feedback\":\"wrong total\"}]}' WHERE submission_id=101");
        var inconsistent=service().load(1,10,101L).detail();assertTrue(inconsistent.evaluationUnavailable());assertTrue(inconsistent.evaluation().marks().isEmpty());assertNotNull(inconsistent.essay());
    }
    @Test void releasedObjectiveShowsSavedChoicesAndCorrectnessWithoutRecalculatingScore()throws Exception{
        sql("""
            UPDATE submission_content_snapshots SET assignment_data=jsonb_set(assignment_data,'{response_type}','"OBJECTIVE"'),questions_data='[{"question_id":1,"assignment_id":10,"question_order":1,"question_text":"Chọn đáp án","maximum_points":10,"options":[{"option_id":"A","option_text":"Đúng","is_correct":true},{"option_id":"B","option_text":"Sai","is_correct":false}]}]' WHERE submission_id=101;
            UPDATE submissions SET score=0,answer='[{"question_id":1,"selected_option_ids":["B"],"awarded_points":0}]' WHERE submission_id=101;
            """);
        var result=service().load(1,10,101L).detail();assertNull(result.essay());
        var answer=result.objective().items().get(0);assertEquals("0",answer.awardedPoints());
        assertEquals(true,answer.choices().get(0).correct());assertFalse(answer.choices().get(0).selected());
        assertEquals(false,answer.choices().get(1).correct());assertTrue(answer.choices().get(1).selected());
    }
}
