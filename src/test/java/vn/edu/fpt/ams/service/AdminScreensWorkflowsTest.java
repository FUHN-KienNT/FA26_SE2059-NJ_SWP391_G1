package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import vn.edu.fpt.ams.dto.SubjectDTO;
import vn.edu.fpt.ams.dto.UserCreateRequest;
import vn.edu.fpt.ams.integration.EmailService;
import vn.edu.fpt.ams.integration.GoogleIdentityClient.Identity;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

/** Real JDBC transactions against H2; PostgreSQL-specific triggers need deployment verification. */
class AdminScreensWorkflowsTest {
  Jdbc.Connections db;
  UserService users;
  SubjectService subjects;
  MasterDataService masterData;
  SubjectClassService subjectClasses;
  DashboardService dashboards;
  TemplateService templates;
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

  @Test
  void systemSettingsUseDatabaseFiltersPriorityAndPreserveExistingAccounts() {
    var settings = new SystemSettingService(db);
    assertEquals(8, settings.list(1, null, null, null).size());
    assertEquals(4, settings.list(1, null, SettingType.USER_ROLE, true).size());
    assertEquals(1, settings.list(1, "practice", SettingType.ASSIGNMENT_TYPE, null).size());
    assertThrows(SecurityException.class, () -> settings.list(2, null, null, null));

    SystemSetting expert = settings.list(1, "EXPERT", SettingType.USER_ROLE, null).get(0);
    assertThrows(SecurityException.class,
        () -> settings.update(2, expert.settingId(), "Unauthorized", 9));
    assertThrows(SecurityException.class,
        () -> settings.setActive(2, expert.settingId(), false));
    assertEquals("Expert", settings.detail(1, expert.settingId()).name());
    settings.update(1, expert.settingId(), "Chuyên gia", 4);
    SystemSetting renamed = settings.detail(1, expert.settingId());
    assertEquals("EXPERT", renamed.value());
    assertEquals(SettingType.USER_ROLE, renamed.type());
    assertEquals("Chuyên gia", renamed.name());
    assertEquals("EXPERT", users.roleOptions(1).get(2).value());
    assertThrows(IllegalArgumentException.class,
        () -> settings.update(1, expert.settingId(), " ", 0));

    settings.setActive(1, expert.settingId(), false);
    assertFalse(settings.detail(1, expert.settingId()).active());
    assertEquals(1, scalar("SELECT COUNT(*) FROM system_settings WHERE setting_id=? AND is_active=FALSE", expert.settingId()));
    assertEquals(1, settings.list(1, "EXPERT", SettingType.USER_ROLE, false).size());
    assertTrue(settings.activeOptions(1, SettingType.USER_ROLE).stream()
        .noneMatch(s -> "EXPERT".equals(s.value())));
    assertThrows(IllegalArgumentException.class,
        () -> settings.setActive(1, expert.settingId(), false));
    assertEquals(1, scalar("SELECT COUNT(*) FROM system_settings WHERE setting_id=? AND is_active=FALSE", expert.settingId()));
    assertTrue(users.roleOptions(1).stream().noneMatch(s -> "EXPERT".equals(s.value())));
    assertEquals(AccountStatus.ACTIVE, users.detail(1, 2).accountStatus());
    assertEquals(UserRole.EXPERT, users.detail(1, 2).role());
    assertThrows(IllegalArgumentException.class,
        () -> users.create(1, new UserCreateRequest("New Expert", "new-expert@test.edu",
            UserRole.EXPERT), Set.of()));
    assertEquals(0, scalar("SELECT COUNT(*) FROM users WHERE email='new-expert@test.edu'"));
    settings.setActive(1, expert.settingId(), true);
    assertEquals(1, scalar("SELECT COUNT(*) FROM system_settings WHERE setting_id=? AND is_active=TRUE", expert.settingId()));
    assertTrue(users.roleOptions(1).stream().anyMatch(s -> "EXPERT".equals(s.value())));
    assertThrows(IllegalArgumentException.class,
        () -> settings.setActive(1, expert.settingId(), true));
  }

  @Test
  void noActiveStaffRolePreventsCreationWithoutChangingExistingUsers() {
    var settings = new SystemSettingService(db);
    for (String role : List.of("ADMIN", "EXPERT", "TEACHER")) {
      long id = settings.list(1, role, SettingType.USER_ROLE, null).get(0).settingId();
      settings.setActive(1, id, false);
    }
    assertTrue(users.roleOptions(1).isEmpty());
    assertThrows(IllegalArgumentException.class,
        () -> users.create(1, new UserCreateRequest("New Staff", "new-staff@test.edu",
            UserRole.TEACHER), Set.of()));
    assertEquals(0, scalar("SELECT COUNT(*) FROM users WHERE email='new-staff@test.edu'"));
    assertEquals(UserRole.EXPERT, users.detail(1, 2).role());
    assertEquals(AccountStatus.ACTIVE, users.detail(1, 2).accountStatus());
  }

  @Test
  void futureTermNeverBecomesCurrentFromSubmittedFlag() {
    long id=masterData.createTerm(1,requestedTerm(0,"NEXTTERM",true,true));
    assertTrue(masterData.getTerm(1,id).active());
    assertFalse(masterData.getTerm(1,id).current());
    assertEquals(0,scalar("SELECT COUNT(*) FROM academic_terms WHERE term_id=? AND is_current=TRUE",id));
  }


