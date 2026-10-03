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
          if (dao.findTermByCode(c, term.termCode(), false).isPresent())
            throw new IllegalArgumentException("Academic Term code already exists");
          long id = dao.insertTerm(c, new AcademicTerm(0, term.termCode(), term.termName(),
              term.startsAt(), term.endsAt(), false, term.active()));
          if (term.current()) dao.markCurrentTerm(c, id);
          return id;
        });
  }

  public void updateTerm(long actor, AcademicTerm requested) {
    AcademicTerm term = validated(requested, requested == null ? 0 : requested.termId(), false);
    Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          AcademicTerm old = requireTerm(c, term.termId(), true);
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
                  old.current(), old.active()));
          return null;
        });
  }

  public void markCurrent(long actor, long termId) {
    requirePositive(termId);
    Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          AcademicTerm term = requireTerm(c, termId, true);
          if (!term.active()) throw new IllegalArgumentException("Current Academic Term must be active");
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
          AcademicTerm term = requireTerm(c, termId, true);
          if (term.active() == active)
            throw new IllegalArgumentException("Academic Term already has this status");
          if (!active
              && term.current()
              && Jdbc.scalar(
                      c,
                      "SELECT COUNT(*) FROM subject_terms WHERE term_id=? AND registration_enabled=TRUE"
                          + " AND CURRENT_TIMESTAMP>=registration_opens_at"
                          + " AND CURRENT_TIMESTAMP<registration_closes_at",
                      termId)
                  > 0)
            throw new IllegalArgumentException(
                "Current Academic Term cannot be deactivated while registration is open");
          dao.updateTermStatus(c, termId, active);
          return null;
        });
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
      throw new IllegalArgumentException("Current Academic Term must be active");
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
