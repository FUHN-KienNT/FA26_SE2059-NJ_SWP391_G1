package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.dto.AssignmentCreatePage;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.AssignmentDraftValidator.ValidationException;
import vn.edu.fpt.ams.util.AssignmentEditConflictException;
import vn.edu.fpt.ams.util.AssignmentEditSupport;

/** State-machine and transaction checks that do not require PostgreSQL. */
class AssignmentRestoreServiceTest {
    private static final Instant NOW=Instant.parse("2030-01-01T12:00:00Z");

    private static class Fixture extends AssignmentDAO {
        Assignment assignment=assignment(AssignmentStatus.CANCELLED);
        List<String> events=new ArrayList<>();
        int commits,rollbacks;
        boolean active=true,failRestore;
        Connection connection=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},(proxy,method,args)->{
                    if(method.getName().equals("commit")) commits++;
                    if(method.getName().equals("rollback")) rollbacks++;
                    return null;
                });
        AssignmentService service=new AssignmentService(this,()->connection);
        String revision(){return AssignmentEditSupport.revision(assignment,List.of());}
        @Override public boolean lockActiveTeacher(Connection c,long teacherId){return active && teacherId==1;}
        @Override public Assignment lockOwnedAssignment(Connection c,long teacherId,long id){
            if(id!=101) throw new NoSuchElementException();
            return assignment;
        }
        @Override public AssignmentCreatePage findCreatePage(Connection c,long teacherId,long classId,boolean lock){
            return new AssignmentCreatePage(10,20,"Class","Owned class","SUB","Subject",List.of());
        }
        @Override public List<Question> findAllDraftQuestions(Connection c,long id,boolean lock){return List.of();}
        @Override public Instant currentTime(Connection c){return NOW;}
        @Override public void captureSubmissionContent(Connection c,long id){events.add("snapshot");}
        @Override public void markDraft(Connection c,long id) throws SQLException {
            events.add("draft");
            if(failRestore) throw new SQLException("Injected failure");
            assignment=assignment(AssignmentStatus.DRAFT);
        }
        @Override public void markPublished(Connection c,long id,Instant now){
            events.add("published"); assignment=assignment(AssignmentStatus.PUBLISHED);
        }
        @Override public void markCancelled(Connection c,long id,Instant now){events.add("cancelled");}
        @Override public int voidInProgressSubmissions(Connection c,long id){events.add("void-attempts");return 0;}
    }

    private static Assignment assignment(AssignmentStatus status) {
        return new Assignment(101,10,1,"Essay",AssignmentPurpose.REGULAR,ResponseType.ESSAY,
                "Instructions","Scope","[{\"task_order\":1,\"title\":\"Task\",\"description\":\"\",\"maximum_points\":10}]",
                BigDecimal.TEN,status,NOW.minusSeconds(3600),NOW.plusSeconds(86400),20,null,
                NOW.minusSeconds(7200),status==AssignmentStatus.CANCELLED?NOW.minusSeconds(60):null);
    }

    @Test void cancelledAssignmentIsTerminalAndCannotBePublishedOrEdited() {
        var f=new Fixture();
        assertThrows(AssignmentEditConflictException.class,()->f.service.publishAssignment(1,101,f.revision()));
        assertEquals(AssignmentStatus.CANCELLED,f.assignment.status());
        assertTrue(f.events.isEmpty());assertEquals(0,f.commits);
    }

    @Test void restoreIsRejectedWithoutOpeningADatabaseConnection() {
        var service=new AssignmentService(new AssignmentDAO(),()->{throw new AssertionError("Must not connect");});
        for(String revision:new String[]{null,"", "a".repeat(64)}) {
            assertThrows(IllegalArgumentException.class,()->service.restoreAssignment(1,101,revision));
        }
        assertThrows(IllegalArgumentException.class,()->service.lifecyclePage(1,101,"restore"));
    }

    @Test void rejectingRestoreNeverRewritesHistoricalSnapshotsOrAttemptState() {
        var f=new Fixture();
        var original=f.assignment;
        assertThrows(IllegalArgumentException.class,()->f.service.restoreAssignment(1,101,f.revision()));
        assertEquals(original,f.assignment);assertTrue(f.events.isEmpty());
        assertEquals(0,f.commits);assertEquals(0,f.rollbacks);
    }
}
