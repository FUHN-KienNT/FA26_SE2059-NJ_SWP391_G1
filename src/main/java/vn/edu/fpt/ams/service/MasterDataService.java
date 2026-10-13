package vn.edu.fpt.ams.service;

import java.sql.*;
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
          dao.lockTermWrites(c);
          validateFlags(c, term, 0);
          if (dao.findTermByCode(c, term.termCode(), false).isPresent())
            throw new IllegalArgumentException("Academic Term code already exists");
          long id = dao.insertTerm(c, new AcademicTerm(0, term.termCode(), term.termName(),
              term.startsAt(), term.endsAt(), false, term.active()));
          if (term.current()) dao.markCurrentTerm(c, id);
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
          validateFlags(c, term, term.termId());
          if (old.current() && !term.active())
            throw new IllegalArgumentException("Học kỳ hiện tại phải ở trạng thái hoạt động.");
          Optional<AcademicTerm> duplicate = dao.findTermByCode(c, term.termCode(), false);
          if (duplicate.isPresent() && duplicate.get().termId() != term.termId())
            throw new IllegalArgumentException("Academic Term code already exists");
          boolean datesChanged =
              !old.startsAt().equals(term.startsAt()) || !old.endsAt().equals(term.endsAt());
          if (datesChanged
              && Jdbc.scalar(c, "SELECT COUNT(*) FROM subject_terms WHERE term_id=?", term.termId()) > 0)
            throw new IllegalArgumentException(
                "Academic Term dates cannot change after Subject Term data exists");
          dao.updateTerm(
              c,
              new AcademicTerm(
                  term.termId(), term.termCode(), term.termName(), term.startsAt(), term.endsAt(),
                  term.current(), term.active()));
          return null;
        });
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
            throw new IllegalArgumentException("Academic Term already has this status");
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
        .orElseThrow(() -> new NoSuchElementException("Academic Term not found"));
  }

  private static AcademicTerm validated(AcademicTerm value, long id, boolean current) {
    if (value == null) throw new IllegalArgumentException("Academic Term is required");
    String code = ValidationUtil.required(value.termCode(), "Term code").toUpperCase(Locale.ROOT);
    String name = ValidationUtil.required(value.termName(), "Term name");
    if (code.length() > 20) throw new IllegalArgumentException("Term code maximum is 20 characters");
    if (code.chars().allMatch(Character::isDigit))
      throw new IllegalArgumentException("Term code must contain a non-digit character");
    if (name.length() > 100) throw new IllegalArgumentException("Term name maximum is 100 characters");
    if (current && !value.active())
      throw new IllegalArgumentException("Học kỳ hiện tại phải ở trạng thái hoạt động.");
    ValidationUtil.laterThan(value.endsAt(), value.startsAt(), "Term end");
    return new AcademicTerm(id, code, name, value.startsAt(), value.endsAt(), current, value.active());
  }

  private static void validateSearch(String keyword) {
    if (keyword != null && keyword.length() > 50)
      throw new IllegalArgumentException("Search maximum is 50 characters");
  }

  private static void requirePositive(long id) {
    if (id <= 0) throw new IllegalArgumentException("A positive Academic Term ID is required");
  }
}