  @Test
  void futureEnabledTermDoesNotReplaceCurrentOrItsHistory() {
    var existing=masterData.getTerm(1,1);
    long id=masterData.createTerm(1,requestedTerm(0,"LATERTERM",false,true));
    assertEquals(existing,masterData.getTerm(1,1));
    assertFalse(masterData.getTerm(1,id).current());
    assertEquals(2,masterData.listTerms(1,null,true).size());
    assertEquals(1,scalar("SELECT COUNT(*) FROM subject_terms WHERE term_id=1"));
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
    subjects = new SubjectService(db);
    masterData = new MasterDataService(db);
    subjectClasses = new SubjectClassService(db);
    dashboards = new DashboardService(db);
    templates = new TemplateService(db);
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

  private void verifiedStudent(String subject, String email) {
    var account = google.signIn(new Identity(subject, email, "Student Name"), null);
    sql("UPDATE users SET email_verified=TRUE WHERE user_id=?", account.userId());
  }
  @Test
  void templateEditSavesContentAndStatusAtomicallyWithPrimaryPermission() {
    long id=templates.createTemplate(2,new AssignmentTemplate(0,1,"Before","Instructions",BigDecimal.TEN,TemplateStatus.ACTIVE,null,null));
    var changed=new AssignmentTemplate(id,1,"After","Updated instructions",BigDecimal.TEN,TemplateStatus.ACTIVE,null,null);
    assertThrows(SecurityException.class,()->templates.updateTemplate(3,changed,TemplateStatus.INACTIVE));
    assertEquals("Before",templates.findTemplate(2,id).title());
    assertEquals(TemplateStatus.ACTIVE,templates.findTemplate(2,id).status());
    templates.updateTemplate(2,changed,TemplateStatus.INACTIVE);
    assertEquals("After",templates.findTemplate(2,id).title());
    assertEquals(TemplateStatus.INACTIVE,templates.findTemplate(2,id).status());
    templates.updateTemplate(3,changed);
    assertEquals(TemplateStatus.INACTIVE,templates.findTemplate(2,id).status());
    templates.updateTemplate(2,changed,TemplateStatus.ACTIVE);
    assertEquals(TemplateStatus.ACTIVE,templates.findTemplate(2,id).status());
  }
  @Test
  void templateMaximumScoreUsesIntegerRangeAndRejectsInvalidUpdates() {
    long id = templates.createTemplate(2, new AssignmentTemplate(0,1,"Score test","Instructions",
        BigDecimal.ONE,TemplateStatus.ACTIVE,null,null));
    templates.updateTemplate(2, new AssignmentTemplate(id,1,"Score test","Instructions",
        new BigDecimal("100"),TemplateStatus.ACTIVE,null,null));
    for(String value:List.of("0","-1","101","1000","10.5")) {
      var invalid = new AssignmentTemplate(id,1,"Invalid change","Instructions",
          new BigDecimal(value),TemplateStatus.ACTIVE,null,null);
      assertThrows(IllegalArgumentException.class, () -> templates.updateTemplate(2,invalid));
      assertThrows(IllegalArgumentException.class, () -> templates.createTemplate(2,invalid));
    }
    assertEquals(0, new BigDecimal("100").compareTo(templates.findTemplate(2,id).maximumScore()));
    assertEquals("Score test", templates.findTemplate(2,id).title());
  }
  @Test
  void templatePagingPreservesFiltersAndRestrictsTeacherVisibility() {
    for(int i=0;i<23;i++) sql("INSERT INTO assignment_templates(subject_id,title,instructions,maximum_score,status) VALUES(1,?,'Instructions',10,?)",
        "Paging item " + i, i<21?"ACTIVE":"INACTIVE");
    var first = templates.page(2,1,"Paging",null,1);
    var second = templates.page(2,1,"Paging",null,2);
    var last = templates.page(2,1,"Paging",null,999);
    assertEquals(23, first.total());
    assertEquals(10, first.items().size());
    assertEquals(10, second.items().size());
    assertEquals(3, last.page());
    assertEquals(3, last.items().size());
    assertTrue(first.items().stream().noneMatch(a -> second.items().stream().anyMatch(b -> a.templateId()==b.templateId())));
    assertEquals(2, templates.page(2,1,"Paging",TemplateStatus.INACTIVE,1).total());
    assertEquals(21, templates.page(4,1,"Paging",TemplateStatus.INACTIVE,1).total());
    assertEquals(0, templates.page(2,1,"%_",null,1).total());
    assertThrows(SecurityException.class, () -> templates.page(5,1,"",null,1));
  }
  @Test
  void expertAssignmentsAreAdminOnlyAndIncludeRoleAndHistory() {
    var primary = users.expertSubjects(1, 2);
    assertEquals(2, primary.size());
    assertEquals(ResponsibilityType.PRIMARY, primary.get(0).responsibility());
    assertTrue(primary.get(0).active());
    assertTrue(primary.stream().anyMatch(subject -> subject.subjectId()==2 && subject.responsibility()==null && !subject.active()));
    assertEquals(ResponsibilityType.SUPPORTING, users.expertSubjects(1, 3).get(0).responsibility());
    sql("UPDATE subject_experts SET is_active=FALSE,deactivated_at=CURRENT_TIMESTAMP WHERE expert_id=3");
    assertFalse(users.expertSubjects(1, 3).get(0).active());
    assertEquals(2, users.expertSubjects(1, 4).size());
    assertTrue(users.expertSubjects(1, 4).stream().allMatch(subject -> subject.responsibility()==null));
    assertEquals(2, users.expertSubjects(1, 0).size());
    sql("UPDATE subjects SET status='INACTIVE' WHERE subject_id=2");
    assertEquals(2, users.expertSubjects(1, 2).size());
    assertThrows(SecurityException.class, () -> users.expertSubjects(2, 2));
  }
  @Test
  void userDetailStatusIsSavedAtomicallyWithAccountChanges() {
    var admin = users.detail(1, 1);
    assertThrows(IllegalArgumentException.class, () -> users.update(1, 1,
        "Changed Name", admin.email(), UserRole.ADMIN, Set.of(), AccountStatus.INACTIVE));
    assertEquals(admin.fullName(), users.detail(1, 1).fullName());
    assertEquals(AccountStatus.ACTIVE, users.detail(1, 1).accountStatus());
    var expert = users.detail(1, 5);
    users.update(1, 5, "New Name", expert.email(), UserRole.EXPERT, Set.of(), AccountStatus.INACTIVE);
    assertEquals("New Name", users.detail(1, 5).fullName());
    assertEquals(AccountStatus.INACTIVE, users.detail(1, 5).accountStatus());
  }
  @Test
  void googleDisplayNamesAllowDigitsAndFitDatabaseWithoutSkippingVerification() {
    var numbered = google.signIn(new Identity("numbered-sub", "numbered@test.edu", "Nguyễn Nam 123"), null);
    assertEquals("Nguyễn Nam 123", numbered.fullName());
    assertNull(numbered.lastLoginAt());
    assertTrue(new StudentVerificationService(db, new EmailService()).pending(numbered.userId()));
    var longName = google.signIn(new Identity("long-sub", "long@test.edu", "A".repeat(60)), null);
    assertEquals(50, longName.fullName().length());
    var missing = google.signIn(new Identity("missing-name-sub", "fallback@test.edu", null), null);
    assertEquals("fallback", missing.fullName());
  }
  @Test
  void templatesEnforceOwnershipValidationAndPersistLifecycle() {
    var draft = new AssignmentTemplate(0, 1, "Test template", "Instructions", new BigDecimal("10.00"), TemplateStatus.ACTIVE, null, null);
    long id = templates.createTemplate(2, draft);
    assertEquals("Test template", templates.findTemplate(2, id).title());
    assertThrows(SecurityException.class, () -> templates.createTemplate(5, draft));
    assertThrows(SecurityException.class, () -> templates.findTemplate(1, id));
    assertThrows(IllegalArgumentException.class, () -> templates.createTemplate(2,
        new AssignmentTemplate(0, 1, " ", "Instructions", BigDecimal.TEN, TemplateStatus.ACTIVE, null, null)));
    assertThrows(IllegalArgumentException.class, () -> templates.createTemplate(2,
        new AssignmentTemplate(0, 1, "Title", "Instructions", new BigDecimal("0.001"), TemplateStatus.ACTIVE, null, null)));
    templates.updateTemplate(2, new AssignmentTemplate(id, 1, "Updated", "New instructions", BigDecimal.TEN, TemplateStatus.ACTIVE, null, null));
    assertEquals("Updated", templates.findTemplate(2, id).title());
    templates.changeStatus(2, id, TemplateStatus.INACTIVE);
    assertEquals(TemplateStatus.INACTIVE, templates.findTemplate(2, id).status());
    assertThrows(SecurityException.class, () -> templates.findTemplate(4, id));
    templates.changeStatus(2, id, TemplateStatus.ACTIVE);
    assertEquals(TemplateStatus.ACTIVE, templates.findTemplate(4, id).status());
  }
  @Test
  void studentConfirmationRequiresFreshSingleUseTokenAndCommittedAccount() {
    var identity = new Identity("verify-sub", "verify@test.edu", "Student Name");
    var account = google.signIn(identity, null);
    assertNull(account.lastLoginAt());
    var verification = new StudentVerificationService(db, new EmailService() {
      @Override public void sendNotification(String to, String subject, String text) {
        assertEquals(1, scalar("SELECT COUNT(*) FROM users WHERE email=? AND email_verified=FALSE", to));
        mailed = text;
      }
    });
    assertTrue(verification.pending(account.userId()));
    assertTrue(verification.send(account.userId()));
    String token = mailed.split("token=")[1].split("\\n")[0];
    assertEquals(0, scalar("SELECT COUNT(*) FROM student_email_verifications WHERE token_hash=?", token));
    assertThrows(IllegalArgumentException.class, () -> verification.send(account.userId()));
    assertThrows(IllegalArgumentException.class, () -> verification.confirm("wrong"));
    verification.confirm(token);
    assertFalse(verification.pending(account.userId()));
    assertThrows(IllegalArgumentException.class, () -> verification.confirm(token));
    assertNotNull(google.signIn(identity, null).lastLoginAt());
  }
  @Test
  void expiredTokenAndSmtpFailureDoNotActivateStudent() {
    var account = google.signIn(new Identity("expire-sub", "expire@test.edu", "Student Name"), null);
    var verification = new StudentVerificationService(db, new EmailService() {
      @Override public void sendNotification(String to, String subject, String text) {
        if(failMail) throw new IllegalStateException("SMTP unavailable");
        mailed = text;
      }
    });
    failMail = true;
    assertFalse(verification.send(account.userId()));
    assertTrue(verification.pending(account.userId()));
    assertEquals(0, scalar("SELECT COUNT(*) FROM student_email_verifications"));
    failMail = false;
    assertTrue(verification.send(account.userId()));
    String token = mailed.split("token=")[1].split("\\n")[0];
    sql("UPDATE student_email_verifications SET expires_at=?", Instant.now().minusSeconds(1));
    assertThrows(IllegalArgumentException.class, () -> verification.confirm(token));
    assertTrue(verification.pending(account.userId()));
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
            .replaceAll("CHECK \\(jsonb_typeof\\([^)]*\\) = '(array|object)'\\)", "");
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
    assertNull(again.lastLoginAt());
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
    var pending = google.signIn(identity, null);
    sql("UPDATE users SET email_verified=TRUE WHERE user_id=?", pending.userId());
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
    verifiedStudent("s", "wrong@test.edu"); verifiedStudent("invited-sub", "invited@test.edu");
    assertThrows(
        SecurityException.class,
        () -> google.signIn(new Identity("s", "wrong@test.edu", "Wrong Name"), "token"));
    assertThrows(
        IllegalArgumentException.class,
        () -> google.signIn(new Identity("invited-sub", "invited@test.edu", "Student Name"), "missing"));
    assertEquals(7, scalar("SELECT COUNT(*) FROM users"));
    assertEquals(0, scalar("SELECT COUNT(*) FROM class_enrollments"));
  }

  @Test
  void expiredOrOverCapacityInvitationRollsBackEverything() {
    verifiedStudent("old-sub", "old@test.edu");
    sql(
        "INSERT INTO student_invitations(class_id,invited_by,email,token,sent_at,expires_at)"
            + " VALUES(1,4,'old@test.edu','old',?,?)",
        Instant.now().minusSeconds(259200),
        Instant.now().minusSeconds(86400));
    assertThrows(
        IllegalArgumentException.class,
        () -> google.signIn(new Identity("old-sub", "old@test.edu", "Student Name"), "old"));
    verifiedStudent("new-sub", "s0@test.edu");
    for (int n = 0; n < 11; n++) invite("t" + n, "s" + n + "@test.edu");
    assertThrows(
        IllegalArgumentException.class,
        () -> google.signIn(new Identity("new-sub", "s0@test.edu", "Student Name"), "t0"));
    assertEquals(7, scalar("SELECT COUNT(*) FROM users"));
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

  @Test
  void subjectListSupportsAuthorizationSearchStatusPaginationAndEmptyResults() {
    assertEquals(2, subjects.list(1, null, null, 1).size());
    assertEquals("SWP391", subjects.list(1, "swp", null, 1).get(0).subjectCode());
    assertEquals("PRJ301", subjects.list(1, "java web", null, 1).get(0).subjectCode());
    assertTrue(subjects.list(1, "missing", null, 1).isEmpty());
    sql("UPDATE subjects SET status='INACTIVE' WHERE subject_id=2");
    assertEquals(1, subjects.list(1, null, SubjectStatus.ACTIVE, 1).size());
    assertEquals("PRJ301", subjects.list(1, null, SubjectStatus.INACTIVE, 1).get(0).subjectCode());
    assertEquals("PRJ301", subjects.list(1, "java", SubjectStatus.INACTIVE, 1).get(0).subjectCode());
    assertTrue(subjects.list(1, "swp", SubjectStatus.INACTIVE, 1).isEmpty());
    for (int i = 0; i < 21; i++)
      sql(
          "INSERT INTO subjects(subject_code,subject_name) VALUES(?,?)",
          "T" + i,
          "Test " + i);
    assertEquals(20, subjects.list(1, null, null, 1).size());
    assertEquals(3, subjects.list(1, null, null, 2).size());
    assertThrows(
        IllegalArgumentException.class,
        () -> subjects.list(1, null, null, 0));
    assertThrows(SecurityException.class, () -> subjects.list(2, null, null, 1));
  }

  @Test
  void subjectListShowsCurrentAndHistoricalTermsAndFiltersBeforePagination() {
    sql("UPDATE subject_terms SET registration_enabled=TRUE WHERE subject_id=1");
    sql("INSERT INTO academic_terms(term_code,term_name,starts_at,ends_at,is_current,is_active) VALUES('PAST','Previous term',?,?,FALSE,FALSE)",
        Instant.now().minusSeconds(900000), Instant.now().minusSeconds(800000));
    sql("INSERT INTO subject_terms(subject_id,term_id,registration_enabled,registration_opens_at,registration_closes_at) VALUES(1,2,TRUE,?,?)",
        Instant.now().minusSeconds(900000), Instant.now().minusSeconds(800000));
    var overview = subjects.list(1, "SWP", null, 1).get(0);
    assertEquals(2, overview.subjectTerms().size());
    assertTrue(overview.subjectTerms().get(0).currentTerm());
    assertTrue(overview.subjectTerms().get(0).termActive());
    assertEquals("OPEN", overview.subjectTerms().get(0).registrationStatus());
    assertFalse(overview.subjectTerms().get(1).termActive());
    assertEquals("TERM_INACTIVE", overview.subjectTerms().get(1).registrationStatus());
    assertEquals(overview.subjectTerms(), subjects.details(1, 1).subjectTerms());
    assertTrue(subjects.list(1, "PRJ", null, 1).get(0).subjectTerms().isEmpty());
    assertEquals(1, subjects.count(1, null, null, 2L));
    for (int i = 0; i < 21; i++) {
      sql("INSERT INTO subjects(subject_code,subject_name) VALUES(?,?)", "TERM"+i, "Term test "+i);
      sql("INSERT INTO subject_terms(subject_id,term_id,registration_opens_at,registration_closes_at) SELECT subject_id,2,?,? FROM subjects WHERE subject_code=?",
          Instant.now().minusSeconds(900000), Instant.now().minusSeconds(800000), "TERM"+i);
    }
    assertEquals(22, subjects.count(1, null, null, 2L));
    assertEquals(20, subjects.list(1, null, null, 2L, 1).size());
    assertEquals(2, subjects.list(1, null, null, 2L, 2).size());
    assertEquals(1, subjects.list(1, null, null, 1L, 1).size());
    assertTrue(subjects.list(1, "missing", null, 2L, 1).isEmpty());
    assertEquals(2, subjects.termOptions(1).size());
    assertThrows(SecurityException.class, () -> subjects.list(2, null, null, 2L, 1));
    assertThrows(SecurityException.class, () -> subjects.count(2, null, null, 2L));
    assertThrows(SecurityException.class, () -> subjects.termOptions(2));
    assertThrows(IllegalArgumentException.class, () -> subjects.list(1, null, null, -1L, 1));
  }

  @Test
  void subjectTermDisplayDoesNotReportAnUnavailableOfferingAsOpen() {
    sql("UPDATE subject_terms SET registration_enabled=TRUE WHERE subject_id=1");
    assertEquals("OPEN", subjects.list(1, "SWP", null, 1).get(0).subjectTerms().get(0).registrationStatus());
    sql("UPDATE academic_terms SET is_current=FALSE WHERE term_id=1");
    assertEquals("OPEN", subjects.list(1, "SWP", null, 1).get(0).subjectTerms().get(0).registrationStatus());
    sql("UPDATE academic_terms SET is_current=TRUE WHERE term_id=1");
    sql("UPDATE subject_terms SET registration_enabled=FALSE WHERE subject_id=1");
    assertEquals("DISABLED", subjects.list(1, "SWP", null, 1).get(0).subjectTerms().get(0).registrationStatus());
    sql("UPDATE subject_terms SET registration_enabled=TRUE,registration_opens_at=?,registration_closes_at=? WHERE subject_id=1",
        Instant.now().plusSeconds(600), Instant.now().plusSeconds(3600));
    assertEquals("UPCOMING", subjects.list(1, "SWP", null, 1).get(0).subjectTerms().get(0).registrationStatus());
    sql("UPDATE subject_terms SET registration_opens_at=?,registration_closes_at=? WHERE subject_id=1",
        Instant.now().minusSeconds(3600), Instant.now().minusSeconds(600));
    assertEquals("CLOSED", subjects.list(1, "SWP", null, 1).get(0).subjectTerms().get(0).registrationStatus());
    sql("UPDATE subjects SET status='INACTIVE' WHERE subject_id=1");
    assertEquals("SUBJECT_INACTIVE", subjects.list(1, "SWP", null, 1).get(0).subjectTerms().get(0).registrationStatus());
  }

  @Test
  void endedHistoricalTermIsExcludedAndCannotBeChangedAtTheExactEndTime() {
    Instant end=Instant.parse("2030-01-01T00:00:00Z");
    sql("UPDATE academic_terms SET starts_at=?,ends_at=?,is_active=TRUE,is_current=FALSE WHERE term_id=1",end.minusSeconds(86400),end);
    sql("UPDATE subject_terms SET registration_enabled=TRUE,registration_opens_at=?,registration_closes_at=? WHERE subject_term_id=1",end.minusSeconds(3600),end.plusSeconds(86400));
    var before=new SubjectService(db,Clock.fixed(end.minusNanos(1),ZoneOffset.UTC));
    assertEquals(1,before.eligibleTerms(1).size());
    assertEquals("OPEN",before.details(1,1).subjectTerms().get(0).registrationStatus());
    var ended=new SubjectService(db,Clock.fixed(end,ZoneOffset.UTC));
    assertTrue(ended.eligibleTerms(1).isEmpty());
    assertEquals("TERM_ENDED",ended.details(1,1).subjectTerms().get(0).registrationStatus());
    for(boolean enabled:List.of(true,false))
      assertThrows(IllegalArgumentException.class,()->ended.openForTerm(1,
          new SubjectTerm(0,1,1,enabled,end.minusSeconds(1800),end.plusSeconds(3600))));
    var stored=ended.details(1,1).subjectTerms().get(0);
    assertTrue(stored.registrationEnabled());
    assertEquals(end.minusSeconds(3600),stored.registrationOpensAt());
    assertEquals(SubjectStatus.ACTIVE,ended.details(1,1).subject().status());
    long subjectRows=scalar("SELECT COUNT(*) FROM subjects"), expertRows=scalar("SELECT COUNT(*) FROM subject_experts");
    assertThrows(IllegalArgumentException.class,()->ended.create(1,
        new SubjectDTO(0,"ENDEDNEW","Ended",null,SubjectStatus.ACTIVE),Map.of(2L,ResponsibilityType.PRIMARY),
        new SubjectService.InitialTerm(1,true,end,end.plusSeconds(3600))));
    assertEquals(subjectRows,scalar("SELECT COUNT(*) FROM subjects"));
    assertEquals(expertRows,scalar("SELECT COUNT(*) FROM subject_experts"));
    assertEquals(0,scalar("SELECT COUNT(*) FROM subjects WHERE subject_code='ENDEDNEW'"));
  }

  @Test
  void onlyActiveUnendedTermsAreEditableWhileHistoricalTermsRemainViewable() {
    Instant now=Instant.parse("2030-01-01T00:00:00Z");
    sql("UPDATE academic_terms SET starts_at=?,ends_at=?,is_current=FALSE WHERE term_id=1",now.minusSeconds(86400),now.minusSeconds(1));
    sql("INSERT INTO academic_terms(term_code,term_name,starts_at,ends_at,is_current,is_active) VALUES('FUTUREOFF','Future disabled',?,?,FALSE,FALSE),('FUTUREON','Future active',?,?,FALSE,TRUE)",now.plusSeconds(3600),now.plusSeconds(86400),now.plusSeconds(3600),now.plusSeconds(86400));
    var service=new SubjectService(db,Clock.fixed(now,ZoneOffset.UTC));
    assertEquals(List.of("FUTUREON"),service.eligibleTerms(1).stream().map(AcademicTerm::termCode).toList());
    assertEquals(3,service.termOptions(1).size());
    assertEquals(1,service.details(1,1).subjectTerms().size());
    assertThrows(SecurityException.class,()->service.eligibleTerms(2));
  }

  @Test
  void staleManualCurrentFlagCannotReopenAnEndedTerm() {
    Instant now=Instant.parse("2030-01-01T00:00:00Z");
    sql("UPDATE academic_terms SET starts_at=?,ends_at=?,is_current=TRUE,is_active=TRUE WHERE term_id=1",now.minusSeconds(172800),now.minusSeconds(86400));
    var service=new SubjectService(db,Clock.fixed(now,ZoneOffset.UTC));
    assertTrue(service.eligibleTerms(1).isEmpty());
    assertFalse(service.termOptions(1).get(0).current());
    assertThrows(IllegalArgumentException.class,()->service.openForTerm(1,new SubjectTerm(0,1,1,true,now,now.plusSeconds(3600))));
    assertEquals("TERM_ENDED",service.details(1,1).subjectTerms().get(0).registrationStatus());
    assertEquals(1,service.list(1,null,null,1L,1).size());
  }


  @Test
  void creatingSubjectCanSaveItsInitialTermAndExpertsTogether() {
    Instant opens=Instant.now().minusSeconds(3600), closes=Instant.now().plusSeconds(86400);
    long id=subjects.create(1,new SubjectDTO(0,"ONEFORM","One form",null,SubjectStatus.ACTIVE),
        Map.of(2L,ResponsibilityType.PRIMARY,3L,ResponsibilityType.SUPPORTING),
        new SubjectService.InitialTerm(1,true,opens,closes));
    var created=subjects.details(1,id);
    assertEquals("ONEFORM",created.subject().subjectCode());
    assertEquals(1,created.primaryExperts().size());
    assertEquals(1,created.supportingExperts().size());
    assertEquals(1,created.subjectTerms().size());
    assertEquals(1,created.subjectTerms().get(0).termId());
    assertEquals("OPEN",created.subjectTerms().get(0).registrationStatus());
    assertEquals(1,subjects.count(1,"ONEFORM",null,1L));
    assertEquals(1,scalar("SELECT COUNT(*) FROM subject_terms WHERE subject_id=?",id));
    assertThrows(IllegalArgumentException.class,()->subjects.create(1,
        new SubjectDTO(0,"ONEFORM","Duplicate",null,SubjectStatus.ACTIVE),
        Map.of(2L,ResponsibilityType.PRIMARY),new SubjectService.InitialTerm(1,true,opens,closes)));
    assertEquals(1,scalar("SELECT COUNT(*) FROM subjects WHERE subject_code='ONEFORM'"));
  }

  @Test
  void failedInitialTermRollsBackNewSubjectExpertsAndOffering() {
    Instant opens=Instant.now(), closes=opens.plusSeconds(86400);
    sql("INSERT INTO academic_terms(term_code,term_name,starts_at,ends_at,is_current,is_active) VALUES('DISABLED','Disabled',?,?,FALSE,FALSE)",opens,closes);
    long subjectCount=scalar("SELECT COUNT(*) FROM subjects"), expertCount=scalar("SELECT COUNT(*) FROM subject_experts"), termCount=scalar("SELECT COUNT(*) FROM subject_terms");
    var invalid=List.of(new SubjectService.InitialTerm(2,true,opens,closes),
        new SubjectService.InitialTerm(999,true,opens,closes),
        new SubjectService.InitialTerm(1,true,closes,opens),
        new SubjectService.InitialTerm(1,true,null,closes));
    for(int i=0;i<invalid.size();i++) {
      var opening=invalid.get(i); String code="BADTERM"+i;
      assertThrows(RuntimeException.class,()->subjects.create(1,
          new SubjectDTO(0,code,"Invalid opening",null,SubjectStatus.ACTIVE),
          Map.of(2L,ResponsibilityType.PRIMARY),opening));
      assertEquals(0,scalar("SELECT COUNT(*) FROM subjects WHERE subject_code=?",code));
      assertEquals(subjectCount,scalar("SELECT COUNT(*) FROM subjects"));
      assertEquals(expertCount,scalar("SELECT COUNT(*) FROM subject_experts"));
      assertEquals(termCount,scalar("SELECT COUNT(*) FROM subject_terms"));
    }
    assertThrows(IllegalArgumentException.class,()->subjects.create(1,
        new SubjectDTO(0,"INACTIVEOPEN","Inactive",null,SubjectStatus.INACTIVE),
        Map.of(2L,ResponsibilityType.PRIMARY),new SubjectService.InitialTerm(1,true,opens,closes)));
    assertThrows(SecurityException.class,()->subjects.create(2,
        new SubjectDTO(0,"FORBIDDENOPEN","Forbidden",null,SubjectStatus.ACTIVE),
        Map.of(2L,ResponsibilityType.PRIMARY),new SubjectService.InitialTerm(1,true,opens,closes)));
    assertEquals(subjectCount,scalar("SELECT COUNT(*) FROM subjects"));
    assertEquals(expertCount,scalar("SELECT COUNT(*) FROM subject_experts"));
    assertEquals(termCount,scalar("SELECT COUNT(*) FROM subject_terms"));
  }

  @Test
  void catalogOnlyCreationDoesNotRequireAnActiveTermOrCreateAnOffering() {
    sql("UPDATE academic_terms SET is_current=FALSE,is_active=FALSE");
    long id=subjects.create(1,new SubjectDTO(0,"CATONLY","Catalog only",null,SubjectStatus.INACTIVE),
        Map.of(2L,ResponsibilityType.PRIMARY),null);
    assertEquals(SubjectStatus.INACTIVE,subjects.details(1,id).subject().status());
    assertTrue(subjects.details(1,id).subjectTerms().isEmpty());
    assertEquals(0,scalar("SELECT COUNT(*) FROM subject_terms WHERE subject_id=?",id));
  }

  @Test
  void subjectCreateIsAtomicAndEnforcesAllExpertAndFieldRules() {
    long one =
        subjects.create(
            1,
            new SubjectDTO(0, " mad101 ", "Mathematics", null, SubjectStatus.ACTIVE),
            Map.of(2L, ResponsibilityType.PRIMARY));
    assertEquals(1, scalar("SELECT COUNT(*) FROM subjects WHERE subject_id=? AND subject_code='MAD101'", one));
    long two =
        subjects.create(
            1,
            new SubjectDTO(0, "DBI202", "Database", "Data", SubjectStatus.INACTIVE),
            Map.of(
                2L, ResponsibilityType.PRIMARY,
                3L, ResponsibilityType.PRIMARY,
                5L, ResponsibilityType.SUPPORTING));
    assertEquals(3, scalar("SELECT COUNT(*) FROM subject_experts WHERE subject_id=? AND is_active=TRUE", two));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.create(
                1,
                new SubjectDTO(0, "mad101", "Duplicate", null, SubjectStatus.ACTIVE),
                Map.of(2L, ResponsibilityType.PRIMARY)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.create(
                1,
                new SubjectDTO(0, " ", "Blank code", null, SubjectStatus.ACTIVE),
                Map.of(2L, ResponsibilityType.PRIMARY)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.create(
                1,
                new SubjectDTO(0, "NEW1", " ", null, SubjectStatus.ACTIVE),
                Map.of(2L, ResponsibilityType.PRIMARY)));
    var missingPrimary = assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.create(
                1,
                new SubjectDTO(0, "NEW2", "No primary", null, SubjectStatus.ACTIVE),
                Map.of(3L, ResponsibilityType.SUPPORTING)));
    assertEquals("Môn học cần có từ 1 đến 2 chuyên gia chính.", missingPrimary.getMessage());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.create(
                1,
                new SubjectDTO(0, "NEW3", "Three primary", null, SubjectStatus.ACTIVE),
                Map.of(
                    2L, ResponsibilityType.PRIMARY,
                    3L, ResponsibilityType.PRIMARY,
                    5L, ResponsibilityType.PRIMARY)));
    sql(
        "INSERT INTO users(full_name,email,role,password_hash) VALUES('Extra Expert',?,'EXPERT','fixture-hash')",
        UUID.randomUUID() + "@test.edu");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.create(
                1,
                new SubjectDTO(0, "NEW4", "Three support", null, SubjectStatus.ACTIVE),
                Map.of(
                    2L, ResponsibilityType.PRIMARY,
                    3L, ResponsibilityType.SUPPORTING,
                    5L, ResponsibilityType.SUPPORTING,
                    6L, ResponsibilityType.SUPPORTING)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.create(
                1,
                new SubjectDTO(0, "NEW5", "Teacher invalid", null, SubjectStatus.ACTIVE),
                Map.of(4L, ResponsibilityType.PRIMARY)));
    sql("UPDATE users SET account_status='INACTIVE' WHERE user_id=5");
    long before = scalar("SELECT COUNT(*) FROM subjects");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.create(
                1,
                new SubjectDTO(0, "ROLLBACK", "Rollback", null, SubjectStatus.ACTIVE),
                Map.of(5L, ResponsibilityType.PRIMARY)));
    assertEquals(before, scalar("SELECT COUNT(*) FROM subjects"));
    assertEquals(0, scalar("SELECT COUNT(*) FROM subjects WHERE subject_code='ROLLBACK'"));
  }

