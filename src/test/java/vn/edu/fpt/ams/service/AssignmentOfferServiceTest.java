package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import vn.edu.fpt.ams.model.AssignmentOfferStatus;
import vn.edu.fpt.ams.util.Jdbc;

class AssignmentOfferServiceTest {
    private Jdbc.Connections db;
    private AssignmentOfferService service;

    /** Build a fresh database per test; no local application rows are touched. */
    @BeforeEach void setup() throws Exception {
        String url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        db=()->DriverManager.getConnection(url);
        try(var c=db.open()) { AssignmentOfferFixture.seed(c,false); }
        service=new AssignmentOfferService(db);
    }

    /** Recipient-only listing expires old episodes and retains terminal history. */
    @Test void listIsOwnedAndExpiresPendingOffers() throws Exception {
        assertEquals(1,service.list(1).size());
        assertTrue(service.list(2).isEmpty());
        sql("UPDATE additional_class_assignment_offers SET sent_at=?,expires_at=?",Instant.now().minusSeconds(172900),Instant.now().minusSeconds(100));
        assertEquals(AssignmentOfferStatus.EXPIRED,service.list(1).get(0).status());
        assertEquals("APPROVED",text("SELECT status FROM additional_class_requests"));
    }

    /** Acceptance finalizes a separate class, experts and request without moving overflow. */
    @Test void acceptanceCreatesAndResolvesExactlyOneClass() throws Exception {
        assertEquals(AssignmentOfferService.Response.ACCEPTED,service.respond(1,20,AssignmentOfferStatus.ACCEPTED," I agree "));
        assertEquals("ACCEPTED",text("SELECT status FROM additional_class_assignment_offers"));
        assertEquals("I agree",text("SELECT response_note FROM additional_class_assignment_offers"));
        long id=count("SELECT created_class_id FROM additional_class_requests");
        assertNotEquals(100,id);
        assertEquals(1,count("SELECT teacher_id FROM subject_classes WHERE class_id="+id));
        assertEquals("ACTIVE",text("SELECT status FROM subject_classes WHERE class_id="+id));
        assertEquals(30,count("SELECT capacity FROM subject_classes WHERE class_id="+id));
        assertEquals(2,count("SELECT COUNT(*) FROM class_experts WHERE class_id="+id));
        assertEquals(2,count("SELECT teacher_id FROM subject_classes WHERE class_id=100"));
        assertEquals("RESOLVED",text("SELECT status FROM additional_class_requests"));
        assertTrue(text("SELECT overflow_students FROM overflow_batches").contains("waiting@example.test"));
        assertThrows(IllegalArgumentException.class,()->service.respond(1,20,AssignmentOfferStatus.ACCEPTED,null));
        assertEquals(2,count("SELECT COUNT(*) FROM subject_classes"));
    }

    /** A valid linked setup class is activated and reused rather than duplicated. */
    @Test void acceptanceReusesLinkedUnstaffedClass() throws Exception {
        setupClass();
        service.respond(1,20,AssignmentOfferStatus.ACCEPTED,null);
        assertEquals(200,count("SELECT created_class_id FROM additional_class_requests"));
        assertEquals(1,count("SELECT teacher_id FROM subject_classes WHERE class_id=200"));
        assertEquals("ACTIVE",text("SELECT status FROM subject_classes WHERE class_id=200"));
        assertEquals(2,count("SELECT COUNT(*) FROM subject_classes"));
    }

    /** Declining preserves the original approval and overflow for further Primary handling. */
    @Test void declineDoesNotFinalizeOrEraseApproval() throws Exception {
        String reviewed=text("SELECT CAST(reviewed_at AS VARCHAR) FROM additional_class_requests");
        service.respond(1,20,AssignmentOfferStatus.DECLINED,"Unavailable");
        assertEquals("DECLINED",text("SELECT status FROM additional_class_assignment_offers"));
        assertEquals("APPROVED",text("SELECT status FROM additional_class_requests"));
        assertEquals(reviewed,text("SELECT CAST(reviewed_at AS VARCHAR) FROM additional_class_requests"));
        assertEquals(1,count("SELECT COUNT(*) FROM subject_classes"));
        assertEquals(0,count("SELECT COUNT(*) FROM additional_class_requests WHERE resolved_at IS NOT NULL"));
    }

