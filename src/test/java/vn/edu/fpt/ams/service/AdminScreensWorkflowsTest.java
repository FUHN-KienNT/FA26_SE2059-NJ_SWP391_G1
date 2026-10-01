package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import vn.edu.fpt.ams.dto.UserCreateRequest;
import vn.edu.fpt.ams.integration.EmailService;
import vn.edu.fpt.ams.integration.GoogleIdentityClient.Identity;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

/** Real JDBC transactions against H2; PostgreSQL-specific triggers need deployment verification. */
class AdminScreensWorkflowsTest {
  Jdbc.Connections db;
  UserService users;
  GoogleAccountService google;
  String mailed;
  boolean failMail;

  @BeforeEach
  void setup() throws Exception {
    String url =
        "jdbc:h2:mem:"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    db = () -> DriverManager.getConnection(url);
    apply(Files.readString(Path.of("database/schema.sql")));
    // Application writes normalized emails; these restore uniqueness skipped with expression
    // indexes.
    sql("CREATE UNIQUE INDEX test_users_email ON users(email)");
    sql("CREATE UNIQUE INDEX test_google_subject ON users(google_subject)");
    apply(Files.readString(Path.of("database/migrations/001-namdh-iter2-audit.sql")));
    String history =
        Files.readString(Path.of("database/migrations/002-responsibility-evidence.sql"));
    apply(history.substring(0, history.indexOf("CREATE OR REPLACE FUNCTION")));
    initializeServicesAndSeed();
  }

  @Test
  void internalLoginUsesDatabaseRoleAndUpdatesLastLoginOnlyOnSuccess() {
    var auth = new AuthenticationService(db);
    String hash = PasswordHash.hash("Local-Test-Password");
    for (long id : new long[] {1, 2, 4}) {
      sql("UPDATE users SET password_hash=? WHERE user_id=?", hash, id);
      var expected = users.detail(1, id);
      assertThrows(SecurityException.class, () -> auth.signIn(expected.email(), "wrong"));
      assertNull(users.detail(1, id).lastLoginAt());
      var signedIn = auth.signIn(" " + expected.email().toUpperCase() + " ", "Local-Test-Password");
      assertEquals(expected.role(), signedIn.role());
      assertNotNull(signedIn.lastLoginAt());
    }
  }

  @Test
  void internalLoginRejectsInactiveStudentMissingAndPlaceholderAccounts() {
    var auth = new AuthenticationService(db);
    sql(
        "UPDATE users SET password_hash=?,account_status='INACTIVE' WHERE user_id=5",
        PasswordHash.hash("Password"));
    assertThrows(
        SecurityException.class, () -> auth.signIn(users.detail(1, 5).email(), "Password"));
    var student =
        google.signIn(new Identity("student-sub", "student@test.edu", "Student Name"), null);
    assertThrows(SecurityException.class, () -> auth.signIn(student.email(), "Password"));
    assertThrows(SecurityException.class, () -> auth.signIn("missing@test.edu", "Password"));
    assertThrows(
        SecurityException.class, () -> auth.signIn(users.detail(1, 1).email(), "fixture-hash"));
    assertThrows(SecurityException.class, () -> auth.signIn(null, "Password"));
  }

