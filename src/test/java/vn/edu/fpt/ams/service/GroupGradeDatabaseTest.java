package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.util.*;

/** Group grades and peer authorization exercise actual PostgreSQL only in session-local tables. */
class GroupGradeDatabaseTest {
    private Connection connection,borrowed;
    private GroupGradeService service;
    private static final String TASKS="""
            [{"task_order":1,"title":"Câu hỏi nhóm","maximum_points":10,"rubric":"PRIVATE_RUBRIC",
              "requirements":[{"requirement_order":1,"title":"Giải thích","maximum_points":10,
              "answer_type":"TEXT","rubric":"PRIVATE_CHILD_RUBRIC"}]}]
            """;

    @BeforeEach void setup() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
        connection=DBConnection.getConnection();
        sql("""
                SET search_path TO pg_temp;
                CREATE TEMP TABLE users(user_id BIGINT PRIMARY KEY,role TEXT,account_status TEXT,full_name TEXT,email TEXT);
                CREATE TEMP TABLE subjects(subject_id BIGINT PRIMARY KEY,subject_code TEXT,subject_name TEXT);
                CREATE TEMP TABLE academic_terms(term_id BIGINT PRIMARY KEY,term_code TEXT);
                CREATE TEMP TABLE subject_terms(subject_term_id BIGINT PRIMARY KEY,subject_id BIGINT,term_id BIGINT);
                CREATE TEMP TABLE assignment_templates(template_id BIGINT PRIMARY KEY,title TEXT);
                CREATE TEMP TABLE subject_classes(class_id BIGINT PRIMARY KEY,subject_term_id BIGINT,class_code TEXT,class_name TEXT,status TEXT,teacher_id BIGINT);
                CREATE TEMP TABLE subject_registrations(registration_id BIGINT PRIMARY KEY,student_id BIGINT,subject_term_id BIGINT,status TEXT);
                CREATE TEMP TABLE class_enrollments(enrollment_id BIGINT PRIMARY KEY,registration_id BIGINT,class_id BIGINT,status TEXT);
                CREATE TEMP TABLE project_groups(group_id BIGINT PRIMARY KEY,class_id BIGINT,UNIQUE(group_id,class_id));
                CREATE TEMP TABLE assignments(LIKE public.assignments INCLUDING ALL);
                CREATE TEMP TABLE questions(LIKE public.questions INCLUDING ALL);
                CREATE TEMP TABLE submissions(LIKE public.submissions INCLUDING ALL);
                ALTER TABLE submissions ALTER COLUMN submission_id DROP IDENTITY IF EXISTS;
                CREATE TEMP TABLE submission_content_snapshots(submission_id BIGINT PRIMARY KEY,assignment_data JSONB,questions_data JSONB);
                CREATE TEMP TABLE submission_evaluation_history(history_id BIGINT GENERATED ALWAYS AS IDENTITY,submission_id BIGINT,assignment_id BIGINT,student_id BIGINT,actor_id BIGINT,action TEXT,reason TEXT,previous_evaluation JSONB,next_evaluation JSONB,previous_score NUMERIC,next_score NUMERIC,previous_status TEXT,next_status TEXT,created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP);
                INSERT INTO users VALUES(1,'STUDENT','ACTIVE','Một','one@test'),(2,'STUDENT','ACTIVE','Hai','two@test'),
                    (3,'STUDENT','ACTIVE','Ba','three@test'),(4,'STUDENT','ACTIVE','Ngoài nhóm','other@test'),
                    (5,'STUDENT','INACTIVE','Không hoạt động','inactive@test'),(9,'TEACHER','ACTIVE','Giảng viên','teacher@test'),
                    (99,'TEACHER','ACTIVE','Giảng viên khác','foreign@test');
                INSERT INTO subjects VALUES(1,'WEB','Web');
                INSERT INTO academic_terms VALUES(1,'TERM');
                INSERT INTO subject_terms VALUES(100,1,1);
                INSERT INTO assignment_templates VALUES(1,'Mẫu');
                INSERT INTO subject_classes VALUES(10,100,'GROUP-A','Lớp nhóm','ACTIVE',9);
                INSERT INTO subject_registrations VALUES(11,1,100,'ACTIVE'),(22,2,100,'ACTIVE'),(33,3,100,'ACTIVE'),(44,4,100,'ACTIVE');
                INSERT INTO class_enrollments VALUES(1,11,10,'ACTIVE'),(2,22,10,'ACTIVE'),(3,33,10,'ACTIVE'),(4,44,10,'ACTIVE');
                INSERT INTO project_groups VALUES(7,10),(8,10);
                """);
        sql(Files.readString(Path.of("database/migrations/009-group-assignments-peer-grading.sql"))
                .replace("CREATE TABLE IF NOT EXISTS","CREATE TEMP TABLE IF NOT EXISTS")
                .replace("BEGIN;","").replace("COMMIT;",""));
        try(var p=connection.prepareStatement("""
                INSERT INTO assignments(assignment_id,class_id,template_id,title,assignment_purpose,response_type,
                    instructions,task_checklist,maximum_score,status,available_from,due_at,published_at)
                SELECT id,10,1,'Bài '||id,'REGULAR',CASE WHEN id=51 THEN 'OBJECTIVE' ELSE 'ESSAY' END,
                    '',?::jsonb,10,'PUBLISHED',now()-interval '2 days',now()-interval '1 day',now()-interval '2 days'
                FROM (VALUES(50),(51)) value(id)
                """)) {p.setString(1,TASKS);p.executeUpdate();}
        sql("""
                INSERT INTO assignment_group_settings(assignment_id,delivery_mode,target_mode,teacher_weight,peer_weight)
                    VALUES(50,'GROUP','ALL',70,30),(51,'GROUP','ALL',70,30);
                INSERT INTO assignment_group_targets(target_id,assignment_id,class_id,group_id,group_name,owner_enrollment_id,frozen_at)
                    VALUES(70,50,10,7,'Nhóm gốc',1,now()),(71,51,10,7,'Nhóm gốc',1,now()),
                          (72,50,10,8,'Nhóm chưa làm',4,now());
                INSERT INTO assignment_group_members(target_id,student_id,enrollment_id,student_name)
                    SELECT target,student,student,CASE student WHEN 1 THEN 'Một' WHEN 2 THEN 'Hai' ELSE 'Ba' END
                    FROM (VALUES(70),(71)) targets(target) CROSS JOIN (VALUES(1),(2),(3)) people(student);
                INSERT INTO assignment_group_members VALUES(72,4,4,'Ngoài nhóm');
                INSERT INTO submissions(submission_id,assignment_id,enrollment_id,attempt_number,status,started_at,deadline_at,submitted_at,assignment_group_target_id)
                    VALUES(500,50,1,1,'SUBMITTED',now()-interval '2 days',now()-interval '1 day',now()-interval '1 day',70),
                          (501,51,1,1,'SUBMITTED',now()-interval '2 days',now()-interval '1 day',now()-interval '1 day',71);
                """);
        borrowed=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,args)->{
            if(m.getName().equals("close")) {
                if(!connection.getAutoCommit()) connection.rollback();
                connection.setReadOnly(false);connection.setAutoCommit(true);return null;
            }
            try{return m.invoke(connection,args);}catch(InvocationTargetException ex){throw ex.getCause();}
        });
        service=new GroupGradeService(()->borrowed);
    }

    @AfterEach void close() throws Exception {if(connection!=null) connection.close();}
    private BigDecimal n(String value) {return new BigDecimal(value);}
    private void sql(String sql) throws Exception {
        if(!connection.getAutoCommit()) connection.commit();
        connection.setReadOnly(false);connection.setAutoCommit(true);
        try(var s=connection.createStatement()){s.execute(sql);}
    }
    private GroupGradeService.ReviewInput review(String value,long revision) {return new GroupGradeService.ReviewInput(value,"Nhận xét",revision);}
    private BigDecimal score(long target,long student) throws Exception {
        try(var p=Jdbc.statement(connection,"SELECT final_score FROM assignment_group_results WHERE target_id=? AND student_id=?",target,student);var r=p.executeQuery()) {
            assertTrue(r.next());return r.getBigDecimal(1);
        }
    }

    private void pendingReplacement(long previousId, long nextId) throws Exception {
        try (var statement = Jdbc.statement(connection, """
                INSERT INTO submissions(submission_id,assignment_id,enrollment_id,attempt_number,status,
                    started_at,deadline_at,assignment_group_target_id)
                SELECT ?,assignment_id,enrollment_id,attempt_number+1,'IN_PROGRESS',
                    started_at,deadline_at,assignment_group_target_id
                FROM submissions WHERE submission_id=?
                """, nextId, previousId)) {
            assertEquals(1, statement.executeUpdate());
        }
    }

    @Test void peerReviewsEnforceTimingMembershipDecimalBoundsAndRevision() throws Exception {
        sql("UPDATE assignments SET due_at=now()+interval '1 day' WHERE assignment_id=50");
        assertThrows(IllegalArgumentException.class,()->service.savePeerReviews(1,70,Map.of(2L,review("8",0))));
        sql("UPDATE assignments SET closed_at=now() WHERE assignment_id=50");
        service.savePeerReviews(1,70,Map.of(2L,review("8",0)));
        assertEquals(n("8.00"),service.peerPage(1,70L,1,1).reviews().stream().filter(x->x.studentId()==2).findFirst().orElseThrow().score());
        assertTrue(service.peerPage(2,70L,1,1).reviews().stream().allMatch(x->x.score()==null));
        assertThrows(SecurityException.class,()->service.savePeerReviews(1,70,Map.of(1L,review("8",0))));
        assertThrows(SecurityException.class,()->service.savePeerReviews(1,70,Map.of(4L,review("8",0))));
        assertThrows(NoSuchElementException.class,()->service.savePeerReviews(4,70,Map.of(1L,review("8",0))));
        assertThrows(SecurityException.class,()->service.peerPage(5,70L,1,1));
        assertThrows(IllegalArgumentException.class,()->service.savePeerReviews(1,70,Map.of(2L,review("10.01",1))));
        assertThrows(ConcurrentModificationException.class,()->service.savePeerReviews(1,70,Map.of(2L,review("9",0))));
        service.savePeerReviews(1,70,Map.of(2L,review("9",1)));
        sql("UPDATE assignment_group_targets SET peer_closed_at=now() WHERE target_id=70");
        assertThrows(IllegalArgumentException.class,()->service.savePeerReviews(1,70,Map.of(2L,review("10",2))));
    }

    @Test void objectivePublicationUsesAvailablePeerReviewsAndFreezesEveryMemberResult() throws Exception {
        service.savePeerReviews(1,71,Map.of(2L,review("9",0)));
        service.savePeerReviews(3,71,Map.of(2L,review("7",0)));
        service.objectiveGrade(9,51,501,0,"publish","8","Nhận xét GV","");
        assertEquals(n("8.00"),score(71,2));
        assertEquals(n("5.60"),score(71,1));
        assertEquals(n("5.60"),score(71,3));
        assertEquals(3,Jdbc.scalar(connection,"SELECT count(*) FROM assignment_group_results WHERE target_id=71"));
        assertEquals(1,Jdbc.scalar(connection,"SELECT count(*) FROM assignment_group_targets WHERE target_id=71 AND peer_closed_at IS NOT NULL"));
        assertThrows(IllegalArgumentException.class,()->service.savePeerReviews(1,71,Map.of(2L,review("10",1))));
    }

    @Test void objectiveGradingWaitsForTheNewestRetryToBeAccepted() throws Exception {
        pendingReplacement(501, 601);
        var grades = new GroupGradeDAO();
        assertFalse(grades.teacherDetail(connection, 501).gradingOpen());

        for (String action : List.of("save", "publish")) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> service.objectiveGrade(9,51,501,0,action,"8","Bản cũ",""));
            assertTrue(error.getMessage().contains("lượt làm mới"));
        }
        assertEquals(0,Jdbc.scalar(connection,"SELECT count(*) FROM assignment_group_results"));
        assertEquals(0,Jdbc.scalar(connection,"SELECT evaluation_revision FROM submissions WHERE submission_id=501"));

        var work = new StudentObjectiveService(new StudentObjectiveDAO(),new AssignmentDAO(),()->borrowed);
        assertTrue(work.finishOverdue() > 0);
        assertEquals(0,Jdbc.scalar(connection,"SELECT count(*) FROM submissions WHERE submission_id=501"));
        assertTrue(grades.teacherDetail(connection,601).gradingOpen());
        service.objectiveGrade(9,51,601,0,"publish","8","Bản cuối","");
        assertEquals(3,Jdbc.scalar(connection,"SELECT count(*) FROM assignment_group_results WHERE submission_id=601"));
    }

    @Test void essayGradingWaitsForTheNewestRetryToBeAccepted() throws Exception {
        pendingReplacement(500,600);
        var evaluations = new TeacherEvaluationService(new TeacherEvaluationDAO(),new TeacherSubmissionDAO(),new AssignmentDAO(),()->borrowed);
        assertTrue(evaluations.page(9,50,500).blockedReason().contains("lượt làm mới"));

        for (String action : List.of("saveEvaluation", "publishEvaluation")) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> evaluations.apply(9,50,500,action,0,
                            List.of(new SubmissionEvaluation.Mark(1,1,n("8"),"Đạt")),"Bản cũ"));
            assertTrue(error.getMessage().contains("lượt làm mới"));
        }
        assertEquals(0,Jdbc.scalar(connection,"SELECT count(*) FROM assignment_group_results"));
        assertEquals(0,Jdbc.scalar(connection,"SELECT evaluation_revision FROM submissions WHERE submission_id=500"));

        var work = new StudentObjectiveService(new StudentObjectiveDAO(),new AssignmentDAO(),()->borrowed);
        assertTrue(work.finishOverdue() > 0);
        assertEquals(0,Jdbc.scalar(connection,"SELECT count(*) FROM submissions WHERE submission_id=500"));
        assertNull(evaluations.page(9,50,600).blockedReason());
        evaluations.apply(9,50,600,"publishEvaluation",0,
                List.of(new SubmissionEvaluation.Mark(1,1,n("8"),"Đạt")),"Bản cuối");
        assertEquals(3,Jdbc.scalar(connection,"SELECT count(*) FROM assignment_group_results WHERE submission_id=600"));
    }

    @Test void objectiveReleasedAmendmentsRequireReasonAndRetainPeerFreezeAndJournal() throws Exception {
        service.savePeerReviews(1,71,Map.of(2L,review("9",0)));
        service.objectiveGrade(9,51,501,0,"publish","8","Bản đầu","");
        assertEquals(n("8.30"),score(71,2));
        assertThrows(IllegalArgumentException.class,()->service.objectiveGrade(9,51,501,1,"revise","10","Mới",""));
        assertEquals(n("8.30"),score(71,2));
        service.objectiveGrade(9,51,501,1,"revise","10","Mới","Chấm lại");
        assertEquals(n("9.70"),score(71,2));
        service.objectiveGrade(9,51,501,2,"restore","0","","Khôi phục" );
        assertEquals(n("8.30"),score(71,2));
        assertEquals(3,Jdbc.scalar(connection,"SELECT count(*) FROM submission_evaluation_history WHERE submission_id=501"));
        assertThrows(NoSuchElementException.class,()->service.objectiveGrade(99,51,501,3,"revise","10","","Ngoài lớp"));
    }

    @Test void essayPublicationAndAuditedAmendmentUpdateIndividualResultsAtomically() throws Exception {
        service.savePeerReviews(1,70,Map.of(2L,review("9",0)));
        var evaluations=new TeacherEvaluationService(new TeacherEvaluationDAO(),new TeacherSubmissionDAO(),new AssignmentDAO(),()->borrowed);
        evaluations.apply(9,50,500,"publishEvaluation",0,List.of(new SubmissionEvaluation.Mark(1,1,n("8"),"Đạt")),"Bản đầu");
        assertEquals(n("8.30"),score(70,2));
        assertEquals(n("5.60"),score(70,1));
        evaluations.apply(9,50,500,"reviseReleasedEvaluation",2,List.of(new SubmissionEvaluation.Mark(1,1,n("10"),"Đủ")),"Mới","Bổ sung đánh giá",false,0);
        assertEquals(n("9.70"),score(70,2));
        assertEquals(2,Jdbc.scalar(connection,"SELECT count(*) FROM submission_evaluation_history WHERE submission_id=500"));
    }

    @Test void teacherRosterHasOneRowPerTargetIncludingGroupsWithoutSubmission() throws Exception {
        var dao=new TeacherSubmissionDAO();var all=TeacherSubmissionFilter.from(null,null);
        assertEquals(2,dao.countForTeacher(connection,9,50,all));
        var rows=dao.findForTeacher(connection,9,50,all);
        assertEquals(2,rows.size());
        assertTrue(rows.stream().anyMatch(row->row.studentName().equals("Nhóm chưa làm")&&!row.hasSubmission()));
        assertEquals(1,dao.countForTeacher(connection,9,50,TeacherSubmissionFilter.from("Nhóm gốc",null)));
        assertEquals(1,dao.countForTeacher(connection,9,50,TeacherSubmissionFilter.from("Hai",null)));
        assertEquals(0,dao.countForTeacher(connection,99,50,all));
        assertEquals(1L,dao.studentCounts(connection,9,50).get("NOT_STARTED"));
    }

    @Test void missingAcceptedWorkAndCancelledAssignmentCannotReceivePeerReviews() throws Exception {
        assertFalse(service.peerPage(4,72L,1,1).selected().editable());
        assertThrows(IllegalArgumentException.class,()->service.savePeerReviews(4,72,Map.of(1L,review("8",0))));
        sql("UPDATE assignments SET status='CANCELLED',cancelled_at=now() WHERE assignment_id=50");
        assertThrows(NoSuchElementException.class,()->service.savePeerReviews(1,70,Map.of(2L,review("8",0))));
        assertThrows(NoSuchElementException.class,()->service.peerPage(1,70L,1,1));
    }
}
