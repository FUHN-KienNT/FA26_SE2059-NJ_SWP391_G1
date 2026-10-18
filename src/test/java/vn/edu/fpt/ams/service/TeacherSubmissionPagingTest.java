package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.dao.TeacherSubmissionDAO;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.SubmissionContentSnapshot;
import vn.edu.fpt.ams.util.TeacherSubmissionNavigation;

/** Real SQL paging/count tests against isolated in-memory tables; no local application database. */
public class TeacherSubmissionPagingTest {
    public static int strpos(String text, String search) { return text.indexOf(search) + 1; }
    private String url;
    private Assignment assignment;
    private boolean active;
    private TeacherSubmissionService service;

    @BeforeEach void setup() throws SQLException {
        url = "jdbc:h2:mem:submission_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        assignment = assignment(AssignmentStatus.PUBLISHED, Instant.EPOCH);
        active = true;
        try (Connection c = connection(); var s = c.createStatement()) {
            s.execute("CREATE ALIAS STRPOS FOR 'vn.edu.fpt.ams.service.TeacherSubmissionPagingTest.strpos'");
            s.execute("CREATE TABLE users (user_id BIGINT PRIMARY KEY, role VARCHAR, account_status VARCHAR, full_name VARCHAR, email VARCHAR)");
            s.execute("CREATE TABLE assignments (assignment_id BIGINT PRIMARY KEY, class_id BIGINT)");
            s.execute("CREATE TABLE subject_classes (class_id BIGINT PRIMARY KEY, teacher_id BIGINT, subject_term_id BIGINT)");
            s.execute("CREATE TABLE class_enrollments (enrollment_id BIGINT PRIMARY KEY, class_id BIGINT, registration_id BIGINT, status VARCHAR)");
            s.execute("CREATE TABLE subject_registrations (registration_id BIGINT PRIMARY KEY, subject_term_id BIGINT, student_id BIGINT, status VARCHAR)");
            s.execute("CREATE TABLE submissions (submission_id BIGINT PRIMARY KEY, assignment_id BIGINT, enrollment_id BIGINT, attempt_number INT, status VARCHAR, started_at TIMESTAMP WITH TIME ZONE, deadline_at TIMESTAMP WITH TIME ZONE, submitted_at TIMESTAMP WITH TIME ZONE, content VARCHAR, submission_link VARCHAR, answer VARCHAR, score NUMERIC(10,2), feedback VARCHAR, graded_by BIGINT, graded_at TIMESTAMP WITH TIME ZONE, released_at TIMESTAMP WITH TIME ZONE)");
            s.execute("CREATE TABLE submission_content_snapshots (submission_id BIGINT PRIMARY KEY, assignment_data VARCHAR, questions_data VARCHAR)");
            s.execute("INSERT INTO users VALUES (1,'TEACHER','ACTIVE','Teacher','teacher@test'),(2,'STUDENT','ACTIVE','Alice 100%_','alice@test'),(3,'STUDENT','ACTIVE','Bob','bob@test')");
            s.execute("INSERT INTO assignments VALUES (50,10),(60,20)");
            s.execute("INSERT INTO subject_classes VALUES (10,1,100),(20,999,100)");
            s.execute("INSERT INTO subject_registrations VALUES (20,100,2,'ACTIVE'),(30,100,3,'ACTIVE')");
            s.execute("INSERT INTO class_enrollments VALUES (20,10,20,'ACTIVE'),(30,10,30,'ACTIVE'),(40,20,20,'ACTIVE')");
        }
        AssignmentDAO owned = new AssignmentDAO() {
            @Override public boolean isActiveTeacher(Connection c, long id) { return active && id == 1; }
            @Override public Optional<AssignmentDetailPage> findDetailForTeacher(Connection c, long teacher, long id) {
                return teacher == 1 && id == 50 ? Optional.of(context()) : Optional.empty();
            }
        };
        service = new TeacherSubmissionService(new TeacherSubmissionDAO(), owned, this::connection);
    }

