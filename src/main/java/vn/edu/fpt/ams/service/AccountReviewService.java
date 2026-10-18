package vn.edu.fpt.ams.service;
import vn.edu.fpt.ams.dao.*;

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
                  && AccountReviewDAO.countExpertSubjects(c, u.userId())
                      == 0) reasons.add("No active Subject responsibility");
              if (u.role() == UserRole.TEACHER) {
                long responsibilities =
                    AccountReviewDAO.countTeacherEligibility(c, u.userId())
                        + AccountReviewDAO.countTeacherClasses(c, u.userId())
                        + AccountReviewDAO.countPendingOffers(c, u.userId());
                if (responsibilities == 0)
                  reasons.add("No active eligibility, Class or pending offer");
                if (AccountReviewDAO.countOverdueEssays(c, u.userId())
                    > 6) reasons.add("More than 6 overdue accepted Essays awaiting release");
                if (AccountReviewDAO.expiredOfferCountsByTerm(c, u.userId()).stream().anyMatch(count -> count >= 3))
                  reasons.add("At least 3 unanswered expired offers in one Term");
              }
              if (u.role() == UserRole.EXPERT) {
                if (AccountReviewDAO.overdueRequestCountsByTerm(c, u.userId()).stream().anyMatch(count -> count >= 3))
                  reasons.add("At least 3 overdue Requests in one Term under Primary responsibility");
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
    AccountReviewDAO.recordEssayBreaches(c);
    AccountReviewDAO.recordRequestBreaches(c);
  }
}
