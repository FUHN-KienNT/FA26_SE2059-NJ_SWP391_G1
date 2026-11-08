package vn.edu.fpt.ams.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import vn.edu.fpt.ams.dto.GroupGradePage;
import vn.edu.fpt.ams.dto.PeerReviewPage;
import vn.edu.fpt.ams.util.GroupAssignmentSchema;
import vn.edu.fpt.ams.util.GroupScoreRules;
import vn.edu.fpt.ams.util.Jdbc;

/** Group grade queries participate in the caller's assignment-leading transaction. */
public final class GroupGradeDAO {
    public GroupGradePage teacherDetail(Connection c,long submissionId) throws SQLException {
        Long targetId=GroupAssignmentSchema.targetForSubmission(c,submissionId);
        if(targetId==null) return null;
        String name; BigDecimal teacher,peer; long revision; Instant closed; boolean open;
        try(var p=Jdbc.statement(c,"""
                SELECT t.group_name,g.teacher_weight,g.peer_weight,s.evaluation_revision,t.peer_closed_at,
                    ((a.closed_at IS NOT NULL OR a.due_at<=CURRENT_TIMESTAMP)
                        AND NOT EXISTS (SELECT 1 FROM submissions pending
                            WHERE pending.assignment_group_target_id=t.target_id
                                AND pending.status='IN_PROGRESS')) AS grading_open
                FROM assignment_group_targets t JOIN assignment_group_settings g USING(assignment_id)
                JOIN assignments a USING(assignment_id)
                JOIN submissions s ON s.assignment_group_target_id=t.target_id
                WHERE s.submission_id=?
                """,submissionId); var r=p.executeQuery()) {
            if(!r.next()) return null;
            name=r.getString(1);teacher=r.getBigDecimal(2);peer=r.getBigDecimal(3);revision=r.getLong(4);
            closed=r.getTimestamp(5)==null?null:r.getTimestamp(5).toInstant();open=r.getBoolean(6);
        }
        var members=new ArrayList<GroupGradePage.Member>();
        try(var p=Jdbc.statement(c,"""
                SELECT m.student_id,m.student_name,COALESCE(avg(pr.score),0) AS peer_score,
                    COALESCE(sum(pr.score),0) AS peer_sum,
                    count(pr.rater_student_id) AS received,
                    (SELECT count(*)-1 FROM assignment_group_members all_m WHERE all_m.target_id=m.target_id) AS eligible,
                    result.final_score
                FROM assignment_group_members m
                LEFT JOIN assignment_peer_reviews pr ON pr.target_id=m.target_id AND pr.reviewee_student_id=m.student_id
                LEFT JOIN assignment_group_results result ON result.target_id=m.target_id AND result.student_id=m.student_id
                WHERE m.target_id=?
                GROUP BY m.target_id,m.student_id,m.student_name,result.final_score
                ORDER BY m.student_name,m.student_id
                """,targetId); var r=p.executeQuery()) {
            while(r.next()) members.add(new GroupGradePage.Member(r.getLong(1),r.getString(2),
                    r.getBigDecimal(3),r.getBigDecimal(4),r.getInt(5),r.getInt(6),r.getBigDecimal(7)));
        }
        return new GroupGradePage(targetId,name,teacher,peer,revision,open,closed,members);
    }

    /** Call after taking the assignment lock so a retry cannot replace a graded final. */
    public void requireFinalSubmission(Connection c, long submissionId) throws SQLException {
        Long targetId = GroupAssignmentSchema.targetForSubmission(c, submissionId);
        if (targetId != null && Jdbc.scalar(c, """
                SELECT count(*) FROM submissions
                WHERE assignment_group_target_id=? AND status='IN_PROGRESS'
                """, targetId) > 0)
            throw new IllegalArgumentException(
                    "Nhóm còn lượt làm mới đang chờ được tiếp nhận. Hãy tải lại sau khi lượt mới được nộp tự động để chấm đúng bản cuối.");
    }