    private Connection connection() throws SQLException { return DriverManager.getConnection(url); }
    private Assignment assignment(AssignmentStatus status, Instant published) {
        return new Assignment(50,10,1,"Current title",AssignmentPurpose.REGULAR,ResponseType.ESSAY,
                "New instructions","New scope","[]",BigDecimal.valueOf(100),status,
                Instant.EPOCH,Instant.EPOCH.plusSeconds(3600),20,null,published,null);
    }
    private AssignmentDetailPage context() {
        return new AssignmentDetailPage(assignment,"C1","Lớp","S1","Subject","T1","Template",List.of(),true);
    }
    private void attempt(long id, long enrollment, String status, boolean submitted) throws SQLException {
        try (Connection c = connection(); var s = c.prepareStatement("INSERT INTO submissions (submission_id,assignment_id,enrollment_id,attempt_number,status,submitted_at,content,answer) VALUES (?,50,?,?,?,?,'Student answer','[]')")) {
            s.setLong(1,id); s.setLong(2,enrollment); s.setInt(3,(int)id); s.setString(4,status);
            s.setTimestamp(5,submitted ? java.sql.Timestamp.from(Instant.parse("2026-10-15T00:00:00Z")) : null);
            s.executeUpdate();
        }
    }
    private TeacherSubmissionListPage page(String search, String status, String number) {
        return service.listPage(1,50,TeacherSubmissionFilter.from(search,status,number));
    }

    @Test void mixedRosterAndAttemptsUseTwelveRowPagesWithoutDuplicatesOrSkippedRows() throws Exception {
        var unstarted = page(null,null,null);
        assertEquals(2,unstarted.totalCount()); assertEquals(1,unstarted.totalPages());
        assertEquals(List.of(2L,3L),unstarted.submissions().stream().map(TeacherSubmissionRow::studentId).toList());
        assertTrue(unstarted.submissions().stream().allMatch(row -> !row.hasSubmission() && row.submission() == null));
        assertFalse(unstarted.hasNext());
        for (int i=1;i<=11;i++) attempt(i,20,"SUBMITTED",true);
        var twelve = page(null,null,null);
        assertEquals(12,twelve.submissions().size()); assertEquals(12,twelve.totalCount());
        assertEquals(1,twelve.totalPages()); assertFalse(twelve.hasNext());
        attempt(12,20,"SUBMITTED",true);
        var first = page(null,null,"1"); var second = page(null,null,"2");
        assertEquals(12,first.submissions().size()); assertEquals(13,first.totalCount());
        assertEquals(12,first.submissions().get(0).submission().submissionId());
        assertEquals(1,first.submissions().get(11).submission().submissionId());
        assertEquals(12,first.submissions().stream().map(row -> row.submission().submissionId()).distinct().count());
        assertTrue(first.hasNext()); assertFalse(first.hasPrevious());
        assertEquals(1,second.submissions().size()); assertEquals(3,second.submissions().get(0).studentId());
        assertNull(second.submissions().get(0).submission());
        assertEquals(13,second.firstRow()); assertEquals(13,second.lastRow());
        assertTrue(second.hasPrevious()); assertFalse(second.hasNext());
        assertEquals(2,page(null,null,"999").filter().page());
    }

