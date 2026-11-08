package vn.edu.fpt.ams.service;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.dao.GroupGradeDAO;
import vn.edu.fpt.ams.dao.TeacherEvaluationDAO;
import vn.edu.fpt.ams.dao.TeacherSubmissionDAO;
import vn.edu.fpt.ams.dto.PeerReviewPage;
import vn.edu.fpt.ams.dto.SubmissionEvaluation;
import vn.edu.fpt.ams.model.ResponseType;
import vn.edu.fpt.ams.model.SubmissionStatus;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.GroupAssignmentSchema;
import vn.edu.fpt.ams.util.GroupScoreRules;
import vn.edu.fpt.ams.util.Jdbc;

/** Peer input and teacher publication share assignment-first locks and one atomic grade boundary. */
public class GroupGradeService {
    public record ReviewInput(String score,String feedback,long revision) {}
    private final Jdbc.Connections connections;
    private final GroupGradeDAO dao=new GroupGradeDAO();

    public GroupGradeService() { this(DBConnection::getConnection); }
    public GroupGradeService(Jdbc.Connections connections) { this.connections=connections; }

    public PeerReviewPage peerPage(long studentId,Long targetId,int requestedPage,int requestedReviewPage) {
        if(studentId<=0) throw new SecurityException("Cần đăng nhập tài khoản sinh viên.");
        if(requestedPage<1 || requestedReviewPage<1 || targetId!=null && targetId<=0)
            throw new IllegalArgumentException("Trang hoặc bài nhóm không hợp lệ.");
        return Jdbc.transaction(connections,c->{
            requireStudent(c,studentId);
            if(!GroupAssignmentSchema.installed(c))
                return new PeerReviewPage(List.of(),0,1,null,List.of(),1,0);
            long total=dao.countStudentTargets(c,studentId);
            int page=(int)Math.min(requestedPage,Math.max(1,(total+9)/10));
            var targets=dao.studentTargets(c,studentId,page);
            var selected=targetId==null?null:dao.studentTarget(c,studentId,targetId,false);
            long reviewTotal=selected==null?0:dao.peerCount(c,targetId,studentId);
            int reviewPage=(int)Math.min(requestedReviewPage,Math.max(1,(reviewTotal+9)/10));
            var reviews=selected==null?List.<PeerReviewPage.Review>of():dao.reviews(c,targetId,studentId,reviewPage);
            return new PeerReviewPage(targets,total,page,selected,reviews,reviewPage,reviewTotal);
        });
    }

    public void savePeerReviews(long studentId,long targetId,Map<Long,ReviewInput> input) {
        if(studentId<=0) throw new SecurityException("Cần đăng nhập tài khoản sinh viên.");
        if(targetId<=0 || input==null || input.isEmpty() || input.size()>10)
            throw new IllegalArgumentException("Nhập ít nhất một và tối đa 10 đánh giá mỗi lần.");
        Jdbc.transaction(connections,c->{
            requireStudent(c,studentId);
            if(!GroupAssignmentSchema.installed(c)) throw new IllegalStateException("Chưa cài đặt chức năng bài nhóm.");
            dao.lockStudentAssignment(c,studentId,targetId);
            var target=dao.studentTarget(c,studentId,targetId,true);
            if(!target.editable()) throw new IllegalArgumentException(target.state());
            for(var entry:input.entrySet()) {
                long peer=entry.getKey();var value=entry.getValue();
                if(peer==studentId || Jdbc.scalar(c,"SELECT count(*) FROM assignment_group_members WHERE target_id=? AND student_id=?",targetId,peer)!=1)
                    throw new SecurityException("Chỉ được đánh giá thành viên khác trong nhóm của bài tập.");
                BigDecimal score=GroupScoreRules.score(value.score(),target.maximumScore());
                String feedback=value.feedback()==null?"":value.feedback().strip();
                if(feedback.codePointCount(0,feedback.length())>2000 || value.revision()<0)
                    throw new IllegalArgumentException("Nhận xét tối đa 2000 ký tự và phiên bản phải hợp lệ.");
                Long actual=null;
                try(var p=Jdbc.statement(c,"SELECT revision FROM assignment_peer_reviews WHERE target_id=? AND rater_student_id=? AND reviewee_student_id=? FOR UPDATE",targetId,studentId,peer);var r=p.executeQuery()) {
                    if(r.next()) actual=r.getLong(1);
                }
                if(actual==null) {
                    if(value.revision()!=0) throw stale();
                    Jdbc.update(c,"""
                            INSERT INTO assignment_peer_reviews(target_id,rater_student_id,reviewee_student_id,score,feedback,revision)
                            VALUES(?,?,?,?,?,1)
                            """,targetId,studentId,peer,score,feedback);
                } else {
                    if(actual!=value.revision()) throw stale();
                    if(Jdbc.update(c,"""
                            UPDATE assignment_peer_reviews SET score=?,feedback=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP
                            WHERE target_id=? AND rater_student_id=? AND reviewee_student_id=? AND revision=?
                            """,score,feedback,targetId,studentId,peer,value.revision())!=1) throw stale();
                }
            }
            return null;
        });
    }

