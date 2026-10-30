package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.sql.*;
import java.time.Instant;
import org.junit.jupiter.api.*;
import vn.edu.fpt.ams.model.AssignmentOfferStatus;
import vn.edu.fpt.ams.util.*;

/** PostgreSQL-only verification in session TEMP tables; never modifies application rows. */
class AssignmentOfferPostgresTest {
    private Connection connection;
    private AssignmentOfferService service;

    /** Shadow every touched table in pg_temp and lend the isolated session to the service. */
    @BeforeEach void setup() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
        connection=DBConnection.getConnection();
        try(var statement=connection.createStatement()) { statement.execute("SET search_path TO pg_temp"); }
        AssignmentOfferFixture.seed(connection,true);
        var borrowed=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},
                (proxy,method,args)->{
                    if(method.getName().equals("close")) return null;
                    try { return method.invoke(connection,args); }
                    catch(InvocationTargetException ex) { throw ex.getCause(); }
                });
        service=new AssignmentOfferService(()->borrowed);
    }

    /** Actual PostgreSQL accepts the joins, row locks and atomic class finalization SQL. */
    @Test void acceptsAndListsUsingRealPostgres() throws Exception {
        assertEquals(1,service.list(1).size());
        assertEquals(AssignmentOfferService.Response.ACCEPTED,service.respond(1,20,AssignmentOfferStatus.ACCEPTED,"Accepted"));
        assertEquals(2,Jdbc.scalar(connection,"SELECT COUNT(*) FROM subject_classes"));
        assertEquals(1,Jdbc.scalar(connection,"SELECT COUNT(*) FROM additional_class_requests WHERE status='RESOLVED' AND created_class_id IS NOT NULL"));
        assertEquals(AssignmentOfferStatus.ACCEPTED,service.list(1).get(0).status());
    }

    /** Deadline checks use wall time, even when the PostgreSQL transaction began before expiry. */
    @Test void expiredOfferCannotUseTransactionStartTime() throws Exception {
        var expires=Instant.now().plusSeconds(1);
        connection.setAutoCommit(false);
        Jdbc.scalar(connection,"SELECT 1");
        Jdbc.update(connection,"UPDATE additional_class_assignment_offers SET sent_at=?,expires_at=?",expires.minusSeconds(172800),expires);
        try(var statement=connection.createStatement()) { statement.execute("SELECT pg_sleep(1.1)"); }
        assertEquals(AssignmentOfferService.Response.EXPIRED,service.respond(1,20,AssignmentOfferStatus.ACCEPTED,null));
        assertEquals(1,Jdbc.scalar(connection,"SELECT COUNT(*) FROM additional_class_assignment_offers WHERE status='EXPIRED' AND responded_at IS NULL"));
        assertEquals(1,Jdbc.scalar(connection,"SELECT COUNT(*) FROM subject_classes"));
    }

    /** Existing setup reuse also checks reserved-seat SQL on PostgreSQL itself. */
    @Test void reusesSetupOnRealPostgres() throws Exception {
        Jdbc.update(connection,"INSERT INTO subject_classes(class_id,subject_term_id,class_code,class_name,capacity,status) VALUES(200,30,'SETUP','Setup',20,'INACTIVE')");
        Jdbc.update(connection,"INSERT INTO class_experts(class_id,subject_id,expert_id) VALUES(200,10,3)");
        Jdbc.update(connection,"UPDATE additional_class_requests SET created_class_id=200");
        assertEquals(AssignmentOfferService.Response.ACCEPTED,service.respond(1,20,AssignmentOfferStatus.ACCEPTED,null));
        assertEquals(200,Jdbc.scalar(connection,"SELECT created_class_id FROM additional_class_requests"));
        assertEquals(1,Jdbc.scalar(connection,"SELECT teacher_id FROM subject_classes WHERE class_id=200"));
        assertEquals(2,Jdbc.scalar(connection,"SELECT COUNT(*) FROM subject_classes"));
    }

    /** Close drops all TEMP data and local identity sequences. */
    @AfterEach void close() throws Exception { if(connection!=null) connection.close(); }
}