    /** Expiry is persisted even when a Teacher submits a stale acceptance form. */
    @Test void expiredResponseCommitsExpiryWithoutCreatingClass() throws Exception {
        sql("UPDATE additional_class_assignment_offers SET expires_at=?,sent_at=?",Instant.now().minusSeconds(1),Instant.now().minusSeconds(172801));
        assertEquals(AssignmentOfferService.Response.EXPIRED,service.respond(1,20,AssignmentOfferStatus.ACCEPTED,null));
        assertEquals("EXPIRED",text("SELECT status FROM additional_class_assignment_offers"));
        assertEquals(1,count("SELECT COUNT(*) FROM subject_classes"));
        assertEquals("APPROVED",text("SELECT status FROM additional_class_requests"));
    }

    /** Wrong actors and forged recipient identifiers cannot read or respond to this offer. */
    @Test void wrongRoleInactiveAndForeignRecipientAreDenied() throws Exception {
        assertThrows(SecurityException.class,()->service.list(5));
        assertThrows(NoSuchElementException.class,()->service.respond(2,20,AssignmentOfferStatus.ACCEPTED,null));
        sql("UPDATE users SET account_status='INACTIVE' WHERE user_id=1");
        assertThrows(SecurityException.class,()->service.respond(1,20,AssignmentOfferStatus.ACCEPTED,null));
        assertEquals("PENDING",text("SELECT status FROM additional_class_assignment_offers"));
    }

    /** Eligibility changes after listing are rechecked at acceptance. */
    @Test void revokedEligibilityBlocksAcceptanceButAllowsDecline() throws Exception {
        service.list(1);
        sql("UPDATE teacher_subjects SET is_active=FALSE WHERE teacher_id=1");
        assertThrows(IllegalArgumentException.class,()->service.respond(1,20,AssignmentOfferStatus.ACCEPTED,null));
        assertEquals("PENDING",text("SELECT status FROM additional_class_assignment_offers"));
        assertEquals(1,count("SELECT COUNT(*) FROM subject_classes"));
        assertEquals(AssignmentOfferService.Response.DECLINED,service.respond(1,20,AssignmentOfferStatus.DECLINED,null));
    }

    /** Invalid or missing expert coverage cannot be copied to the finalized class. */
    @Test void expertCoverageIsRevalidated() throws Exception {
        sql("UPDATE subject_experts SET is_active=FALSE WHERE expert_id=4");
        assertThrows(IllegalArgumentException.class,()->service.respond(1,20,AssignmentOfferStatus.ACCEPTED,null));
        assertEquals(1,count("SELECT COUNT(*) FROM subject_classes"));
    }

    /** Terminal and future episodes must never accept another response. */
    @Test void terminalAndFutureOffersAreRejected() throws Exception {
        for(String status:List.of("CANCELLED","EXPIRED")) {
            sql("UPDATE additional_class_assignment_offers SET status=?",status);
            assertThrows(IllegalArgumentException.class,()->service.respond(1,20,AssignmentOfferStatus.ACCEPTED,null));
        }
        sql("UPDATE additional_class_assignment_offers SET status='PENDING',sent_at=?,expires_at=?",Instant.now().plusSeconds(100),Instant.now().plusSeconds(172900));
        assertThrows(IllegalArgumentException.class,()->service.respond(1,20,AssignmentOfferStatus.ACCEPTED,null));
    }

    /** An already staffed or cross-offering setup must not be reassigned by acceptance. */
    @Test void existingStaffingAndWrongOfferingAreProtected() throws Exception {
        setupClass();
        sql("UPDATE subject_classes SET teacher_id=2 WHERE class_id=200");
        assertThrows(IllegalArgumentException.class,()->service.respond(1,20,AssignmentOfferStatus.ACCEPTED,null));
        sql("INSERT INTO academic_terms VALUES(21,'SPRING2027',TRUE)");
        sql("INSERT INTO subject_terms VALUES(31,10,21)");
        sql("UPDATE subject_classes SET teacher_id=NULL,subject_term_id=31 WHERE class_id=200");
        assertThrows(IllegalArgumentException.class,()->service.respond(1,20,AssignmentOfferStatus.ACCEPTED,null));
        assertEquals("PENDING",text("SELECT status FROM additional_class_assignment_offers"));
    }

