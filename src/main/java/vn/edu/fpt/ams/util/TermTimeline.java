package vn.edu.fpt.ams.util;

import java.time.Instant;
import java.time.ZoneId;

/** Calendar rules shared by term administration and offering views. End is exclusive. */
public final class TermTimeline {
  public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private TermTimeline() {}

  public static boolean current(Instant start, Instant end, boolean enabled, Instant now) {
    return enabled && !now.isBefore(start) && now.isBefore(end);
  }

  public static String phase(Instant start, Instant end, Instant now) {
    if (!now.isBefore(end)) return "Đã kết thúc";
    // Enabled current terms are labeled separately by the view.
    // A disabled term whose dates include today must not appear to be running.
    return now.isBefore(start) ? "Sắp tới" : "—";
  }
  /** Calendar lifecycle for an offering, independent of Subject or enrollment status. */
  public static String label(vn.edu.fpt.ams.model.AcademicTerm term, Instant now) {
    if (term == null) return "";
    if (!now.isBefore(term.endsAt())) return "Đã kết thúc";
    if (!term.active()) return "Đã tắt";
    return current(term.startsAt(), term.endsAt(), true, now) ? "Hiện tại" : "Sắp tới";
  }

}
