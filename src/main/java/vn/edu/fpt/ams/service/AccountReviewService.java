package vn.edu.fpt.ams.service;

import java.time.*;
import java.util.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

/** Review indicators with immutable deadline evidence derived from responsibility intervals. */
public class AccountReviewService {
  private final Jdbc.Connections connections;

  public AccountReviewService() {
    this(DBConnection::getConnection);
  }

  public AccountReviewService(Jdbc.Connections source) {
    connections = source;
  }

  public Map<Long, List<String>> reasons(long actor, List<UserAccount> users) {
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          recordBreaches(c);
          var out = new HashMap<Long, List<String>>();
          Instant now = Instant.now();
          for (var u : users) {
            var reasons = new ArrayList<String>();
            if (u.role() == UserRole.TEACHER || u.role() == UserRole.EXPERT) {
              Instant seen = u.lastLoginAt() == null ? u.createdAt() : u.lastLoginAt();
              if (seen != null && !seen.plus(Duration.ofDays(90)).isAfter(now))
                reasons.add("No successful sign-in for at least 90 days");
              if (u.role() == UserRole.EXPERT
                  && Jdbc.scalar(
                          c,
                          "SELECT COUNT(*) FROM subject_experts WHERE expert_id=? AND"
                              + " is_active=TRUE",
                          u.userId())
                      == 0) reasons.add("No active Subject responsibility");
              if (u.role() == UserRole.TEACHER) {
                long responsibilities =
                    Jdbc.scalar(
                            c,
                            "SELECT COUNT(*) FROM teacher_subjects WHERE teacher_id=? AND"
                                + " is_active=TRUE",
                            u.userId())
                        + Jdbc.scalar(
                            c,
                            "SELECT COUNT(*) FROM subject_classes WHERE teacher_id=? AND"
                                + " status='ACTIVE'",
                            u.userId())
                        + Jdbc.scalar(
                            c,
                            "SELECT COUNT(*) FROM additional_class_assignment_offers WHERE"
                                + " teacher_id=? AND status='PENDING' AND"
                                + " expires_at>CURRENT_TIMESTAMP",
                            u.userId());
                if (responsibilities == 0)
                  reasons.add("No active eligibility, Class or pending offer");
                if (Jdbc.scalar(
                        c,
                        "SELECT COUNT(DISTINCT b.item_id) FROM responsibility_deadline_breaches b"
                            + " JOIN submissions s ON s.submission_id=b.item_id WHERE"
                            + " b.breach_type='ESSAY' AND b.user_id=? AND s.status IN"
                            + " ('SUBMITTED','GRADED') AND s.released_at IS NULL",
                        u.userId())
                    > 6) reasons.add("More than 6 overdue accepted Essays awaiting release");
                try (var p =
                        Jdbc.statement(
                            c,
                            "SELECT st.term_id,COUNT(*) FROM additional_class_assignment_offers o"
                                + " JOIN additional_class_requests q ON q.request_id=o.request_id"
                                + " JOIN overflow_batches b ON"
                                + " b.overflow_batch_id=q.overflow_batch_id JOIN subject_classes sc"
                                + " ON sc.class_id=b.source_class_id JOIN subject_terms st ON"
                                + " st.subject_term_id=sc.subject_term_id WHERE o.teacher_id=? AND"
                                + " (o.status='EXPIRED' OR (o.status='PENDING' AND"
                                + " o.expires_at<=CURRENT_TIMESTAMP)) GROUP BY st.term_id",
                            u.userId());
                    var r = p.executeQuery()) {
                  while (r.next())
                    if (r.getLong(2) >= 3) {
                      reasons.add("At least 3 unanswered expired offers in one Term");
                      break;
                    }
                }
              }
              if (u.role() == UserRole.EXPERT) {
                try (var p =
                        Jdbc.statement(
                            c,
                            "SELECT term_id,COUNT(DISTINCT item_id) FROM"
                                + " responsibility_deadline_breaches WHERE breach_type='REQUEST'"
                                + " AND user_id=? GROUP BY term_id",
                            u.userId());
                    var r = p.executeQuery()) {
                  while (r.next())
                    if (r.getLong(2) >= 3) {
                      reasons.add(
                          "At least 3 overdue Requests in one Term under Primary responsibility");
                      break;
                    }
                }
              }
            }
            out.put(u.userId(), List.copyOf(reasons));
          }
          return out;
        });
  }

  private static void recordBreaches(java.sql.Connection c) throws java.sql.SQLException {
    // A closed interval still owns deadlines inside it. Later reassignment/review cannot erase
    // evidence.
    Jdbc.update(
        c,
        "INSERT INTO"
            + " responsibility_deadline_breaches(breach_type,item_id,user_id,term_id,deadline_at)"
            + " SELECT DISTINCT 'ESSAY',s.submission_id,p.user_id,st.term_id,a.due_at+INTERVAL '7'"
            + " DAY FROM submissions s JOIN assignments a ON a.assignment_id=s.assignment_id JOIN"
            + " subject_classes sc ON sc.class_id=a.class_id JOIN subject_terms st ON"
            + " st.subject_term_id=sc.subject_term_id JOIN responsibility_periods p ON"
            + " p.class_id=sc.class_id AND p.responsibility_type='TEACHER' WHERE"
            + " a.response_type='ESSAY' AND s.status IN ('SUBMITTED','GRADED','RELEASED') AND"
            + " s.submitted_at IS NOT NULL AND CURRENT_TIMESTAMP>a.due_at+INTERVAL '7' DAY AND"
            + " (s.released_at IS NULL OR s.released_at>a.due_at+INTERVAL '7' DAY) AND"
            + " p.started_at<=a.due_at+INTERVAL '7' DAY AND (p.ended_at IS NULL OR"
            + " p.ended_at>a.due_at+INTERVAL '7' DAY) AND NOT EXISTS(SELECT 1 FROM"
            + " responsibility_deadline_breaches b WHERE b.breach_type='ESSAY' AND"
            + " b.item_id=s.submission_id AND b.user_id=p.user_id) ON CONFLICT DO NOTHING");
    Jdbc.update(
        c,
        "INSERT INTO"
            + " responsibility_deadline_breaches(breach_type,item_id,user_id,term_id,deadline_at)"
            + " SELECT DISTINCT 'REQUEST',q.request_id,p.user_id,st.term_id,q.review_due_at FROM"
            + " additional_class_requests q JOIN overflow_batches ob ON"
            + " ob.overflow_batch_id=q.overflow_batch_id JOIN subject_classes sc ON"
            + " sc.class_id=ob.source_class_id JOIN subject_terms st ON"
            + " st.subject_term_id=sc.subject_term_id JOIN responsibility_periods p ON"
            + " p.subject_id=st.subject_id AND p.responsibility_type='PRIMARY' WHERE"
            + " CURRENT_TIMESTAMP>=q.review_due_at AND (q.reviewed_at IS NULL OR"
            + " q.reviewed_at>q.review_due_at) AND p.started_at<=q.review_due_at AND (p.ended_at IS"
            + " NULL OR p.ended_at>q.review_due_at) AND NOT EXISTS(SELECT 1 FROM"
            + " responsibility_deadline_breaches b WHERE b.breach_type='REQUEST' AND"
            + " b.item_id=q.request_id AND b.user_id=p.user_id) ON CONFLICT DO NOTHING");
  }
}
