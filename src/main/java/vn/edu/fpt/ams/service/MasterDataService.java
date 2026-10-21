package vn.edu.fpt.ams.service;

import java.sql.*;
import java.time.Instant;
import java.time.Clock;
import java.util.*;
import vn.edu.fpt.ams.dao.MasterDataDAO;
import vn.edu.fpt.ams.model.AcademicTerm;
import vn.edu.fpt.ams.util.*;

/** UC10-12 Academic Term administration. */
public class MasterDataService {
  private final Jdbc.Connections connections;
  private final MasterDataDAO dao;
  private final Clock clock;

  public MasterDataService() {
    this(DBConnection::getConnection);
  }

  public MasterDataService(Jdbc.Connections source) {
    this(source, Clock.systemUTC());
  }

  public MasterDataService(Jdbc.Connections source, Clock clock) {
    connections = source;
    this.clock = Objects.requireNonNull(clock);
    dao = new MasterDataDAO(clock);
  }

  public List<AcademicTerm> listTerms(long actor, String keyword, Boolean active) {
    validateSearch(keyword);
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          return dao.findTerms(c, keyword, active);
        });
  }

  public AcademicTerm getTerm(long actor, long termId) {
    requirePositive(termId);
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          return requireTerm(c, termId, false);
        });
  }

  public long createTerm(long actor, AcademicTerm requested) {
    AcademicTerm term = validated(requested, 0);
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          dao.lockTermWrites(c);
          validateSchedule(c, term);
          if (dao.findTermByCode(c, term.termCode(), false).isPresent())
            throw new IllegalArgumentException("Mã học kỳ đã tồn tại.");
          long id = dao.insertTerm(c, new AcademicTerm(0, term.termCode(), term.termName(),
              term.startsAt(), term.endsAt(), false, term.active()));
          return id;
        });
  }

  public void updateTerm(long actor, AcademicTerm requested) {
    AcademicTerm term = validated(requested, requested == null ? 0 : requested.termId());
    Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          dao.lockTermWrites(c);
          AcademicTerm old = requireTerm(c, term.termId(), true);
          if (!old.endsAt().isAfter(clock.instant()))
            throw new IllegalArgumentException("Học kỳ đã kết thúc; chỉ được xem lịch sử.");
          Optional<AcademicTerm> duplicate = dao.findTermByCode(c, term.termCode(), false);
          if (duplicate.isPresent() && duplicate.get().termId() != term.termId())
            throw new IllegalArgumentException("Mã học kỳ đã tồn tại.");
          boolean startDateChanged = !sameLocalDate(old.startsAt(), term.startsAt());
          boolean endDateChanged = !sameLocalDate(old.endsAt(), term.endsAt());
          boolean datesChanged = startDateChanged || endDateChanged;
          if (datesChanged
              && Jdbc.scalar(c, "SELECT COUNT(*) FROM subject_terms WHERE term_id=?", term.termId()) > 0)
            throw new IllegalArgumentException(
                "Không thể thay đổi ngày học kỳ khi đã có cấu hình môn học cho học kỳ này.");
          AcademicTerm saved = new AcademicTerm(term.termId(), term.termCode(), term.termName(),
              startDateChanged ? term.startsAt() : old.startsAt(),
              endDateChanged ? term.endsAt() : old.endsAt(), false, term.active());
          validateSchedule(c, saved);
          dao.updateTerm(c, saved);
          return null;
        });
  }

  private static boolean sameLocalDate(Instant first, Instant second) {
    return first.atZone(TermTimeline.ZONE).toLocalDate()
        .equals(second.atZone(TermTimeline.ZONE).toLocalDate());
  }

  /** Legacy entry point: a caller cannot override the calendar. */
  public void markCurrent(long actor, long termId) {
    rejectManualSwitch(actor);
  }

  private void rejectManualSwitch(long actor) {
    Jdbc.transaction(connections, c -> {
      Access.admin(c, actor);
      throw new IllegalArgumentException("Học kỳ hiện tại được xác định tự động theo ngày bắt đầu và kết thúc.");
    });
  }

  public void setTermStatus(long actor, long termId, boolean active) {
    requirePositive(termId);
    Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          dao.lockTermWrites(c);
          AcademicTerm term = requireTerm(c, termId, true);
          if (term.active() == active)
            throw new IllegalArgumentException("Học kỳ đã ở trạng thái này.");
          validateSchedule(c, new AcademicTerm(term.termId(), term.termCode(), term.termName(),
              term.startsAt(), term.endsAt(), false, active));
          dao.updateTermStatus(c, termId, active);
          return null;
        });
  }

  /** Kept only so old POSTs fail with an explanatory message instead of changing data. */
  public void switchCurrentTerm(long actor, long targetId, long expectedActiveId) {
    rejectManualSwitch(actor);
  }

  private void validateSchedule(Connection c, AcademicTerm term) throws SQLException {
    if (!term.active()) return;
    if (!term.endsAt().isAfter(clock.instant()))
      throw new IllegalArgumentException("Không thể bật sử dụng học kỳ đã kết thúc.");
    // The transaction advisory lock serializes create/update/enable, including empty calendars.
    if (Jdbc.scalar(c, "SELECT COUNT(*) FROM academic_terms WHERE is_active=TRUE AND term_id<>?"
            + " AND ends_at>? AND starts_at<? AND ends_at>?", term.termId(), clock.instant(), term.endsAt(), term.startsAt()) > 0)
      throw new IllegalArgumentException("Lịch học kỳ trùng với một học kỳ được sử dụng. Vui lòng kiểm tra ngày bắt đầu và kết thúc.");
  }

  private AcademicTerm requireTerm(Connection c, long id, boolean lock) throws SQLException {
    return dao.findTermById(c, id, lock)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy học kỳ."));
  }

  private static AcademicTerm validated(AcademicTerm value, long id) {
    if (value == null) throw new IllegalArgumentException("Vui lòng nhập thông tin học kỳ.");
    if (value.termCode() == null || value.termCode().isBlank())
      throw new IllegalArgumentException("Vui lòng nhập mã học kỳ.");
    if (value.termName() == null || value.termName().isBlank())
      throw new IllegalArgumentException("Vui lòng nhập tên học kỳ.");
    String code = value.termCode().trim().toUpperCase(Locale.ROOT);
    String name = value.termName().trim();
    if (code.length() > 20) throw new IllegalArgumentException("Mã học kỳ không được quá 20 ký tự.");
    if (code.chars().allMatch(Character::isDigit))
      throw new IllegalArgumentException("Mã học kỳ phải chứa ít nhất một ký tự không phải số.");
    if (name.length() > 100) throw new IllegalArgumentException("Tên học kỳ không được quá 100 ký tự.");
    if (value.startsAt() == null || value.endsAt() == null)
      throw new IllegalArgumentException("Vui lòng nhập ngày bắt đầu và ngày kết thúc hợp lệ.");
    if (!value.endsAt().isAfter(value.startsAt()))
      throw new IllegalArgumentException("Ngày kết thúc phải sau ngày bắt đầu.");
    return new AcademicTerm(id, code, name, value.startsAt(), value.endsAt(), false, value.active());
  }

  private static void validateSearch(String keyword) {
    if (keyword != null && keyword.length() > 50)
      throw new IllegalArgumentException("Từ khóa tìm kiếm không được quá 50 ký tự.");
  }

  private static void requirePositive(long id) {
    if (id <= 0) throw new IllegalArgumentException("Mã học kỳ không hợp lệ.");
  }
}
