package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verify the real migration using session-local relations, never application records. */
class GroupAssignmentSchemaPostgresTest {
    private Connection connection;

    @BeforeEach
    void open() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
        connection = DBConnection.getConnection();
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TEMP TABLE users(user_id BIGINT PRIMARY KEY);
                    CREATE TEMP TABLE subject_classes(class_id BIGINT PRIMARY KEY);
                    CREATE TEMP TABLE class_enrollments(enrollment_id BIGINT PRIMARY KEY);
                    CREATE TEMP TABLE assignments(assignment_id BIGINT PRIMARY KEY);
                    CREATE TEMP TABLE project_groups(
                        group_id BIGINT PRIMARY KEY, class_id BIGINT NOT NULL,
                        UNIQUE(group_id,class_id));
                    CREATE TEMP TABLE submissions(
                        submission_id BIGINT PRIMARY KEY, assignment_id BIGINT NOT NULL REFERENCES assignments,
                        enrollment_id BIGINT NOT NULL REFERENCES class_enrollments,
                        attempt_number INTEGER NOT NULL, status TEXT NOT NULL);
                    INSERT INTO users VALUES(1),(2),(3);
                    INSERT INTO subject_classes VALUES(10);
                    INSERT INTO class_enrollments VALUES(11),(12),(13);
                    INSERT INTO assignments VALUES(100),(101);
                    INSERT INTO project_groups VALUES(20,10);
                    """);
        }
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null) connection.close();
    }

    private void migrate() throws Exception {
        String migration = Files.readString(
                Path.of("database/migrations/009-group-assignments-peer-grading.sql"))
                .replace("CREATE TABLE IF NOT EXISTS", "CREATE TEMP TABLE IF NOT EXISTS");
        try (var statement = connection.createStatement()) {
            statement.execute(migration);
        }
    }

    private void seedGroup() throws Exception {
        Jdbc.update(connection, """
                INSERT INTO assignment_group_settings(assignment_id,delivery_mode)
                VALUES(100,'GROUP'),(101,'GROUP')
                """);
        Jdbc.update(connection, """
                INSERT INTO assignment_group_targets(
                    target_id,assignment_id,class_id,group_id,group_name,owner_enrollment_id,frozen_at)
                VALUES(30,100,10,20,'Nhóm A',11,CURRENT_TIMESTAMP),
                      (31,101,10,20,'Nhóm A',11,CURRENT_TIMESTAMP)
                """);
        Jdbc.update(connection, """
                INSERT INTO assignment_group_members(target_id,student_id,enrollment_id,student_name)
                VALUES(30,1,11,'Một'),(30,2,12,'Hai'),(31,1,11,'Một')
                """);
    }

    @Test
    void visibleAssignmentNamespaceControlsFeatureAvailability() throws Exception {
        // Permanent application settings, if installed, cannot affect these temporary assignments.
        assertFalse(GroupAssignmentSchema.installed(connection));
        assertFalse(GroupAssignmentSchema.isGroup(connection, 100));
        migrate();
        assertTrue(GroupAssignmentSchema.installed(connection));
        assertFalse(GroupAssignmentSchema.isGroup(connection, 100));
        seedGroup();
        assertTrue(GroupAssignmentSchema.isGroup(connection, 100));
        assertFalse(GroupAssignmentSchema.isGroup(connection, 999));
    }

    @Test
    void migrationCanRunAgainWithoutDiscardingConfiguration() throws Exception {
        migrate();
        seedGroup();
        migrate();
        assertTrue(GroupAssignmentSchema.isGroup(connection, 100));
        assertEquals(3, Jdbc.scalar(connection, "SELECT COUNT(*) FROM assignment_group_members"));
    }

    @Test
    void sharedAttemptsBelongToExactlyOneAssignedGroupAndOneAssignment() throws Exception {
        migrate();
        seedGroup();
        Jdbc.update(connection, """
                INSERT INTO submissions VALUES(40,100,11,1,'IN_PROGRESS',30)
                """);
        assertEquals(30L, GroupAssignmentSchema.targetForSubmission(connection, 40));
        assertNull(GroupAssignmentSchema.targetForSubmission(connection, 999));

        SQLException duplicate = assertThrows(SQLException.class, () -> Jdbc.update(connection,
                "INSERT INTO submissions VALUES(41,100,12,2,'IN_PROGRESS',30)"));
        assertEquals("23505", duplicate.getSQLState());
        SQLException wrongAssignment = assertThrows(SQLException.class, () -> Jdbc.update(connection,
                "INSERT INTO submissions VALUES(42,101,12,3,'SUBMITTED',30)"));
        assertEquals("23503", wrongAssignment.getSQLState());

        Jdbc.update(connection, "UPDATE submissions SET status='SUBMITTED' WHERE submission_id=40");
        Jdbc.update(connection, "INSERT INTO submissions VALUES(41,100,11,2,'IN_PROGRESS',30)");
        assertEquals(2, Jdbc.scalar(connection,
                "SELECT COUNT(*) FROM submissions WHERE assignment_group_target_id=30"));
    }

    @Test
    void reviewsRequireDistinctFrozenMembersAndResultMatchesSubmissionGroup() throws Exception {
        migrate();
        seedGroup();
        Jdbc.update(connection, "INSERT INTO submissions VALUES(40,100,11,1,'SUBMITTED',30)");
        Jdbc.update(connection, """
                INSERT INTO assignment_peer_reviews(target_id,rater_student_id,reviewee_student_id,score)
                VALUES(30,1,2,9)
                """);
        SQLException self = assertThrows(SQLException.class, () -> Jdbc.update(connection, """
                INSERT INTO assignment_peer_reviews(target_id,rater_student_id,reviewee_student_id,score)
                VALUES(30,1,1,9)
                """));
        assertEquals("23514", self.getSQLState());
        SQLException outsider = assertThrows(SQLException.class, () -> Jdbc.update(connection, """
                INSERT INTO assignment_peer_reviews(target_id,rater_student_id,reviewee_student_id,score)
                VALUES(30,3,2,9)
                """));
        assertEquals("23503", outsider.getSQLState());

        Jdbc.update(connection, """
                INSERT INTO assignment_group_results(
                    target_id,student_id,submission_id,teacher_score,peer_score,final_score,
                    teacher_weight,peer_weight,received_reviews,eligible_reviews)
                VALUES(30,2,40,8,9,8.3,70,30,1,1)
                """);
        SQLException wrongGroup = assertThrows(SQLException.class, () -> Jdbc.update(connection, """
                INSERT INTO assignment_group_results(
                    target_id,student_id,submission_id,teacher_score,peer_score,final_score,
                    teacher_weight,peer_weight,received_reviews,eligible_reviews)
                VALUES(31,1,40,8,0,5.6,70,30,0,0)
                """));
        assertEquals("23503", wrongGroup.getSQLState());
    }
}