    @Test void unstartedRosterIncludesInactiveAccountsAndExcludesNoncurrentOrForeignStudents() throws Exception {
        try (Connection c=connection(); var s=c.createStatement()) {
            s.execute("""
                    INSERT INTO users VALUES
                      (4,'STUDENT','INACTIVE','Current inactive account','inactive@test'),
                      (5,'STUDENT','ACTIVE','Inactive enrollment','enrollment@test'),
                      (6,'STUDENT','ACTIVE','Inactive registration','registration@test'),
                      (7,'STUDENT','ACTIVE','Wrong term','term@test'),
                      (8,'STUDENT','ACTIVE','Foreign class','foreign@test'),
                      (9,'EXPERT','ACTIVE','Wrong role','expert@test')
                    """);
            s.execute("""
                    INSERT INTO subject_registrations VALUES
                      (40,100,4,'ACTIVE'),(50,100,5,'ACTIVE'),(60,100,6,'INACTIVE'),
                      (70,200,7,'ACTIVE'),(80,100,8,'ACTIVE'),(90,100,9,'ACTIVE')
                    """);
            s.execute("""
                    INSERT INTO class_enrollments VALUES
                      (50,10,40,'ACTIVE'),(60,10,50,'INACTIVE'),(70,10,60,'ACTIVE'),
                      (80,10,70,'ACTIVE'),(90,20,80,'ACTIVE'),(100,10,90,'ACTIVE')
                    """);
        }
        var roster = page(null,"NOT_STARTED",null);
        assertEquals(3,roster.totalCount());
        assertEquals(List.of(2L,3L,4L),roster.submissions().stream().map(TeacherSubmissionRow::studentId).toList());
        assertTrue(roster.submissions().stream().noneMatch(TeacherSubmissionRow::hasSubmission));
        assertThrows(NoSuchElementException.class,()->service.detailPage(1,50,4));
        assertEquals(3,page(null,null,null).totalCount());
        assertEquals(0,page(null,"SUBMITTED",null).totalCount());
    }

    @Test void rosterWithThirteenUnstartedStudentsPagesByNameThenStudentId() throws Exception {
        try (Connection c=connection(); var s=c.createStatement()) {
            for (int id=4;id<=14;id++) {
                s.execute("INSERT INTO users VALUES ("+id+",'STUDENT','ACTIVE','Same name','student"+id+"@test')");
                s.execute("INSERT INTO subject_registrations VALUES ("+(id*10)+",100,"+id+",'ACTIVE')");
                s.execute("INSERT INTO class_enrollments VALUES ("+(id*10+1)+",10,"+(id*10)+",'ACTIVE')");
            }
        }
        var first=page(null,"NOT_STARTED","1"); var second=page(null,"NOT_STARTED","2");
        assertEquals(13,first.totalCount()); assertEquals(2,first.totalPages());
        assertEquals(List.of(2L,3L,4L,5L,6L,7L,8L,9L,10L,11L,12L,13L),
                first.submissions().stream().map(TeacherSubmissionRow::studentId).toList());
        assertEquals(List.of(14L),second.submissions().stream().map(TeacherSubmissionRow::studentId).toList());
        assertTrue(second.filter().notStartedOnly()); assertEquals(13,second.firstRow());
        assertTrue(first.submissions().stream().noneMatch(TeacherSubmissionRow::hasSubmission));
        assertNull(second.submissions().get(0).submission());
    }

    @Test void inactiveHistoricalAttemptsRemainVisibleAfterReenrollmentWithoutFalseUnstartedRow() throws Exception {
        attempt(1,20,"SUBMITTED",true);
        attempt(2,20,"EXPIRED",false);
        try (Connection c=connection(); var s=c.createStatement()) {
            s.execute("UPDATE class_enrollments SET status='INACTIVE' WHERE enrollment_id=20");
            s.execute("UPDATE subject_registrations SET status='INACTIVE' WHERE registration_id=20");
            s.execute("INSERT INTO subject_registrations VALUES (21,100,2,'ACTIVE')");
            s.execute("INSERT INTO class_enrollments VALUES (21,10,21,'ACTIVE')");
        }
        var all = page(null,null,null);
        assertEquals(3,all.totalCount());
        assertEquals(List.of(2L,1L),all.submissions().stream().filter(TeacherSubmissionRow::hasSubmission)
                .map(row -> row.submission().submissionId()).toList());
        assertEquals(List.of(3L),page(null,"NOT_STARTED",null).submissions().stream()
                .map(TeacherSubmissionRow::studentId).toList());
        assertEquals(1,service.detailPage(1,50,1).row().submission().submissionId());
    }

