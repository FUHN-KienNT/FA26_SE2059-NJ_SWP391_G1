package vn.edu.fpt.ams.dao;

import java.sql.*;
import java.util.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

/** SQL for the Subject aggregate's Academic Term configurations. */
public class SubjectTermDAO {
  public record TermConfiguration(SubjectTerm configuration, AcademicTerm academicTerm) {}

  /** Load all offerings for one result page in one read, including historical terms. */
  public List<TermConfiguration> findBySubjects(Connection c, List<Long> subjectIds) throws SQLException {
    if (subjectIds.isEmpty()) return List.of();
    String placeholders = String.join(",", Collections.nCopies(subjectIds.size(), "?"));
    String sql = "SELECT st.*,t.term_code,t.term_name,t.starts_at,t.ends_at,CASE WHEN t.is_active=TRUE AND t.starts_at<=CURRENT_TIMESTAMP AND t.ends_at>CURRENT_TIMESTAMP THEN TRUE ELSE FALSE END AS is_current,t.is_active"
        + " FROM subject_terms st JOIN academic_terms t ON t.term_id=st.term_id"
        + " WHERE st.subject_id IN (" + placeholders + ")"
        + " ORDER BY is_current DESC,t.starts_at DESC,t.term_code,st.subject_term_id";
    try (var p = Jdbc.statement(c, sql, subjectIds.toArray()); var r = p.executeQuery()) {
      var result = new ArrayList<TermConfiguration>();
      while (r.next()) result.add(new TermConfiguration(mapSubjectTerm(r),
          new AcademicTerm(r.getLong("term_id"), r.getString("term_code"), r.getString("term_name"),
              ResultSetSupport.instant(r, "starts_at"), ResultSetSupport.instant(r, "ends_at"),
              r.getBoolean("is_current"), r.getBoolean("is_active"))));
      return result;
    }
  }

  public Optional<SubjectTerm> findBySubjectAndTerm(long subjectId, long termId) {
    return Jdbc.transaction(DBConnection::getConnection,
        c -> findBySubjectAndTerm(c, subjectId, termId, false));
  }

  public Optional<SubjectTerm> findBySubjectAndTerm(
      Connection c, long subjectId, long termId, boolean lock) throws SQLException {
    String sql = "SELECT * FROM subject_terms WHERE subject_id=? AND term_id=?"
        + (lock ? " FOR UPDATE" : "");
    try (var p = Jdbc.statement(c, sql, subjectId, termId); var r = p.executeQuery()) {
      return r.next() ? Optional.of(mapSubjectTerm(r)) : Optional.empty();
    }
  }

  public Optional<SubjectTerm> findById(long subjectTermId) {
    return Jdbc.transaction(DBConnection::getConnection, c -> {
      try (var p = Jdbc.statement(c,
              "SELECT * FROM subject_terms WHERE subject_term_id=?", subjectTermId);
          var r = p.executeQuery()) {
        return r.next() ? Optional.of(mapSubjectTerm(r)) : Optional.empty();
      }
    });
  }

  public List<SubjectTerm> findBySubject(Connection c, long subjectId) throws SQLException {
    try (var p = Jdbc.statement(c,
            "SELECT * FROM subject_terms WHERE subject_id=? ORDER BY term_id DESC", subjectId);
        var r = p.executeQuery()) {
      var result = new ArrayList<SubjectTerm>();
      while (r.next()) result.add(mapSubjectTerm(r));
      return result;
    }
  }

  public long insert(SubjectTerm term) {
    return Jdbc.transaction(DBConnection::getConnection, c -> insert(c, term));
  }

  public long insert(Connection c, SubjectTerm term) throws SQLException {
    return Jdbc.insert(c,
        "INSERT INTO subject_terms(subject_id,term_id,registration_enabled,"
            + "registration_opens_at,registration_closes_at) VALUES(?,?,?,?,?)",
        term.subjectId(), term.termId(), term.registrationEnabled(),
        term.registrationOpensAt(), term.registrationClosesAt());
  }

  public void update(SubjectTerm term) {
    Jdbc.transaction(DBConnection::getConnection, c -> { update(c, term); return null; });
  }

  public void update(Connection c, SubjectTerm term) throws SQLException {
    Jdbc.update(c,
        "UPDATE subject_terms SET registration_enabled=?,registration_opens_at=?,"
            + "registration_closes_at=? WHERE subject_term_id=?",
        term.registrationEnabled(), term.registrationOpensAt(),
        term.registrationClosesAt(), term.subjectTermId());
  }

  static SubjectTerm mapSubjectTerm(ResultSet rs) throws SQLException {
    return new SubjectTerm(rs.getLong("subject_term_id"), rs.getLong("subject_id"),
        rs.getLong("term_id"), rs.getBoolean("registration_enabled"),
        ResultSetSupport.instant(rs, "registration_opens_at"),
        ResultSetSupport.instant(rs, "registration_closes_at"));
  }
}
