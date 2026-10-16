package vn.edu.fpt.ams.service;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.StudentObjectivePage;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

/** Assignment-row locks serialize starting/submitting with Teacher cancellation and restoration. */
public class StudentObjectiveService {
    public record SaveResult(boolean finished, Instant serverTime, Instant deadline, long revision) { }
    @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
    private final StudentObjectiveDAO dao;
    private final AssignmentDAO assignments;
    private final Connections connections;
    public StudentObjectiveService() { this(new StudentObjectiveDAO(),new AssignmentDAO(),DBConnection::getConnection); }
    public StudentObjectiveService(StudentObjectiveDAO dao,AssignmentDAO assignments,Connections connections) {
        this.dao=dao;this.assignments=assignments;this.connections=connections;
    }
    public List<StudentObjectiveDAO.Owned> list(long studentId) {
        actor(studentId);return transaction(c->dao.list(c,studentId));
    }
    public StudentObjectivePage page(long studentId,long assignmentId,long submissionId) {
        actor(studentId);
        return transaction(c->{
            var owned=dao.owned(c,studentId,assignmentId,true);
            Instant now=assignments.currentTime(c);
            if(submissionId==0) return page(owned,null,List.of(),now,0);
            var attempt=attempt(dao.attempts(c,studentId,assignmentId),submissionId);
            if(attempt.status()==SubmissionStatus.IN_PROGRESS && !now.isBefore(deadline(owned.assignment(),attempt))) {
                finishSaved(c,owned.assignment(),attempt,now);
                attempt=attempt(dao.attempts(c,studentId,assignmentId),submissionId);
            }
            var questions=attempt.status()==SubmissionStatus.IN_PROGRESS
                    ? ObjectiveAnswerGrader.display(dao.snapshotQuestions(c,submissionId)) : List.<StudentObjectivePage.Item>of();
            return page(owned,attempt,questions,now,dao.revision(c,submissionId));
        });
    }
    public long start(long studentId,long assignmentId) {
        actor(studentId);
        return transaction(c->{
            var owned=dao.owned(c,studentId,assignmentId,true);
            Instant now=assignments.currentTime(c);
            List<Submission> attempts=dao.attempts(c,studentId,assignmentId);
            for(var attempt:attempts) if(attempt.status()==SubmissionStatus.IN_PROGRESS) {
                if(!now.isBefore(deadline(owned.assignment(),attempt))) finishSaved(c,owned.assignment(),attempt,now);
                return attempt.submissionId();
            }
            requireOpen(owned.assignment(),now);
            Assignment a=owned.assignment();
            int limit=a.assignmentPurpose()==AssignmentPurpose.PRACTICE
                    ? (a.attemptLimit()==null?0:a.attemptLimit()) : 1;
            long used=attempts.stream().filter(s->s.status()!=SubmissionStatus.VOID).count();
            if(used>=limit) throw new IllegalArgumentException("Bạn đã sử dụng hết số lượt làm bài.");
            var questions=assignments.findAllDraftQuestions(c,assignmentId,false);
            ObjectiveAnswerGrader.display(questions); // Reject a broken answer key before starting the timer.
            var total=questions.stream().map(Question::maximumPoints).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
            if(total.compareTo(a.maximumScore())!=0) throw new IllegalStateException("Tổng điểm các câu hỏi không bằng điểm tối đa.");
            Instant deadline=a.dueAt();
            if(a.timeLimitMinutes()!=null) {
                Instant timed=now.plusSeconds(a.timeLimitMinutes()*60L);
                if(timed.isBefore(deadline)) deadline=timed;
            }
            int number=attempts.stream().mapToInt(Submission::attemptNumber).max().orElse(0)+1;
            return dao.start(c,owned,number,now,deadline);
        });
    }
    public long submit(long studentId,long assignmentId,long submissionId,long expectedRevision,Map<Long,List<String>> selected) {
        actor(studentId);
        return transaction(c->{
            var owned=dao.owned(c,studentId,assignmentId,true);
            var attempt=attempt(dao.attempts(c,studentId,assignmentId),submissionId);
            if(attempt.status()!=SubmissionStatus.IN_PROGRESS) return submissionId; // Double-submit cannot overwrite work.
            Instant now=assignments.currentTime(c);
            if(!now.isBefore(deadline(owned.assignment(),attempt))) {
                // A late request cannot replace answers already saved before the deadline.
                finishSaved(c,owned.assignment(),attempt,now);return submissionId;
            }
            requireOpen(owned.assignment(),now);
            checkRevision(c,submissionId,expectedRevision);
            var grade=ObjectiveAnswerGrader.grade(dao.snapshotQuestions(c,submissionId),selected);
            dao.submit(c,submissionId,now,now,grade);
            return submissionId;
        });
    }
    public SaveResult save(long studentId,long assignmentId,long submissionId,long expectedRevision,Map<Long,List<String>> selected) {
        actor(studentId);
        return transaction(c->{
            var owned=dao.owned(c,studentId,assignmentId,true);
            var attempt=attempt(dao.attempts(c,studentId,assignmentId),submissionId);
            Instant now=assignments.currentTime(c), deadline=deadline(owned.assignment(),attempt);
            if(attempt.status()!=SubmissionStatus.IN_PROGRESS) return new SaveResult(true,now,deadline,dao.revision(c,submissionId));
            if(!now.isBefore(deadline)) {
                finishSaved(c,owned.assignment(),attempt,now);
                return new SaveResult(true,now,deadline,dao.revision(c,submissionId));
            }
            requireOpen(owned.assignment(),now);
            checkRevision(c,submissionId,expectedRevision);
            dao.save(c,submissionId,expectedRevision,ObjectiveAnswerGrader.draft(dao.snapshotQuestions(c,submissionId),selected));
            return new SaveResult(false,now,deadline,expectedRevision+1);
        });
    }
    private void checkRevision(Connection c,long submissionId,long expected) throws SQLException {
        if(expected<0 || expected!=dao.revision(c,submissionId))
            throw new ConcurrentModificationException("Đáp án đã được cập nhật ở phiên khác. Vui lòng tải lại trang trước khi tiếp tục.");
    }