  @Test
  void subjectUpdateKeepsCodeStatusAndAuthorizationBoundaries() {
    subjects.update(1, 1, "swp391", "Updated Project", "Updated description");
    assertEquals(
        1,
        scalar(
            "SELECT COUNT(*) FROM subjects WHERE subject_id=1 AND subject_code='SWP391'"
                + " AND subject_name='Updated Project' AND status='ACTIVE'"));
    assertThrows(
        IllegalArgumentException.class,
        () -> subjects.update(1, 1, "OTHER", "Name", null));
    assertThrows(
        NoSuchElementException.class,
        () -> subjects.update(1, 999, "ABC", "Name", null));
    assertThrows(
        SecurityException.class,
        () -> subjects.update(2, 1, "SWP391", "Unauthorized", null));
  }

  @Test
  void expertAssignmentChangesRolesPreservesRowsAndEnforcesLimits() {
    long stableId =
        scalar(
            "SELECT subject_expert_id FROM subject_experts WHERE subject_id=1 AND expert_id=2");
    subjects.assignExperts(
        1,
        1,
        Map.of(
            2L, ResponsibilityType.SUPPORTING,
            3L, ResponsibilityType.PRIMARY,
            5L, ResponsibilityType.SUPPORTING));
    assertEquals(
        1,
        scalar(
            "SELECT COUNT(*) FROM subject_experts WHERE subject_id=1 AND expert_id=3"
                + " AND responsibility_type='PRIMARY' AND is_active=TRUE"));
    assertEquals(
        2,
        scalar(
            "SELECT COUNT(*) FROM subject_experts WHERE subject_id=1"
                + " AND responsibility_type='SUPPORTING' AND is_active=TRUE"));
    subjects.assignExperts(
        1,
        1,
        Map.of(
            3L, ResponsibilityType.PRIMARY,
            5L, ResponsibilityType.SUPPORTING));
    assertEquals(
        1,
        scalar(
            "SELECT COUNT(*) FROM subject_experts WHERE subject_expert_id=?"
                + " AND is_active=FALSE AND deactivated_at IS NOT NULL",
            stableId));
    subjects.assignExperts(
        1,
        1,
        Map.of(
            2L, ResponsibilityType.PRIMARY,
            3L, ResponsibilityType.PRIMARY,
            5L, ResponsibilityType.SUPPORTING));
    assertEquals(
        stableId,
        scalar(
            "SELECT subject_expert_id FROM subject_experts WHERE subject_id=1 AND expert_id=2"));
    assertEquals(
        1,
        scalar(
            "SELECT COUNT(*) FROM subject_experts WHERE subject_id=1 AND expert_id=2"));
    assertThrows(
        IllegalArgumentException.class,
        () -> subjects.assignExperts(1, 1, Map.of(3L, ResponsibilityType.SUPPORTING)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.assignExperts(
                1,
                1,
                Map.of(
                    2L, ResponsibilityType.PRIMARY,
                    3L, ResponsibilityType.PRIMARY,
                    5L, ResponsibilityType.PRIMARY)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.assignExperts(
                1,
                1,
                Map.of(
                    2L, ResponsibilityType.PRIMARY,
                    3L, ResponsibilityType.SUPPORTING,
                    5L, ResponsibilityType.SUPPORTING,
                    4L, ResponsibilityType.SUPPORTING)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.assignExperts(
                1, 1, Map.of(4L, ResponsibilityType.PRIMARY)));
    sql("UPDATE users SET account_status='INACTIVE' WHERE user_id=5");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.assignExperts(
                1,
                1,
                Map.of(
                    2L, ResponsibilityType.PRIMARY,
                    5L, ResponsibilityType.SUPPORTING)));
  }

  @Test
  void expertRemovalRespectsActiveClassExpertDependency() {
    sql("INSERT INTO class_experts(class_id,subject_id,expert_id) VALUES(1,1,3)");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.assignExperts(
                1, 1, Map.of(2L, ResponsibilityType.PRIMARY)));
    assertEquals(
        1,
        scalar(
            "SELECT COUNT(*) FROM subject_experts WHERE subject_id=1 AND expert_id=3"
                + " AND is_active=TRUE"));
  }

  @Test
  void subjectStatusTransitionsPreserveHistoryAndCloseRegistration() {
    sql("UPDATE subject_terms SET registration_enabled=TRUE WHERE subject_term_id=1");
    long subjectRows = scalar("SELECT COUNT(*) FROM subjects");
    long expertRows = scalar("SELECT COUNT(*) FROM subject_experts WHERE subject_id=1");
    subjects.changeStatus(1, 1, SubjectStatus.INACTIVE);
    assertEquals(
        1,
        scalar(
            "SELECT COUNT(*) FROM subjects WHERE subject_id=1 AND status='INACTIVE'"));
    assertEquals(
        0,
        scalar(
            "SELECT COUNT(*) FROM subject_terms WHERE subject_id=1"
                + " AND registration_enabled=TRUE"));
    assertEquals(subjectRows, scalar("SELECT COUNT(*) FROM subjects"));
    assertEquals(
        expertRows,
        scalar("SELECT COUNT(*) FROM subject_experts WHERE subject_id=1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> subjects.changeStatus(1, 1, SubjectStatus.INACTIVE));
    subjects.changeStatus(1, 1, SubjectStatus.ACTIVE);
    assertEquals(
        1,
        scalar("SELECT COUNT(*) FROM subjects WHERE subject_id=1 AND status='ACTIVE'"));
    assertThrows(
        SecurityException.class,
        () -> subjects.changeStatus(2, 1, SubjectStatus.INACTIVE));
  }

  @Test
  void editingOneOfferingDoesNotCloseOthersButSubjectDeactivationClosesAllWithoutDeletingHistory() {
    Instant opens=Instant.now().minusSeconds(3600), closes=Instant.now().plusSeconds(86400);
    sql("INSERT INTO academic_terms(term_code,term_name,starts_at,ends_at,is_current,is_active) VALUES('NEXT','Next',?,?,FALSE,TRUE)",
        closes,closes.plusSeconds(86400));
    subjects.openForTerm(1,new SubjectTerm(0,1,1,true,opens,closes));
    long next=subjects.openForTerm(1,new SubjectTerm(0,1,2,true,opens,closes));
    subjects.openForTerm(1,new SubjectTerm(0,1,1,false,opens,closes));
    assertEquals(1,scalar("SELECT COUNT(*) FROM subject_terms WHERE subject_term_id=? AND registration_enabled=TRUE",next));
    sql("INSERT INTO users(full_name,email,role,google_subject,email_verified) VALUES('History Student','history@test.edu','STUDENT','history-subject',TRUE)");
    sql("INSERT INTO subject_registrations(student_id,subject_term_id) SELECT user_id,1 FROM users WHERE email='history@test.edu'");
    sql("INSERT INTO class_enrollments(registration_id,class_id,enrollment_method) SELECT registration_id,1,'SELF_REGISTRATION' FROM subject_registrations");
    long template=templates.createTemplate(2,new AssignmentTemplate(0,1,"History fixture","Instructions",BigDecimal.TEN,TemplateStatus.ACTIVE,null,null));
    sql("INSERT INTO assignments(class_id,template_id,title,assignment_purpose,response_type,instructions,maximum_score) VALUES(1,?,'History assignment','REGULAR','ESSAY','Instructions',10)",template);
    sql("INSERT INTO submissions(assignment_id,enrollment_id,attempt_number,deadline_at) SELECT a.assignment_id,e.enrollment_id,1,? FROM assignments a CROSS JOIN class_enrollments e",closes);
    var counts=new LinkedHashMap<String,Long>();
    for(String table:List.of("subjects","subject_terms","subject_experts","subject_classes","subject_registrations","class_enrollments","assignments","submissions"))
      counts.put(table,scalar("SELECT COUNT(*) FROM "+table));
    subjects.changeStatus(1,1,SubjectStatus.INACTIVE);
    assertEquals(0,scalar("SELECT COUNT(*) FROM subject_terms WHERE subject_id=1 AND registration_enabled=TRUE"));
    counts.forEach((table,count)->assertEquals(count.longValue(),scalar("SELECT COUNT(*) FROM "+table),table));
    assertEquals(1,scalar("SELECT COUNT(*) FROM subject_registrations WHERE status='ACTIVE'"));
    assertEquals(1,scalar("SELECT COUNT(*) FROM class_enrollments WHERE status='ACTIVE'"));
    assertEquals(1,scalar("SELECT COUNT(*) FROM submissions WHERE status='IN_PROGRESS'"));
    subjects.changeStatus(1,1,SubjectStatus.ACTIVE);
    assertEquals(0,scalar("SELECT COUNT(*) FROM subject_terms WHERE subject_id=1 AND registration_enabled=TRUE"));
    assertEquals(next,subjects.openForTerm(1,new SubjectTerm(0,1,2,true,opens,closes)));
    assertEquals(0,scalar("SELECT COUNT(*) FROM subject_terms WHERE subject_id=1 AND term_id=1 AND registration_enabled=TRUE"));
    sql("UPDATE academic_terms SET is_active=FALSE WHERE term_id=2");
    assertThrows(IllegalArgumentException.class,()->subjects.openForTerm(1,new SubjectTerm(0,1,2,true,opens,closes)));
    assertThrows(SecurityException.class,()->subjects.changeStatus(2,1,SubjectStatus.INACTIVE));
    assertEquals(SubjectStatus.ACTIVE,subjects.details(1,1).subject().status());
  }

  @Test
  void subjectTermValidatesAndUpdatesStablePairWithoutDuplicate() {
    Instant opens = Instant.now().plus(Duration.ofDays(2));
    Instant closes = opens.plus(Duration.ofDays(7));
    long existing =
        subjects.openForTerm(
            1, new SubjectTerm(0, 1, 1, true, opens, closes));
    assertEquals(1, existing);
    assertEquals(
        1,
        scalar(
            "SELECT COUNT(*) FROM subject_terms WHERE subject_id=1 AND term_id=1"));
    Instant changedOpen = opens.plus(Duration.ofHours(1));
    long updated =
        subjects.openForTerm(
            1,
            new SubjectTerm(0, 1, 1, false, changedOpen, closes));
    assertEquals(existing, updated);
    assertEquals(
        1,
        scalar(
            "SELECT COUNT(*) FROM subject_terms WHERE subject_id=1 AND term_id=1"));
    assertEquals(
        0,
        scalar(
            "SELECT COUNT(*) FROM subject_terms WHERE subject_id=1 AND term_id=1"
                + " AND registration_enabled=TRUE"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.openForTerm(
                1, new SubjectTerm(0, 1, 1, true, closes, opens)));
    assertThrows(
        NoSuchElementException.class,
        () ->
            subjects.openForTerm(
                1, new SubjectTerm(0, 1, 999, true, opens, closes)));
    sql("UPDATE subjects SET status='INACTIVE' WHERE subject_id=2");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            subjects.openForTerm(
                1, new SubjectTerm(0, 2, 1, true, opens, closes)));
    assertThrows(
        SecurityException.class,
        () ->
            subjects.openForTerm(
                2, new SubjectTerm(0, 1, 1, true, opens, closes)));
  }

  @Test
  void subjectTermDisplayStatusRequiresActiveSubjectTermAndEnabledRegistration() {
    assertEquals("DISABLED", subjects.details(1, 1).subjectTerms().get(0).registrationStatus());
    sql("UPDATE subject_terms SET registration_enabled=TRUE WHERE subject_term_id=1");
    assertEquals("OPEN", subjects.details(1, 1).subjectTerms().get(0).registrationStatus());

    sql("UPDATE academic_terms SET is_current=FALSE,is_active=FALSE WHERE term_id=1");
    assertEquals("TERM_INACTIVE", subjects.details(1, 1).subjectTerms().get(0).registrationStatus());
    sql("UPDATE subjects SET status='INACTIVE' WHERE subject_id=1");
    assertEquals("SUBJECT_INACTIVE", subjects.details(1, 1).subjectTerms().get(0).registrationStatus());
    assertEquals(1, scalar("SELECT COUNT(*) FROM subject_terms WHERE subject_term_id=1"));
  }

  @Test
  void masterDataListCreateUpdateAndCurrentTermRules() {
    assertEquals(1, masterData.listTerms(1, "fa", true).size());
    assertThrows(SecurityException.class, () -> masterData.listTerms(2, null, null));
    assertThrows(IllegalArgumentException.class, () -> masterData.listTerms(1, "x".repeat(51), null));

    Instant start=Instant.parse("2027-01-01T00:00:00Z");
    Instant end=Instant.parse("2027-05-01T00:00:00Z");
    long id=masterData.createTerm(1,new AcademicTerm(0," SP27 ","Spring 2027",start,end,false,false));
    assertEquals("SP27",masterData.getTerm(1,id).termCode());
    assertThrows(IllegalArgumentException.class,()->masterData.createTerm(1,
        new AcademicTerm(0,"SP27","Duplicate",start,end,false,true)));
    assertThrows(IllegalArgumentException.class,()->masterData.createTerm(1,
        new AcademicTerm(0,"2027","Digits",start,end,false,true)));

    assertThrows(IllegalArgumentException.class,()->masterData.markCurrent(1,id));
    assertTrue(masterData.getTerm(1,1).current());
    masterData.updateTerm(1,new AcademicTerm(id,"SP27","Spring Updated",start,end,false,false));
    assertEquals("Spring Updated",masterData.getTerm(1,id).termName());
    assertThrows(IllegalArgumentException.class,()->masterData.updateTerm(1,
        new AcademicTerm(1,"FA26","Fall",start,end,true,true)));
  }

  @Test
  void savingUnchangedTermDatesAfterSubjectSetupKeepsStoredTimestamps() {
    AcademicTerm old = masterData.getTerm(1, 1);
    Instant formStart = old.startsAt().atZone(TermTimeline.ZONE).toLocalDate()
        .atStartOfDay(TermTimeline.ZONE).toInstant();
    Instant formEnd = old.endsAt().atZone(TermTimeline.ZONE).toLocalDate()
        .plusDays(1).atStartOfDay(TermTimeline.ZONE).minusNanos(1).toInstant();

    masterData.updateTerm(1, new AcademicTerm(1, old.termCode(), "Fall renamed",
        formStart, formEnd, true, true));

    AcademicTerm saved = masterData.getTerm(1, 1);
    assertEquals("Fall renamed", saved.termName());
    assertEquals(old.startsAt(), saved.startsAt());
    assertEquals(old.endsAt(), saved.endsAt());
    Instant changedEnd = old.endsAt().atZone(TermTimeline.ZONE).toLocalDate()
        .plusDays(2).atStartOfDay(TermTimeline.ZONE).minusNanos(1).toInstant();
    var error = assertThrows(IllegalArgumentException.class, () -> masterData.updateTerm(1,
        new AcademicTerm(1, old.termCode(), "Should not save", formStart, changedEnd, true, true)));
    assertEquals("Không thể thay đổi ngày học kỳ khi đã có cấu hình môn học cho học kỳ này.",
        error.getMessage());
    assertEquals(saved, masterData.getTerm(1, 1));
  }

  @Test
  void termStatusFilterIsIndependentOfSubjectStatus() {
    sql("UPDATE subjects SET status='INACTIVE' WHERE subject_id=2");
    assertTrue(masterData.listTerms(1, null, false).isEmpty());
    assertEquals(1, masterData.listTerms(1, null, true).size());
    assertEquals("PRJ301", subjects.list(1, null, SubjectStatus.INACTIVE, 1).get(0).subjectCode());
  }

  @Test
  void disablingCurrentTermStopsRegistrationAndPreservesRows() {
    sql("UPDATE subject_terms SET registration_enabled=TRUE WHERE subject_term_id=1");
    masterData.setTermStatus(1,1,false);
    assertFalse(masterData.getTerm(1,1).current());
    assertEquals("TERM_INACTIVE",subjects.details(1,1).subjectTerms().get(0).registrationStatus());
    assertEquals(1,scalar("SELECT COUNT(*) FROM subject_terms WHERE term_id=1"));
    masterData.setTermStatus(1,1,true);
    assertTrue(masterData.getTerm(1,1).current());
  }


  private AcademicTerm requestedTerm(long id, String code, boolean current, boolean active) {
    return new AcademicTerm(id, code, "Test Term", Instant.parse("2028-01-01T00:00:00Z"),
        Instant.parse("2028-06-01T00:00:00Z"), current, active);
  }

  @Test
  void disabledOverlapAllowedButEnabledOverlapRollsBack() {
    long first=masterData.createTerm(1,requestedTerm(0,"ACTIVE28",false,true));
    long draft=masterData.createTerm(1,requestedTerm(0,"DRAFT28",false,false));
    assertFalse(masterData.getTerm(1,draft).active());
    assertThrows(IllegalArgumentException.class,()->masterData.createTerm(1,requestedTerm(0,"OVERLAP28",false,true)));
    assertThrows(IllegalArgumentException.class,()->masterData.setTermStatus(1,draft,true));
    assertFalse(masterData.getTerm(1,draft).active());
    assertTrue(masterData.getTerm(1,first).active());
    assertEquals(0,scalar("SELECT COUNT(*) FROM academic_terms WHERE term_code='OVERLAP28'"));
  }


  @Test
  void currentCalendarRejectsExactContainedAndPartiallyOverlappingDates() {
    var current=masterData.getTerm(1,1);
    Instant start=current.startsAt(),end=current.endsAt();
    var ranges=List.of(new Instant[]{start,end},
        new Instant[]{start.plusSeconds(3600),end.minusSeconds(3600)},
        new Instant[]{start.minusSeconds(3600),end.plusSeconds(3600)},
        new Instant[]{start.minusSeconds(3600),start.plusSeconds(3600)},
        new Instant[]{end.minusSeconds(3600),end.plusSeconds(3600)});
    int index=0;
    for(var range:ranges) {
      var proposed=new AcademicTerm(0,"OVERLAP"+(index++),"Overlap",range[0],range[1],false,true);
      assertThrows(IllegalArgumentException.class,()->masterData.createTerm(1,proposed));
    }
    assertEquals(1,masterData.listTerms(1,null,null).size());
    long draft=masterData.createTerm(1,new AcademicTerm(0,"DRAFTCURRENT","Draft",start,end,false,false));
    assertFalse(masterData.getTerm(1,draft).current());
    assertThrows(IllegalArgumentException.class,()->masterData.setTermStatus(1,draft,true));
    assertFalse(masterData.getTerm(1,draft).active());
    assertTrue(masterData.getTerm(1,1).current());
  }

  @Test
  void calendarRollsOverAtBoundaryAndGapsHaveNoCurrentTerm() {
    Instant start=Instant.parse("2030-01-01T00:00:00Z"),edge=start.plusSeconds(3600);
    sql("UPDATE academic_terms SET is_current=FALSE,is_active=FALSE");
    var writer=new MasterDataService(db,Clock.fixed(start.minusSeconds(1),ZoneOffset.UTC));
    long first=writer.createTerm(1,new AcademicTerm(0,"FIRST30","First",start,edge,false,true));
    long second=writer.createTerm(1,new AcademicTerm(0,"SECOND30","Second",edge,edge.plusSeconds(3600),false,true));
    assertTrue(writer.listTerms(1,null,null).stream().noneMatch(AcademicTerm::current));
    assertTrue(new MasterDataService(db,Clock.fixed(start,ZoneOffset.UTC)).getTerm(1,first).current());
    var boundary=new MasterDataService(db,Clock.fixed(edge,ZoneOffset.UTC));
    assertFalse(boundary.getTerm(1,first).current());
    assertTrue(boundary.getTerm(1,second).current());
    var after=new MasterDataService(db,Clock.fixed(edge.plusSeconds(7200),ZoneOffset.UTC));
    assertTrue(after.listTerms(1,null,null).stream().noneMatch(AcademicTerm::current));
    assertEquals(1,scalar("SELECT COUNT(*) FROM subject_terms WHERE term_id=1"));
  }


  @Test
  void editsAndActivationCannotIntroduceOverlappingEnabledTerms() {
    var start=Instant.parse("2028-01-01T00:00:00Z");
    masterData.createTerm(1,requestedTerm(0,"ACTIVE28",false,true));
    long later=masterData.createTerm(1,new AcademicTerm(0,"LATER28","Later",start.plusSeconds(20000000),start.plusSeconds(30000000),false,true));
    var old=masterData.getTerm(1,later);
    assertThrows(IllegalArgumentException.class,()->masterData.updateTerm(1,requestedTerm(later,"LATER28",false,true)));
    assertEquals(old,masterData.getTerm(1,later));
    assertThrows(SecurityException.class,()->masterData.setTermStatus(2,later,false));
    assertTrue(masterData.getTerm(1,later).active());
  }


  @Test
  void metadataEditIgnoresSubmittedCurrentFlagAndCanDisableUsage() {
    var old=masterData.getTerm(1,1);
    masterData.updateTerm(1,new AcademicTerm(1,old.termCode(),"Updated",old.startsAt(),old.endsAt(),false,true));
    assertTrue(masterData.getTerm(1,1).current());
    masterData.updateTerm(1,new AcademicTerm(1,old.termCode(),"Disabled",old.startsAt(),old.endsAt(),true,false));
    assertFalse(masterData.getTerm(1,1).active());
    assertFalse(masterData.getTerm(1,1).current());
  }


  @Test
  void pastTermCreationRequiresDisabledUsageAndCannotBeReenabled() {
    Instant now=Instant.parse("2030-01-01T00:00:00Z");
    var service=new MasterDataService(db,Clock.fixed(now,ZoneOffset.UTC));
    int index=0;
    for(Instant end:List.of(now.minusSeconds(1),now)) {
      String code="HISTORY"+(index++);
      long count=scalar("SELECT COUNT(*) FROM academic_terms");
      var error=assertThrows(IllegalArgumentException.class,()->service.createTerm(1,
          new AcademicTerm(0,code,"History",now.minusSeconds(86400),end,false,true)));
      assertEquals("Không thể bật sử dụng học kỳ đã kết thúc.",error.getMessage());
      assertEquals(count,scalar("SELECT COUNT(*) FROM academic_terms"));
      long id=service.createTerm(1,new AcademicTerm(0,code,"History",now.minusSeconds(86400),end,false,false));
      var archived=service.getTerm(1,id);
      assertFalse(archived.active());
      assertFalse(archived.current());
      assertThrows(IllegalArgumentException.class,()->service.setTermStatus(1,id,true));
      assertThrows(IllegalArgumentException.class,()->service.updateTerm(1,
          new AcademicTerm(id,code,"Changed",archived.startsAt(),archived.endsAt(),false,false)));
      assertEquals(archived,service.getTerm(1,id));
    }
  }

  @Test
  void ongoingTermWithPastStartIsCurrentOnlyWhenExplicitlyEnabled() {
    Instant now=Instant.parse("2030-01-01T00:00:00Z");
    var service=new MasterDataService(db,Clock.fixed(now,ZoneOffset.UTC));
    long id=service.createTerm(1,new AcademicTerm(0,"ONGOING30","Ongoing",now.minusSeconds(86400),now.plusSeconds(86400),true,false));
    assertFalse(service.getTerm(1,id).current());
    service.setTermStatus(1,id,true);
    assertTrue(service.getTerm(1,id).current());
  }

  @Test
  void equalOrReversedTermDatesAreRejectedEvenForDisabledHistory() {
    Instant now=Instant.parse("2030-01-01T00:00:00Z");
    var service=new MasterDataService(db,Clock.fixed(now,ZoneOffset.UTC));
    long count=scalar("SELECT COUNT(*) FROM academic_terms");
    for(Instant end:List.of(now,now.minusSeconds(1))) {
      assertThrows(IllegalArgumentException.class,()->service.createTerm(1,
          new AcademicTerm(0,"INVALID30","Invalid",now,end,false,false)));
    }
    assertEquals(count,scalar("SELECT COUNT(*) FROM academic_terms"));
  }

  @Test
  void endedTermsAreReadOnlyAndCannotBeEnabledAgain() {
    Instant now=Instant.parse("2030-01-01T00:00:00Z");
    var ended=new MasterDataService(db,Clock.fixed(now,ZoneOffset.UTC));
    var old=ended.getTerm(1,1);
    assertFalse(old.current());
    assertThrows(IllegalArgumentException.class,()->ended.updateTerm(1,new AcademicTerm(1,old.termCode(),"Changed",old.startsAt(),old.endsAt(),false,true)));
    ended.setTermStatus(1,1,false);
    assertThrows(IllegalArgumentException.class,()->ended.setTermStatus(1,1,true));
    assertEquals(1,scalar("SELECT COUNT(*) FROM subject_terms WHERE term_id=1"));
  }


  @Test
  void enablingValidCalendarDoesNotRequireRewritingEndedLegacyTerms() {
    Instant now=Instant.now();
    sql("UPDATE academic_terms SET starts_at=?,ends_at=? WHERE term_id=1",now.minusSeconds(172800),now.minusSeconds(86400));
    long replacement=masterData.createTerm(1,new AcademicTerm(0,"REALCURRENT","Current",now.minusSeconds(259200),now.plusSeconds(86400),false,true));
    assertTrue(masterData.getTerm(1,replacement).current());
    assertFalse(masterData.getTerm(1,1).current());
    assertTrue(dashboards.currentTerm(1).contains("REALCURRENT"));
    assertEquals(1,scalar("SELECT COUNT(*) FROM subject_terms WHERE term_id=1"));
    assertEquals(1,scalar("SELECT COUNT(*) FROM academic_terms WHERE term_id=1 AND is_active=TRUE AND is_current=TRUE"));
  }

  @Test
  void currentCalendarContextDoesNotRelabelOrMoveHistoricalSubjectsAndStudentPlacement() {
    Instant now=Instant.now();
    sql("UPDATE academic_terms SET starts_at=?,ends_at=? WHERE term_id=1",now.minusSeconds(172800),now.minusSeconds(86400));
    long current=masterData.createTerm(1,new AcademicTerm(0,"NEWCURRENT","Current",now.minusSeconds(3600),now.plusSeconds(86400),false,true));
    verifiedStudent("calendar-student","calendar-student@test.edu");
    long student=scalar("SELECT user_id FROM users WHERE email='calendar-student@test.edu'");
    sql("INSERT INTO subject_registrations(student_id,subject_term_id) VALUES(?,1)",student);
    sql("INSERT INTO class_enrollments(registration_id,class_id,enrollment_method) SELECT registration_id,1,'SELF_REGISTRATION' FROM subject_registrations WHERE student_id=?",student);
    var counts=new LinkedHashMap<String,Long>();
    for(String table:List.of("academic_terms","subject_terms","subject_registrations","class_enrollments")) counts.put(table,scalar("SELECT COUNT(*) FROM "+table));
    var offeringService=new StudentSubjectService(db);
    var overviewService=new StudentDashboardService(db);
    assertEquals(current,subjects.termOptions(1).stream().filter(AcademicTerm::current).findFirst().orElseThrow().termId());
    assertEquals(current,offeringService.termOptions(student).stream().filter(AcademicTerm::current).findFirst().orElseThrow().termId());
    assertEquals(current,overviewService.termOptions(student).stream().filter(AcademicTerm::current).findFirst().orElseThrow().termId());
    assertEquals(1,subjects.details(1,1).subjectTerms().get(0).termId());
    assertEquals("TERM_ENDED",subjects.details(1,1).subjectTerms().get(0).registrationStatus());
    assertEquals("Đã kết thúc",TermTimeline.label(masterData.getTerm(1,1),now));
    assertEquals(1,scalar("SELECT COUNT(*) FROM subject_registrations r JOIN subject_terms st USING(subject_term_id) WHERE r.student_id=? AND st.term_id=1",student));
    assertEquals("FA26",overviewService.load(student).classes().get(0).termCode());
    assertEquals(0,scalar("SELECT COUNT(*) FROM subject_terms WHERE term_id=?",current));
    counts.forEach((table,count)->assertEquals(count.longValue(),scalar("SELECT COUNT(*) FROM "+table),table));
  }

  @Test
  void studentCalendarContextRejectsStaffAndMissingAccounts() {
    var offeringService=new StudentSubjectService(db);
    var overviewService=new StudentDashboardService(db);
    for(long actor:List.of(1L,2L,4L,999L)) {
      assertThrows(SecurityException.class,()->offeringService.termOptions(actor));
      assertThrows(SecurityException.class,()->overviewService.termOptions(actor));
    }
  }

  @Test
  void endedTermCannotAcceptGoogleInvitationButKeepsInvitationHistory() {
    invite("ended-token","ended-student@test.edu");
    verifiedStudent("ended-student","ended-student@test.edu");
    sql("UPDATE academic_terms SET starts_at=?,ends_at=? WHERE term_id=1",Instant.now().minusSeconds(172800),Instant.now().minusSeconds(86400));
    assertThrows(RuntimeException.class,()->google.signIn(new Identity("ended-student","ended-student@test.edu","Student"),"ended-token"));
    assertEquals(0,scalar("SELECT COUNT(*) FROM class_enrollments"));
    assertEquals(1,scalar("SELECT COUNT(*) FROM student_invitations WHERE token='ended-token' AND status='PENDING'"));
  }

  @Test
  void oldManualSwitchEndpointsNeverChangeCalendar() {
    long next=masterData.createTerm(1,requestedTerm(0,"NEXT28",false,true));
    var before=masterData.listTerms(1,null,null);
    assertThrows(SecurityException.class,()->masterData.switchCurrentTerm(2,next,1));
    assertThrows(IllegalArgumentException.class,()->masterData.switchCurrentTerm(1,next,1));
    assertThrows(IllegalArgumentException.class,()->masterData.markCurrent(1,next));
    assertEquals(before,masterData.listTerms(1,null,null));
  }


  @Test
  void expertClassScopeUsesSubjectResponsibilityAndClassAssignment() {
    sql("INSERT INTO subject_classes(subject_term_id,class_code,class_name,capacity)"
        + " VALUES(1,'G2','Group Two',20)");
    sql("INSERT INTO class_experts(class_id,subject_id,expert_id) VALUES(1,1,3)");
    assertEquals(2,subjectClasses.listAssignedClasses(2,null,null,null).size());
    var supporting=subjectClasses.listAssignedClasses(3,null,null,null);
    assertEquals(1,supporting.size());
    assertEquals("G1",supporting.get(0).classCode());
    assertEquals(0,subjectClasses.listAssignedClasses(3,null,null,"missing").size());
    assertTrue(subjectClasses.listAssignedClasses(3,2L,null,null).isEmpty());
    assertEquals("G1",subjectClasses.listAssignedClasses(3,1L,1L,null).get(0).classCode());
    assertEquals(1,subjectClasses.listAssignedSubjects(3).size());
    assertEquals(1,subjectClasses.accessibleTerms(3).size());
    assertThrows(SecurityException.class,()->subjectClasses.listAssignedSubjects(4));
    assertEquals(1,subjectClasses.listAssignedSubjects(2,"swp",ResponsibilityType.PRIMARY,SubjectStatus.ACTIVE).size());
    assertTrue(subjectClasses.listAssignedSubjects(2,"swp",ResponsibilityType.SUPPORTING,null).isEmpty());
    assertTrue(subjectClasses.listAssignedSubjects(3,"swp",null,SubjectStatus.INACTIVE).isEmpty());
    assertEquals(ResponsibilityType.SUPPORTING,subjectClasses.assignedSubject(3,1).responsibility());
    assertThrows(java.util.NoSuchElementException.class,()->subjectClasses.assignedSubject(3,2));
  }

  @Test
  void expertDashboardCountsOnlyAccessibleData() {
    sql("INSERT INTO subject_classes(subject_term_id,class_code,class_name,capacity)"
        + " VALUES(1,'G2','Group Two',20)");
    sql("INSERT INTO class_experts(class_id,subject_id,expert_id) VALUES(1,1,3)");
    sql("INSERT INTO assignment_templates(subject_id,title,instructions,maximum_score)"
        + " VALUES(1,'Template','Instructions',10)");
    var primary=dashboards.expertSummary(2);
    assertEquals(1,primary.assignedSubjects());
    assertEquals(2,primary.accessibleClasses());
    assertEquals(1,primary.visibleTemplates());
    assertEquals(1,templates.listVisible(2,null).size());
    assertEquals(0,templates.listVisible(5,null).size());
    assertTrue(subjectClasses.listAssignedSubjects(5).isEmpty());
    assertTrue(subjectClasses.listAssignedClasses(5,null,null,null).isEmpty());
    var empty=dashboards.expertSummary(5);
    assertEquals(0,empty.assignedSubjects());
    assertEquals(0,empty.accessibleClasses());
    assertEquals(0,empty.visibleTemplates());
    var supporting=dashboards.expertSummary(3);
    assertEquals(1,supporting.accessibleClasses());
    assertEquals(1,supporting.visibleTemplates());
    assertThrows(SecurityException.class,()->dashboards.expertSummary(1));
    assertThrows(SecurityException.class,()->templates.listVisible(1,null));
  }


}
