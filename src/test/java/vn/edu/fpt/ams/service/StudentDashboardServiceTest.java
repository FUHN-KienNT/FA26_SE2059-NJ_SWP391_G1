package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dao.StudentDashboardDAO;
import vn.edu.fpt.ams.util.Jdbc;

class StudentDashboardServiceTest {
    private Jdbc.Connections db;
    private StudentDashboardService service;

    @BeforeEach void setup() throws Exception {
        String url = "jdbc:h2:mem:student_dashboard_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        db = () -> DriverManager.getConnection(url);
        try (var c = db.open()) { StudentDashboardFixture.create(c, false); }
        service = new StudentDashboardService(db);
    }

    @Test void summarizesOnlyOwnActiveMembershipAndPublishedAssignments() {
        var view = service.load(1);
        assertEquals("Student One", view.fullName());
        assertEquals(1, view.subjectCount());
        assertEquals(1, view.classCount());
        assertEquals(4, view.assignmentCount());
        assertEquals(5, view.submittedAttemptCount());
        assertEquals(List.of("OWN_CLASS"), view.classes().stream().map(x -> x.classCode()).toList());
        assertEquals(List.of("Open Objective", "Open Essay"), view.openAssignments().stream().map(x -> x.title()).toList());
        assertTrue(view.openAssignments().get(0).hasAssignmentPage());
        assertFalse(view.openAssignments().get(1).hasAssignmentPage());
        assertTrue(view.openAssignments().stream().allMatch(x -> x.dueAt().isAfter(view.asOf())));
    }

    @Test void releasedResultsPreserveHistoryAndHideUnreleasedForeignAndFutureScores() {
        var view = service.load(1);
        assertEquals(2, view.releasedResultCount());
        assertEquals(List.of("Historical Released", "Past"), view.results().stream().map(x -> x.title()).toList());
        assertEquals(List.of(9, 7), view.results().stream().map(x -> x.score().intValueExact()).toList());
        assertTrue(view.results().stream().allMatch(x -> !x.releasedAt().isAfter(view.asOf())));
    }

    @Test void anotherStudentGetsOnlyTheirOwnOverview() {
        var other = service.load(2);
        assertEquals("Student Two", other.fullName());
        assertEquals(1, other.subjectCount());
        assertEquals(1, other.classCount());
        assertEquals(1, other.assignmentCount());
        assertEquals(1, other.submittedAttemptCount());
        assertEquals(List.of("Foreign Work"), other.openAssignments().stream().map(x -> x.title()).toList());
        assertEquals(1, other.results().size());
    }

    @Test void newStudentHasRealZeroCountsAndEmptySections() {
        var empty = service.load(5);
        assertEquals(0, empty.subjectCount());
        assertEquals(0, empty.classCount());
        assertEquals(0, empty.assignmentCount());
        assertEquals(0, empty.submittedAttemptCount());
        assertEquals(0, empty.releasedResultCount());
        assertTrue(empty.classes().isEmpty());
        assertTrue(empty.openAssignments().isEmpty());
        assertTrue(empty.results().isEmpty());
    }

    @Test void rejectsInactiveMissingAndNonStudentAccountsAtBusinessLayer() {
        for (long id : new long[] {0, -1, 3, 4, 6, 7, 999})
            assertThrows(SecurityException.class, () -> service.load(id), "actor=" + id);
    }

    @Test void previewLimitsDoNotTruncateOverviewTotals() throws Exception {
        var now = Instant.now();
        try (var c = db.open()) {
            for (int i = 0; i < 8; i++) {
                Jdbc.update(c, "INSERT INTO subjects VALUES (?,?,'Another Subject')", 500 + i, "SUBJECT_" + i);
                Jdbc.update(c, "INSERT INTO subject_terms VALUES (?,?,11)", 600 + i, 500 + i);
                Jdbc.update(c, "INSERT INTO subject_classes VALUES (?,?,?,3,'ACTIVE')", 700 + i, 600 + i, "CLASS_" + i);
                Jdbc.update(c, "INSERT INTO subject_registrations VALUES (?,1,?,'ACTIVE')", 800 + i, 600 + i);
                Jdbc.update(c, "INSERT INTO class_enrollments VALUES (?,?,?,'ACTIVE')", 900 + i, 800 + i, 700 + i);
                StudentDashboardFixture.assignment(c, 1000 + i, 10, "Extra Open " + i, "PUBLISHED", "OBJECTIVE",
                        now.minusSeconds(3600), now.plusSeconds(100 + i));
                Jdbc.update(c, "INSERT INTO submissions VALUES (?,103,11,?,'RELEASED',6,?)",
                        2000 + i, 5 + i, now.minusSeconds(10 + i));
            }
        }
        var view = service.load(1);
        assertEquals(9, view.subjectCount());
        assertEquals(9, view.classCount());
        assertEquals(12, view.assignmentCount());
        assertEquals(10, view.releasedResultCount());
        assertEquals(StudentDashboardDAO.PREVIEW_LIMIT, view.classes().size());
        assertEquals(StudentDashboardDAO.PREVIEW_LIMIT, view.openAssignments().size());
        assertEquals(StudentDashboardDAO.PREVIEW_LIMIT, view.results().size());
        assertEquals("Extra Open 0", view.openAssignments().get(0).title());
    }

    @Test void loadingTheDashboardDoesNotChangeAcademicOrAccountRecords() throws Exception {
        String before;
        try (var c = db.open()) { before = StudentDashboardFixture.snapshot(c); }
        service.load(1);
        service.load(1);
        try (var c = db.open()) { assertEquals(before, StudentDashboardFixture.snapshot(c)); }
    }
}