    /** Called on startup and periodically, including when the student's browser is closed. */
    public int finishOverdue() {
        List<Long> ids=transaction(dao::overdueAssignments);
        int completed=0;
        for(long assignmentId:ids) {
            try { completed+=transaction(c->{
                var assignment=dao.lockPublished(c,assignmentId);
                if(assignment.isEmpty()) return 0; // Cancellation won the lock; do not touch VOID work.
                int count=0;
                for(var attempt:dao.inProgress(c,assignmentId)) {
                    Instant now=assignments.currentTime(c);
                    if(!now.isBefore(deadline(assignment.get(),attempt))) {
                        var checkpoint=c.setSavepoint();
                        try { finishSaved(c,assignment.get(),attempt,now);count++; }
                        catch(SQLException|RuntimeException ex) {
                            c.rollback(checkpoint);
                            java.util.logging.Logger.getLogger(StudentObjectiveService.class.getName())
                                    .log(java.util.logging.Level.WARNING,"Không tự nộp được lượt làm bài "+attempt.submissionId(),ex);
                        } finally { c.releaseSavepoint(checkpoint); }
                    }
                }
                return count;
            }); } catch(RuntimeException ex) {
                // A database problem for one assignment must not block all other deadlines.
                java.util.logging.Logger.getLogger(StudentObjectiveService.class.getName())
                        .log(java.util.logging.Level.WARNING,"Không tự nộp được bài tập "+assignmentId,ex);
            }
        }
        return completed;
    }
    static Instant deadline(Assignment assignment,Submission attempt) {
        return assignment.dueAt()!=null && assignment.dueAt().isBefore(attempt.deadlineAt())
                ? assignment.dueAt() : attempt.deadlineAt();
    }
    private void finishSaved(Connection c,Assignment assignment,Submission attempt,Instant now) throws SQLException {
        if(assignment.responseType()==ResponseType.ESSAY) {
            dao.submitSavedWork(c,attempt.submissionId(),deadline(assignment,attempt));
            return;
        }
        // Load outside the validation catch: a database failure must roll back and retry.
        var questions=dao.snapshotQuestions(c,attempt.submissionId());
        ObjectiveAnswerGrader.Result grade;
        try { grade=ObjectiveAnswerGrader.grade(questions,ObjectiveAnswerGrader.selections(attempt.answer())); }
        catch(IllegalStateException|IllegalArgumentException invalidSavedWork) {
            // Legacy/malformed data still reaches a final submitted state without losing student work.
            dao.submitSavedWork(c,attempt.submissionId(),deadline(assignment,attempt));
            return;
        }
        dao.submit(c,attempt.submissionId(),deadline(assignment,attempt),now,grade);
    }
    private static void requireOpen(Assignment a,Instant now) {
        if(a.availableFrom()==null || now.isBefore(a.availableFrom()) || a.dueAt()==null || !now.isBefore(a.dueAt()))
            throw new IllegalArgumentException("Bài tập chưa mở hoặc đã hết hạn nộp.");
    }
    private static Submission attempt(List<Submission> attempts,long id) {
        return attempts.stream().filter(s->s.submissionId()==id).findFirst()
                .orElseThrow(()->new NoSuchElementException("Không tìm thấy lượt làm bài hoặc bạn không có quyền truy cập."));
    }
    private static StudentObjectivePage page(StudentObjectiveDAO.Owned owned,Submission attempt,List<StudentObjectivePage.Item> questions,Instant now,long revision) {
        return new StudentObjectivePage(owned.assignment(),owned.classCode(),attempt==null?0:attempt.submissionId(),
                attempt==null?0:attempt.attemptNumber(),attempt==null?null:attempt.status(),attempt==null?null:deadline(owned.assignment(),attempt),questions,
                now,attempt==null||attempt.status()!=SubmissionStatus.IN_PROGRESS?Map.of():ObjectiveAnswerGrader.selections(attempt.answer()),revision);
    }
    private static void actor(long id) { if(id<=0) throw new SecurityException("Vui lòng đăng nhập bằng tài khoản sinh viên đang hoạt động."); }
    @FunctionalInterface private interface Work<T> { T run(Connection c) throws SQLException; }
    private <T> T transaction(Work<T> work) {
        try(var c=connections.open()) {
            c.setAutoCommit(false);
            try { T result=work.run(c);c.commit();return result; }
            catch(SQLException|RuntimeException ex) {
                try { c.rollback(); } catch(SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        } catch(SQLException ex) { throw new IllegalStateException("Không tải hoặc lưu được bài tập. Vui lòng thử lại.",ex); }
    }
}