    /** Objective answer grading supplies an initial score; the teacher can review, publish or amend it. */
    public void objectiveGrade(long teacherId,long assignmentId,long submissionId,long revision,
            String action,String rawScore,String feedback,String reason) {
        if(teacherId<=0) throw new SecurityException("Cần đăng nhập tài khoản giảng viên.");
        if(assignmentId<=0 || submissionId<=0 || revision<0 || !Set.of("save","publish","revise","restore").contains(action==null?"":action))
            throw new IllegalArgumentException("Thao tác chấm bài nhóm không hợp lệ.");
        String cleanFeedback=feedback==null?"":feedback.strip(),cleanReason=reason==null?"":reason.strip();
        if(cleanFeedback.codePointCount(0,cleanFeedback.length())>5000 || cleanReason.codePointCount(0,cleanReason.length())>2000)
            throw new IllegalArgumentException("Nhận xét tối đa 5000 ký tự; lý do tối đa 2000 ký tự.");
        Jdbc.transaction(connections,c->{
            var assignments=new AssignmentDAO();var evaluations=new TeacherEvaluationDAO();
            if(!assignments.lockActiveTeacher(c,teacherId)) throw new SecurityException("Tài khoản giảng viên không hoạt động.");
            var assignment=assignments.lockOwnedAssignment(c,teacherId,assignmentId);
            evaluations.lockClass(c,teacherId,assignment.classId());
            evaluations.lockSubmission(c,teacherId,assignmentId,submissionId);
            if(assignment.responseType()!=ResponseType.OBJECTIVE || !GroupAssignmentSchema.isGroup(c,assignmentId)
                    || GroupAssignmentSchema.targetForSubmission(c,submissionId)==null)
                throw new IllegalArgumentException("Biểu mẫu này chỉ dành cho bài trắc nghiệm nhóm.");
            var row=new TeacherSubmissionDAO().findDetailForTeacher(c,teacherId,assignmentId,submissionId)
                    .orElseThrow(()->new NoSuchElementException("Không tìm thấy bài nhóm đã nộp."));
            var sub=row.submission();
            if(assignment.closedAt()==null && (assignment.dueAt()==null || assignments.currentTime(c).isBefore(assignment.dueAt())))
                throw new IllegalArgumentException("Chỉ chấm khi hết hạn hoặc đã khóa nhận bài.");
            dao.requireFinalSubmission(c, submissionId);
            var saved=evaluations.read(c,assignmentId,submissionId);
            if(saved.revision()!=revision) throw stale();
            boolean released=sub.status()==SubmissionStatus.RELEASED;
            if(released && (!Set.of("revise","restore").contains(action) || cleanReason.isBlank()))
                throw new IllegalArgumentException("Nhập lý do để sửa điểm đã công bố.");
            if(!released && action.equals("revise")) throw new IllegalArgumentException("Bài nhóm chưa công bố điểm.");
            BigDecimal score;String nextFeedback=cleanFeedback;
            if(action.equals("restore")) {
                try(var p=Jdbc.statement(c,"""
                        SELECT previous_score,previous_evaluation->>'feedback' FROM submission_evaluation_history
                        WHERE assignment_id=? AND submission_id=? AND previous_score IS NOT NULL
                        ORDER BY history_id DESC LIMIT 1
                        """,assignmentId,submissionId);var r=p.executeQuery()) {
                    if(!r.next()) throw new IllegalArgumentException("Chưa có điểm trước để khôi phục.");
                    score=r.getBigDecimal(1);nextFeedback=r.getString(2)==null?"":r.getString(2);
                    GroupScoreRules.score(score.toPlainString(),assignment.maximumScore());
                }
            } else score=GroupScoreRules.score(rawScore,assignment.maximumScore());
            var previous=SubmissionEvaluation.read(saved.json(),saved.revision(),sub.feedback());
            var next=new SubmissionEvaluation(revision,List.of(),nextFeedback);
            boolean publishing=released || action.equals("publish");
            evaluations.captureSnapshot(c,assignmentId,submissionId);
            int changed=Jdbc.update(c,"""
                    UPDATE submissions SET score=?,feedback=?,evaluation=CAST(? AS jsonb),graded_by=?,
                        graded_at=clock_timestamp(),status=?,released_at=CASE WHEN ? THEN clock_timestamp() ELSE NULL END,
                        evaluation_revision=evaluation_revision+1
                    WHERE submission_id=? AND evaluation_revision=? AND submitted_at IS NOT NULL
                        AND status IN ('SUBMITTED','GRADED','RELEASED')
                    """,score,nextFeedback,next.json(),teacherId,publishing?"RELEASED":"GRADED",publishing,submissionId,revision);
            if(changed!=1) throw stale();
            evaluations.journal(c,assignmentId,submissionId,row.studentId(),teacherId,
                    action.equals("restore")?"restoreEvaluation":released?"reviseReleasedEvaluation":publishing?"publishEvaluation":"completeEvaluation",
                    cleanReason,previous,next,sub.score(),score,sub.status().name(),publishing?"RELEASED":"GRADED");
            if(publishing) dao.publish(c,submissionId);
            return null;
        });
    }

    private static void requireStudent(Connection c,long id) throws SQLException {
        if(Jdbc.scalar(c,"SELECT count(*) FROM users WHERE user_id=? AND role='STUDENT' AND account_status='ACTIVE'",id)!=1)
            throw new SecurityException("Cần đăng nhập tài khoản sinh viên đang hoạt động.");
    }

    private static ConcurrentModificationException stale() {
        return new ConcurrentModificationException("Dữ liệu đã thay đổi trong phiên khác. Hãy tải lại trang trước khi lưu.");
    }
}