    @Test void notStartedFilterUsesLiteralNameAndEmailSearchAndCreatesNoSubmissionRecords() throws Exception {
        var literal = page("  100%_  "," not_started ","99");
        assertEquals(1,literal.totalCount()); assertEquals(1,literal.filter().page());
        assertTrue(literal.filter().notStartedOnly()); assertEquals("NOT_STARTED",literal.filter().statusValue());
        assertEquals(2,literal.submissions().get(0).studentId());
        assertNull(literal.submissions().get(0).submission());
        assertEquals(1,page("%","NOT_STARTED",null).totalCount());
        assertEquals(1,page("_","NOT_STARTED",null).totalCount());
        assertEquals(1,page("BOB@TEST","NOT_STARTED",null).totalCount());
        assertEquals(0,page("' OR 1=1 --","NOT_STARTED",null).totalCount());
        assertThrows(NoSuchElementException.class,()->service.detailPage(1,50,2));
        try (Connection c=connection(); var s=c.createStatement(); var rows=s.executeQuery("SELECT count(*) FROM submissions")) {
            assertTrue(rows.next()); assertEquals(0,rows.getLong(1));
        }
        attempt(1,20,"IN_PROGRESS",false);
        assertEquals(0,page("Alice","NOT_STARTED",null).totalCount());
        assertEquals(1,page("Alice","IN_PROGRESS",null).totalCount());
    }

    @Test void countAndPageApplySameLiteralSearchStatusAndEnrollmentAuthorization() throws Exception {
        for (int i=1;i<=13;i++) attempt(i,20,"SUBMITTED",true);
        attempt(14,30,"GRADED",true); attempt(15,40,"SUBMITTED",true);
        var filtered = page("100%_","submitted","2");
        assertEquals(13,filtered.totalCount()); assertEquals(1,filtered.submissions().size());
        assertEquals(1,filtered.submissions().get(0).submission().submissionId());
        assertEquals(1,page("BOB","GRADED",null).totalCount());
        assertEquals(0,page("Alice","GRADED",null).totalCount());
        var absent = page("' OR 1=1 --",null,"2");
        assertEquals(0,absent.totalCount()); assertEquals(1,absent.filter().page());
        assertThrows(NoSuchElementException.class,()->service.detailPage(1,50,15));
    }

    @Test void draftAndCancelledRequirePublicationAndActuallySubmittedHistoryForListAndDetail() throws Exception {
        attempt(1,20,"IN_PROGRESS",false);
        assignment = assignment(AssignmentStatus.DRAFT,null);
        assertThrows(NoSuchElementException.class,()->page(null,null,null));
        assertThrows(NoSuchElementException.class,()->service.detailPage(1,50,1));
        assignment = assignment(AssignmentStatus.CANCELLED,Instant.EPOCH);
        attempt(2,20,"VOID",true); // A void row cannot unlock access even with a stale timestamp.
        attempt(3,40,"SUBMITTED",true); // Nor can a row from the wrong class.
        assertThrows(NoSuchElementException.class,()->page(null,null,null));
        assertThrows(NoSuchElementException.class,()->service.detailPage(1,50,2));
        attempt(4,20,"SUBMITTED",true);
        assertEquals(4,page(null,null,null).totalCount());
        assertEquals(4,service.detailPage(1,50,4).row().submission().submissionId());
        assignment = assignment(AssignmentStatus.DRAFT,Instant.EPOCH);
        assertEquals(4,page(null,null,null).totalCount());
        assignment = assignment(AssignmentStatus.DRAFT,null);
        assertThrows(NoSuchElementException.class,()->page(null,null,null));
    }