    /** Failures after class creation roll back the offer, class and request together. */
    @Test void responseWriteFailureRollsBackAllFinalization() throws Exception {
        sql("ALTER TABLE additional_class_assignment_offers ADD CONSTRAINT reject_test_note CHECK(response_note IS NULL OR response_note<>'ROLLBACK_TEST')");
        assertThrows(IllegalStateException.class,()->service.respond(1,20,AssignmentOfferStatus.ACCEPTED,"ROLLBACK_TEST"));
        assertEquals("PENDING",text("SELECT status FROM additional_class_assignment_offers"));
        assertEquals("APPROVED",text("SELECT status FROM additional_class_requests"));
        assertEquals(1,count("SELECT COUNT(*) FROM subject_classes"));
    }

    /** Request locking lets only one recipient finalize, even with competing pending episodes. */
    @Test void concurrentAcceptancesCannotCreateTwoClasses() throws Exception {
        sql("INSERT INTO additional_class_assignment_offers(offer_id,request_id,teacher_id,offered_by,status,sent_at,expires_at) SELECT 21,request_id,2,offered_by,status,sent_at,expires_at FROM additional_class_assignment_offers WHERE offer_id=20");
        var pool=Executors.newFixedThreadPool(2);
        var start=new CountDownLatch(1);
        try {
            var first=pool.submit(()->compete(start,1,20));
            var second=pool.submit(()->compete(start,2,21));
            start.countDown();
            assertEquals(1,first.get(10,TimeUnit.SECONDS)+second.get(10,TimeUnit.SECONDS));
            assertEquals(2,count("SELECT COUNT(*) FROM subject_classes"));
            assertEquals(1,count("SELECT COUNT(*) FROM additional_class_assignment_offers WHERE status='ACCEPTED'"));
        } finally { pool.shutdownNow(); }
    }

    /** Only accept/decline decisions and notes within the form limit are permitted. */
    @Test void invalidDecisionsAndOversizedNotesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->service.respond(1,20,null,null));
        assertThrows(IllegalArgumentException.class,()->service.respond(1,20,AssignmentOfferStatus.EXPIRED,null));
        assertThrows(IllegalArgumentException.class,()->service.respond(1,20,AssignmentOfferStatus.ACCEPTED,"x".repeat(501)));
    }

    /** Reserved invitations count towards capacity, while expired reservations no longer count. */
    @Test void overfullSetupCannotBeActivated() throws Exception {
        setupClass();
        for(int i=1;i<=20;i++) sql("INSERT INTO class_enrollments VALUES(?,200,'ACTIVE')",i);
        sql("INSERT INTO student_invitations VALUES(1,200,'PENDING',?)",Instant.now().plusSeconds(3600));
        assertThrows(IllegalArgumentException.class,()->service.respond(1,20,AssignmentOfferStatus.ACCEPTED,null));
        assertEquals("PENDING",text("SELECT status FROM additional_class_assignment_offers"));
        sql("UPDATE student_invitations SET expires_at=?",Instant.now().minusSeconds(1));
        assertEquals(AssignmentOfferService.Response.ACCEPTED,service.respond(1,20,AssignmentOfferStatus.ACCEPTED,null));
    }

    /** Start competing transactions together and count successful consent only. */
    private int compete(CountDownLatch start,long teacher,long offer) throws Exception {
        start.await();
        try { service.respond(teacher,offer,AssignmentOfferStatus.ACCEPTED,null); return 1; }
        catch(IllegalArgumentException ex) { return 0; }
    }
    /** Create a linked inactive setup with one valid class expert. */
    private void setupClass() throws Exception {
        sql("INSERT INTO subject_classes(class_id,subject_term_id,class_code,class_name,capacity,registration_enabled,status) VALUES(200,30,'SETUP','Setup',20,TRUE,'INACTIVE')");
        sql("INSERT INTO class_experts(class_id,subject_id,expert_id) VALUES(200,10,3)");
        sql("UPDATE additional_class_requests SET created_class_id=200");
    }
    /** Execute fixture mutations in their own committed transaction. */
    private void sql(String sql,Object...args) throws Exception { try(var c=db.open()) { Jdbc.update(c,sql,args); } }
    /** Read a numeric invariant from a fresh connection. */
    private long count(String sql) throws Exception { try(var c=db.open()) { return Jdbc.scalar(c,sql); } }
    /** Read a state or preserved timestamp from a fresh connection. */
    private String text(String sql) throws Exception { try(var c=db.open();var p=c.prepareStatement(sql);var r=p.executeQuery()) { r.next();return r.getString(1); } }
}