    /** Freeze peer input and write every member's result atomically with teacher publication. */
    public void publish(Connection c,long submissionId) throws SQLException {
        Long targetId=GroupAssignmentSchema.targetForSubmission(c,submissionId);
        if(targetId==null) return;
        BigDecimal score,maximum,teacherWeight,peerWeight;
        Instant releasedAt;
        try(var p=Jdbc.statement(c,"""
                SELECT s.score,a.maximum_score,g.teacher_weight,g.peer_weight,s.released_at
                FROM submissions s JOIN assignments a USING(assignment_id)
                JOIN assignment_group_settings g USING(assignment_id)
                JOIN assignment_group_targets t ON t.target_id=s.assignment_group_target_id
                WHERE s.submission_id=? AND s.status='RELEASED' AND s.submitted_at IS NOT NULL
                  AND t.frozen_at IS NOT NULL
                FOR UPDATE OF t
                """,submissionId);var r=p.executeQuery()) {
            if(!r.next()) throw new IllegalArgumentException("Bài nhóm chưa sẵn sàng công bố.");
            score=r.getBigDecimal(1);maximum=r.getBigDecimal(2);teacherWeight=r.getBigDecimal(3);peerWeight=r.getBigDecimal(4);
            releasedAt=r.getTimestamp(5).toInstant();
        }
        if(score==null || score.signum()<0 || score.compareTo(maximum)>0)
            throw new IllegalArgumentException("Điểm bài nhóm không hợp lệ.");
        var page=teacherDetail(c,submissionId);
        if(page.members().isEmpty()) throw new IllegalArgumentException("Bài nhóm không có thành viên được giao.");
        for(var member:page.members()) {
            BigDecimal finalScore=GroupScoreRules.finalScoreFromTotals(score,member.peerSum(),
                    member.receivedReviews(),teacherWeight,peerWeight);
            Jdbc.update(c,"""
                    INSERT INTO assignment_group_results(target_id,student_id,submission_id,teacher_score,peer_score,
                        final_score,teacher_weight,peer_weight,received_reviews,eligible_reviews,released_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(target_id,student_id) DO UPDATE SET
                        submission_id=EXCLUDED.submission_id,teacher_score=EXCLUDED.teacher_score,
                        peer_score=EXCLUDED.peer_score,final_score=EXCLUDED.final_score,
                        teacher_weight=EXCLUDED.teacher_weight,peer_weight=EXCLUDED.peer_weight,
                        received_reviews=EXCLUDED.received_reviews,eligible_reviews=EXCLUDED.eligible_reviews,
                        released_at=EXCLUDED.released_at
                    """,targetId,member.studentId(),submissionId,score,member.peerScore(),finalScore,
                    teacherWeight,peerWeight,member.receivedReviews(),member.eligibleReviews(),releasedAt);
        }
        Jdbc.update(c,"UPDATE assignment_group_targets SET peer_closed_at=COALESCE(peer_closed_at,?) WHERE target_id=?",
                releasedAt,targetId);
    }

    private static final String STUDENT_TARGETS="""
            FROM assignment_group_targets t JOIN assignments a USING(assignment_id)
            JOIN assignment_group_settings g USING(assignment_id)
            JOIN subject_classes c ON c.class_id=a.class_id
            JOIN assignment_group_members me ON me.target_id=t.target_id
            JOIN users actor ON actor.user_id=me.student_id
            JOIN class_enrollments e ON e.enrollment_id=me.enrollment_id AND e.class_id=a.class_id
            JOIN subject_registrations r ON r.registration_id=e.registration_id AND r.student_id=me.student_id
                AND r.subject_term_id=c.subject_term_id
            WHERE me.student_id=? AND actor.role='STUDENT' AND actor.account_status='ACTIVE'
                AND e.status='ACTIVE' AND r.status='ACTIVE' AND c.status='ACTIVE'
                AND a.status='PUBLISHED' AND g.delivery_mode='GROUP' AND t.frozen_at IS NOT NULL
            """;
    private static final String TARGET_COLUMNS="""
            SELECT t.target_id,a.assignment_id,a.title,t.group_name,c.class_code,a.maximum_score,
                g.teacher_weight,g.peer_weight,
                (t.peer_closed_at IS NULL AND (a.closed_at IS NOT NULL OR a.due_at<=CURRENT_TIMESTAMP)
                    AND EXISTS(SELECT 1 FROM submissions s WHERE s.assignment_group_target_id=t.target_id
                        AND s.submitted_at IS NOT NULL AND s.status IN ('SUBMITTED','GRADED','RELEASED'))) AS editable,
                t.peer_closed_at,(a.closed_at IS NOT NULL OR a.due_at<=CURRENT_TIMESTAMP) AS cutoff_reached
            """;