    @Test void currentActorAndOwnershipChecksStillProtectBothRoutes() throws Exception {
        attempt(1,20,"SUBMITTED",true);
        assertThrows(NoSuchElementException.class,()->service.listPage(1,60,TeacherSubmissionFilter.from(null,null)));
        assertThrows(NoSuchElementException.class,()->service.detailPage(1,60,1));
        active=false;
        assertThrows(SecurityException.class,()->page(null,null,null));
        assertThrows(SecurityException.class,()->service.detailPage(1,50,1));
    }

    @Test void historicalSnapshotKeepsQuestionContentAndMaximumAfterReopenAndEdit() throws Exception {
        attempt(1,20,"SUBMITTED",true);
        String saved = """
                {"assignment_id":50,"class_id":10,"template_id":1,"title":"Original quiz",
                 "assignment_purpose":"REGULAR","response_type":"OBJECTIVE","instructions":"Original instructions",
                 "scope_text":"","task_checklist":[],"maximum_score":10,"status":"CANCELLED",
                 "available_from":"2026-10-14T08:00:00+07:00","due_at":"2026-10-14T09:00:00+07:00",
                 "time_limit_minutes":20,"attempt_limit":null,"published_at":"2026-10-14T00:00:00+00:00","cancelled_at":null}
                """;
        String questions = """
                [{"question_id":80,"assignment_id":50,"question_order":1,"question_text":"Original question",
                  "maximum_points":10,"options":[{"option_id":"a","option_text":"Original choice","is_correct":true}]}]
                """;
        try(Connection c=connection(); var s=c.prepareStatement("INSERT INTO submission_content_snapshots VALUES (1,?,?)")) {
            s.setString(1,saved); s.setString(2,questions); s.executeUpdate();
        }
        assignment = assignment(AssignmentStatus.DRAFT,Instant.EPOCH);
        var detail=service.detailPage(1,50,1);
        assertEquals("Original quiz",detail.assignment().assignment().title());
        assertEquals(BigDecimal.TEN,detail.assignment().assignment().maximumScore());
        assertEquals(ResponseType.OBJECTIVE,detail.assignment().assignment().responseType());
        assertEquals("Original question",detail.assignment().questions().get(0).questionText());
        assertEquals(Instant.parse("2026-10-14T01:00:00Z"),detail.assignment().assignment().availableFrom());
        var list=page(null,null,null);
        assertEquals(BigDecimal.TEN,SubmissionContentSnapshot.maximumScore(list.submissions().get(0),assignment.maximumScore()));
    }

    @Test void paginationInputAndLinksPreserveFiltersWhileRejectingInvalidNumbers() {
        for (String value : List.of("0","-1","1.5","abc","2+2","2147483648"))
            assertThrows(IllegalArgumentException.class,()->TeacherSubmissionFilter.from(null,null,value));
        var filter=TeacherSubmissionFilter.from("Nguyễn & 100%_","GRADED","2");
        assertEquals(12,filter.offset());
        String list=TeacherSubmissionNavigation.listUrl("/ams",50,filter);
        assertTrue(list.contains("&page=2")); assertTrue(list.contains("&status=GRADED"));
        assertTrue(list.contains("%26")); assertTrue(list.contains("100%25_"));
        assertEquals(list+"&mode=view&submissionId=1",TeacherSubmissionNavigation.detailUrl("/ams",50,1,filter));
        var notStarted=TeacherSubmissionFilter.from("Alice","NOT_STARTED","3").withPage(2);
        assertTrue(notStarted.notStartedOnly()); assertNull(notStarted.status());
        assertEquals("NOT_STARTED",notStarted.statusValue()); assertEquals(12,notStarted.offset());
        assertTrue(TeacherSubmissionNavigation.listUrl("/ams",50,notStarted).contains("&status=NOT_STARTED"));
        assertEquals("",TeacherSubmissionFilter.from(null,"ALL").statusValue());
        assertFalse(new TeacherSubmissionFilter("",SubmissionStatus.SUBMITTED).notStartedOnly());
        assertEquals("SUBMITTED",new TeacherSubmissionFilter("",SubmissionStatus.SUBMITTED,2).statusValue());
    }
}