  void initializeServicesAndSeed() {
    users =
        new UserService(
            db,
            new EmailService() {
              @Override
              public void sendNotification(String to, String subject, String text) {
                // Notification must observe the committed account from an independent connection.
                assertEquals(1, scalar("SELECT COUNT(*) FROM users WHERE email=?", to));
                if (failMail) throw new IllegalStateException("SMTP unavailable");
                mailed = text;
              }
            });
    google = new GoogleAccountService(db);
    for (String role : List.of("ADMIN", "EXPERT", "EXPERT", "TEACHER", "EXPERT"))
      sql(
          "INSERT INTO users(full_name,email,role,password_hash) VALUES('Staff Member',?,?,?)",
          role.toLowerCase() + UUID.randomUUID() + "@test.edu",
          role,
          "fixture-hash");
    sql(
        "INSERT INTO subjects(subject_code,subject_name) VALUES('SWP391','Software"
            + " Project'),('PRJ301','Java Web')");
    sql(
        "INSERT INTO academic_terms(term_code,term_name,starts_at,ends_at,is_current)"
            + " VALUES('FA26','Fall',?,?,TRUE)",
        Instant.now().minusSeconds(86400),
        Instant.now().plusSeconds(8640000));
    sql(
        "INSERT INTO subject_terms(subject_id,term_id,registration_opens_at,registration_closes_at)"
            + " VALUES(1,1,?,?)",
        Instant.now().minusSeconds(86400),
        Instant.now().plusSeconds(8640000));
    sql(
        "INSERT INTO subject_experts(subject_id,expert_id,responsibility_type)"
            + " VALUES(1,2,'PRIMARY'),(1,3,'SUPPORTING')");
    sql("INSERT INTO teacher_subjects(teacher_id,subject_id) VALUES(4,1)");
    sql(
        "INSERT INTO"
            + " subject_classes(subject_term_id,teacher_id,class_code,class_name,capacity,registration_enabled)"
            + " VALUES(1,4,'G1','Group One',10,TRUE)");
  }

  private void apply(String script) throws Exception {
    script =
        script
            .replaceAll("(?m)^--.*$", "")
            .replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE")
            .replace("btrim(", "trim(")
            .replace("'[]'::jsonb", "'[]'")
            .replace("JSONB", "JSON")
            .replace("INTERVAL '48 hours'", "INTERVAL '48' HOUR")
            .replaceAll("CHECK \\(jsonb_typeof\\([^)]*\\) = 'array'\\)", "");
    try (var c = db.open();
        var s = c.createStatement()) {
      for (String command : script.split(";")) {
        command = command.strip();
        if (command.isEmpty()) continue;
        // H2 has no PostgreSQL partial or expression index support.
        if (command.startsWith("CREATE")
            && command.contains("INDEX")
            && (command.contains(" WHERE ") || command.contains("lower("))) continue;
        s.execute(command);
      }
    }
  }

  void sql(String q, Object... args) {
    Jdbc.transaction(
        db,
        c -> {
          Jdbc.update(c, q, args);
          return null;
        });
  }

  long scalar(String q, Object... args) {
    return Jdbc.transaction(db, c -> Jdbc.scalar(c, q, args));
  }



  void invite(String token, String email) {
    sql(
        "INSERT INTO student_invitations(class_id,invited_by,email,token,expires_at)"
            + " VALUES(1,4,?,?,CURRENT_TIMESTAMP+INTERVAL '48' HOUR)",
        email,
        token);
  }

  @Test
  void staffCreateCommitsBeforeEmailAndStoresOnlyHash() {
    var created =
        users.create(
            1, new UserCreateRequest("Nguyen Nam", " NAM@test.edu ", UserRole.TEACHER), Set.of(1L));
    assertTrue(created.delivered());
    var u = users.detail(1, created.userId());
    assertEquals("nam@test.edu", u.email());
    String password = mailed.split("Initial password: ")[1].split("\n")[0];
    assertNotEquals(password, u.passwordHash());
    assertTrue(PasswordHash.verify(password, u.passwordHash()));
    assertEquals(Set.of(1L), users.eligibility(1, u.userId()));
    assertEquals(
        1, scalar("SELECT COUNT(*) FROM account_audit_events WHERE user_id=?", u.userId()));
  }

  @Test
  void failedEmailKeepsCommittedStaffAndCanBeReissued() {
    failMail = true;
    var result =
        users.create(
            1, new UserCreateRequest("Nguyen Nam", "nam@test.edu", UserRole.EXPERT), Set.of());
    assertFalse(result.delivered());
    String hash = users.detail(1, result.userId()).passwordHash();
    failMail = false;
    assertTrue(users.resendCredential(1, result.userId()).delivered());
    assertNotEquals(hash, users.detail(1, result.userId()).passwordHash());
  }

