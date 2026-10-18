package vn.edu.fpt.ams.service;

import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import vn.edu.fpt.ams.dao.MasterDataDAO;
import vn.edu.fpt.ams.model.AcademicTerm;
import vn.edu.fpt.ams.util.*;

/** UC10-12 Academic Term administration. */
public class MasterDataService {
  private final Jdbc.Connections connections;
  private final MasterDataDAO dao = new MasterDataDAO();

  public MasterDataService() {
    this(DBConnection::getConnection);
  }

  public MasterDataService(Jdbc.Connections source) {
    connections = source;
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
    AcademicTerm term = validated(requested, 0, requested != null && requested.current());
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          if (term.current())
            throw new IllegalArgumentException("Tạo học kỳ không tự đặt học kỳ hiện tại. Vui lòng dùng thao tác Đặt hiện tại sau khi tạo.");
          dao.lockTermWrites(c);
          validateFlags(c, term, 0);
          if (dao.findTermByCode(c, term.termCode(), false).isPresent())
            throw new IllegalArgumentException("Mã học kỳ đã tồn tại.");
          long id = dao.insertTerm(c, new AcademicTerm(0, term.termCode(), term.termName(),
              term.startsAt(), term.endsAt(), false, term.active()));
          return id;
        });
  }

  public void updateTerm(long actor, AcademicTerm requested) {
    AcademicTerm term = validated(requested, requested == null ? 0 : requested.termId(), requested != null && requested.current());
    Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          dao.lockTermWrites(c);
          AcademicTerm old = requireTerm(c, term.termId(), true);
          if (old.current() != term.current() || old.active() != term.active())
            throw new IllegalArgumentException("Chỉ thay đổi trạng thái học kỳ bằng thao tác riêng.");
          validateFlags(c, term, term.termId());
          if (old.current() && !term.active())
            throw new IllegalArgumentException("Học kỳ hiện tại phải ở trạng thái hoạt động.");
          Optional<AcademicTerm> duplicate = dao.findTermByCode(c, term.termCode(), false);
          if (duplicate.isPresent() && duplicate.get().termId() != term.termId())
            throw new IllegalArgumentException("Mã học kỳ đã tồn tại.");
          boolean startDateChanged = !sameUtcDate(old.startsAt(), term.startsAt());
          boolean endDateChanged = !sameUtcDate(old.endsAt(), term.endsAt());
          boolean datesChanged = startDateChanged || endDateChanged;
          if (datesChanged
              && Jdbc.scalar(c, "SELECT COUNT(*) FROM subject_terms WHERE term_id=?", term.termId()) > 0)
            throw new IllegalArgumentException(
                "Không thể thay đổi ngày học kỳ khi đã có cấu hình môn học cho học kỳ này.");
          dao.updateTerm(
              c,
              new AcademicTerm(
                  term.termId(), term.termCode(), term.termName(),
                  startDateChanged ? term.startsAt() : old.startsAt(),
                  endDateChanged ? term.endsAt() : old.endsAt(),
                  term.current(), term.active()));
          return null;
        });
  }

  private static boolean sameUtcDate(Instant first, Instant second) {
    return first.atZone(ZoneOffset.UTC).toLocalDate()
        .equals(second.atZone(ZoneOffset.UTC).toLocalDate());
  }

  public void markCurrent(long actor, long termId) {
    requirePositive(termId);
    Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          dao.lockTermWrites(c);
          AcademicTerm term = requireTerm(c, termId, true);
          if (!term.active()) throw new IllegalArgumentException("Học kỳ hiện tại phải ở trạng thái hoạt động.");
          validateFlags(c, new AcademicTerm(termId, term.termCode(), term.termName(),
              term.startsAt(), term.endsAt(), true, true), termId);
          dao.markCurrentTerm(c, termId);
          return null;
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
          if (!active && term.current())
            throw new IllegalArgumentException("Học kỳ hiện tại phải ở trạng thái hoạt động.");
          if (active && dao.existsActiveTermExcluding(c, termId))
            throw new IllegalArgumentException("Chỉ được phép có một học kỳ hoạt động tại một thời điểm.");
          dao.updateTermStatus(c, termId, active);
          return null;
        });
  }

  /** Explicitly replace the active term after the Admin reviews both terms. */
  public void switchCurrentTerm(long actor, long targetId, long expectedActiveId) {
    requirePositive(targetId);
    if (expectedActiveId <= 0 || targetId == expectedActiveId)
      throw new IllegalArgumentException("Chọn một học kỳ khác để chuyển.");
    Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          dao.lockTermWrites(c);
          AcademicTerm target = requireTerm(c, targetId, true);
          List<AcademicTerm> activeTerms = dao.findTerms(c, null, true);
          if (activeTerms.size() != 1 || activeTerms.get(0).termId() != expectedActiveId)
            throw new IllegalArgumentException(
                "Học kỳ hoạt động đã thay đổi. Vui lòng xem lại trước khi chuyển.");
          if (target.active())
            throw new IllegalArgumentException("Học kỳ được chọn đã hoạt động.");
          dao.updateTermStatus(c, expectedActiveId, false);
          dao.updateTermStatus(c, targetId, true);
          dao.markCurrentTerm(c, targetId);
          return null;
        });
  }

  private void validateFlags(Connection c, AcademicTerm term, long excluding) throws SQLException {
    if (term.active() && dao.existsActiveTermExcluding(c, excluding))
      throw new IllegalArgumentException("Chỉ được phép có một học kỳ hoạt động tại một thời điểm.");
    if (term.current() && dao.existsCurrentTermExcluding(c, excluding))
      throw new IllegalArgumentException("Chỉ được phép có một học kỳ hiện tại tại một thời điểm.");
  }

  private AcademicTerm requireTerm(Connection c, long id, boolean lock) throws SQLException {
    return dao.findTermById(c, id, lock)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy học kỳ."));
  }

  private static AcademicTerm validated(AcademicTerm value, long id, boolean current) {
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
    if (current && !value.active())
      throw new IllegalArgumentException("Học kỳ hiện tại phải ở trạng thái hoạt động.");
    if (value.startsAt() == null || value.endsAt() == null)
      throw new IllegalArgumentException("Vui lòng nhập ngày bắt đầu và ngày kết thúc hợp lệ.");
    if (!value.endsAt().isAfter(value.startsAt()))
      throw new IllegalArgumentException("Ngày kết thúc phải sau ngày bắt đầu.");
    return new AcademicTerm(id, code, name, value.startsAt(), value.endsAt(), current, value.active());
  }

  private static void validateSearch(String keyword) {
    if (keyword != null && keyword.length() > 50)
      throw new IllegalArgumentException("Từ khóa tìm kiếm không được quá 50 ký tự.");
  }

  private static void requirePositive(long id) {
    if (id <= 0) throw new IllegalArgumentException("Mã học kỳ không hợp lệ.");
  }
}