    public long countStudentTargets(Connection c,long studentId) throws SQLException {
        return Jdbc.scalar(c,"SELECT count(*) "+STUDENT_TARGETS,studentId);
    }

    public List<PeerReviewPage.Target> studentTargets(Connection c,long studentId,int page) throws SQLException {
        var out=new ArrayList<PeerReviewPage.Target>();
        try(var p=Jdbc.statement(c,TARGET_COLUMNS+STUDENT_TARGETS+" ORDER BY a.assignment_id DESC,t.target_id DESC LIMIT 10 OFFSET ?",
                studentId,((long)page-1)*10);var r=p.executeQuery()) {
            while(r.next()) out.add(target(r));
        }
        return out;
    }

    public PeerReviewPage.Target studentTarget(Connection c,long studentId,long targetId,boolean lock) throws SQLException {
        try(var p=Jdbc.statement(c,TARGET_COLUMNS+STUDENT_TARGETS+" AND t.target_id=?"+
                (lock?" FOR UPDATE OF t":""),studentId,targetId);var r=p.executeQuery()) {
            if(!r.next()) throw new java.util.NoSuchElementException("Không tìm thấy bài nhóm hoặc bạn không có quyền đánh giá.");
            return target(r);
        }
    }

    /** Acquire only the leading assignment lock before looking up/locking a writable target. */
    public void lockStudentAssignment(Connection c,long studentId,long targetId) throws SQLException {
        try(var p=Jdbc.statement(c,"SELECT a.assignment_id "+STUDENT_TARGETS+
                " AND t.target_id=? FOR UPDATE OF a",studentId,targetId);var r=p.executeQuery()) {
            if(!r.next()) throw new java.util.NoSuchElementException("Không tìm thấy bài nhóm hoặc bạn không có quyền đánh giá.");
        }
    }

    private static PeerReviewPage.Target target(java.sql.ResultSet r) throws SQLException {
        boolean editable=r.getBoolean("editable");
        String state=r.getTimestamp("peer_closed_at")!=null?"Đã công bố điểm, đánh giá đã khóa."
                :!r.getBoolean("cutoff_reached")?"Chưa mở đánh giá: chờ hết hạn hoặc giáo viên khóa nhận bài."
                :!editable?"Nhóm chưa có bài nộp được tiếp nhận.":"Đang mở đánh giá đồng đội.";
        return new PeerReviewPage.Target(r.getLong("target_id"),r.getLong("assignment_id"),r.getString("title"),
                r.getString("group_name"),r.getString("class_code"),r.getBigDecimal("maximum_score"),
                r.getBigDecimal("teacher_weight"),r.getBigDecimal("peer_weight"),editable,state);
    }

    public List<PeerReviewPage.Review> reviews(Connection c,long targetId,long studentId,int page) throws SQLException {
        var out=new ArrayList<PeerReviewPage.Review>();
        try(var p=Jdbc.statement(c,"""
                SELECT m.student_id,m.student_name,p.score,p.feedback,COALESCE(p.revision,0)
                FROM assignment_group_members m
                LEFT JOIN assignment_peer_reviews p ON p.target_id=m.target_id
                    AND p.reviewee_student_id=m.student_id AND p.rater_student_id=?
                WHERE m.target_id=? AND m.student_id<>?
                ORDER BY m.student_name,m.student_id LIMIT 10 OFFSET ?
                """,studentId,targetId,studentId,((long)page-1)*10);var r=p.executeQuery()) {
            while(r.next()) out.add(new PeerReviewPage.Review(r.getLong(1),r.getString(2),r.getBigDecimal(3),
                    r.getString(4)==null?"":r.getString(4),r.getLong(5)));
        }
        return out;
    }

    public long peerCount(Connection c,long targetId,long studentId) throws SQLException {
        return Jdbc.scalar(c,"SELECT count(*) FROM assignment_group_members WHERE target_id=? AND student_id<>?",targetId,studentId);
    }
}