  @Test
  void invalidEligibilityRollsBackAccountAndDoesNotMail() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            users.create(
                1,
                new UserCreateRequest("Nguyen Nam", "nam@test.edu", UserRole.TEACHER),
                Set.of(999L)));
    assertEquals(5, scalar("SELECT COUNT(*) FROM users"));
    assertNull(mailed);
    assertEquals(0, scalar("SELECT COUNT(*) FROM account_audit_events"));
  }

  @Test
  void duplicateEmailDoesNotChangeExistingAccount() {
    users.create(1, new UserCreateRequest("Nguyen Nam", "nam@test.edu", UserRole.EXPERT), Set.of());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            users.create(
                1,
                new UserCreateRequest("Other Name", " NAM@test.edu ", UserRole.TEACHER),
                Set.of(1L)));
    assertEquals(6, scalar("SELECT COUNT(*) FROM users"));
  }

  @Test
  void nonAdminsAndManualStudentCreationAreRejected() {
    assertThrows(SecurityException.class, () -> users.list(2, null, null, null, 1));
    assertThrows(
        SecurityException.class,
        () ->
            users.create(
                4,
                new UserCreateRequest("Other Name", "other@test.edu", UserRole.EXPERT),
                Set.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            users.create(
                1,
                new UserCreateRequest("Student Name", "s@test.edu", UserRole.STUDENT),
                Set.of()));
  }

  @Test
  void selfAndSolePrimaryAndActiveTeacherCannotBeDeactivated() {
    assertThrows(IllegalArgumentException.class, () -> users.status(1, 1, AccountStatus.INACTIVE));
    assertThrows(IllegalArgumentException.class, () -> users.status(1, 2, AccountStatus.INACTIVE));
    assertThrows(IllegalArgumentException.class, () -> users.status(1, 4, AccountStatus.INACTIVE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            users.update(
                1, 4, "Teacher Name", users.detail(1, 4).email(), UserRole.TEACHER, Set.of()));
    assertEquals(AccountStatus.ACTIVE, users.detail(1, 2).accountStatus());
  }



  @Test
  void soleClassExpertCannotBeDeactivated() {
    sql("INSERT INTO class_experts(class_id,subject_id,expert_id) VALUES(1,1,3)");
    assertThrows(IllegalArgumentException.class, () -> users.status(1, 3, AccountStatus.INACTIVE));
    assertEquals(
        1, scalar("SELECT COUNT(*) FROM class_experts WHERE expert_id=3 AND is_active=TRUE"));
  }









  @Test
  void googleProvisioningIsIdempotentAndNeverLinksStaffEmail() {
    var identity = new Identity("google-subject", "student@test.edu", "Student Name");
    var first = google.signIn(identity, null);
    var again = google.signIn(identity, null);
    assertEquals(first.userId(), again.userId());
    assertEquals(UserRole.STUDENT, first.role());
    assertNull(first.passwordHash());
    assertNotNull(again.lastLoginAt());
    assertThrows(
        SecurityException.class,
        () ->
            google.signIn(new Identity("another", users.detail(1, 1).email(), "Admin Name"), null));
    users.status(1, first.userId(), AccountStatus.INACTIVE);
    assertThrows(SecurityException.class, () -> google.signIn(identity, null));
  }

  @Test
  void invitedGoogleAcceptanceCommitsTogetherAndReplayDoesNotDuplicate() {
    invite("token", "student@test.edu");
    var identity = new Identity("subject", "student@test.edu", "Student Name");
    var first = google.signIn(identity, "token");
    assertEquals(first.userId(), google.signIn(identity, "token").userId());
    assertEquals(1, scalar("SELECT COUNT(*) FROM subject_registrations"));
    assertEquals(1, scalar("SELECT COUNT(*) FROM class_enrollments"));
    assertEquals(
        1,
        scalar(
            "SELECT COUNT(*) FROM student_invitations WHERE status='ACCEPTED' AND accepted_by=?",
            first.userId()));
  }

  @Test
  void invalidInvitationEmailOrTokenRollsBackProvisioning() {
    invite("token", "invited@test.edu");
    assertThrows(
        SecurityException.class,
        () -> google.signIn(new Identity("s", "wrong@test.edu", "Wrong Name"), "token"));
    assertThrows(
        IllegalArgumentException.class,
        () -> google.signIn(new Identity("s", "invited@test.edu", "Student Name"), "missing"));
    assertEquals(5, scalar("SELECT COUNT(*) FROM users"));
    assertEquals(0, scalar("SELECT COUNT(*) FROM class_enrollments"));
  }

  @Test
  void expiredOrOverCapacityInvitationRollsBackEverything() {
    sql(
        "INSERT INTO student_invitations(class_id,invited_by,email,token,sent_at,expires_at)"
            + " VALUES(1,4,'old@test.edu','old',?,?)",
        Instant.now().minusSeconds(259200),
        Instant.now().minusSeconds(86400));
    assertThrows(
        IllegalArgumentException.class,
        () -> google.signIn(new Identity("old-sub", "old@test.edu", "Student Name"), "old"));
    for (int n = 0; n < 11; n++) invite("t" + n, "s" + n + "@test.edu");
    assertThrows(
        IllegalArgumentException.class,
        () -> google.signIn(new Identity("new-sub", "s0@test.edu", "Student Name"), "t0"));
    assertEquals(5, scalar("SELECT COUNT(*) FROM users"));
    assertEquals(0, scalar("SELECT COUNT(*) FROM subject_registrations"));
  }



  @Test
  void deactivationCancelsLiveOffersButPreservesExpiredOfferReviewEvidence() {
    sql("UPDATE subject_classes SET status='INACTIVE' WHERE class_id=1");
    sql(
        "INSERT INTO overflow_batches(source_class_id,imported_by,source_file_name)"
            + " VALUES(1,4,'students.csv')");
    for (int i = 1; i <= 4; i++) {
      sql(
          "INSERT INTO"
              + " additional_class_requests(overflow_batch_id,requested_by,overflow_context,status,reviewed_by,reviewed_at,decision_reason)"
              + " VALUES(1,4,'Overflow','REJECTED',2,CURRENT_TIMESTAMP,'Rejected')");
      Instant sent = i < 4 ? Instant.now().minus(Duration.ofDays(3)) : Instant.now();
      sql(
          "INSERT INTO"
              + " additional_class_assignment_offers(request_id,teacher_id,offered_by,sent_at,expires_at)"
              + " VALUES(?,4,2,?,?)",
          i,
          sent,
          sent.plus(Duration.ofHours(48)));
    }
    users.status(1, 4, AccountStatus.INACTIVE);
    assertEquals(
        3,
        scalar("SELECT COUNT(*) FROM additional_class_assignment_offers WHERE status='EXPIRED'"));
    assertEquals(
        1,
        scalar("SELECT COUNT(*) FROM additional_class_assignment_offers WHERE status='CANCELLED'"));
    assertTrue(
        new AccountReviewService(db)
            .reasons(1, users.list(1, null, null, null, 1)).get(4L).stream()
                .anyMatch(x -> x.contains("3 unanswered")));
  }

  @Test
  void dashboardReturnsDatabaseCountsAndRejectsExpert() {
    var dashboard = new DashboardService(db);
    assertEquals(5L, dashboard.summary(1).get("users"));
    assertTrue(dashboard.currentTerm(1).contains("FA26"));
    assertThrows(SecurityException.class, () -> dashboard.summary(2));
  }


}
