package vn.edu.fpt.ams.dao;

import java.sql.*;
import java.util.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

/** Subject SQL only; SubjectService owns authorization, validation and transactions. */
public class SubjectDAO {
  public List<Subject> findAll(String keyword, SubjectStatus status) {
    return Jdbc.transaction(DBConnection::getConnection, c -> search(c, keyword, status, 1, Integer.MAX_VALUE));
  }

  public List<Subject> search(Connection c, String keyword, SubjectStatus status, int page, int size)
      throws SQLException {
    return search(c, keyword, status, null, page, size);
  }

  public List<Subject> search(Connection c, String keyword, SubjectStatus status, Long termId, int page, int size)
      throws SQLException {
    String sql = "SELECT * FROM subjects WHERE (lower(subject_code) LIKE ? OR lower(subject_name) LIKE ?)";
    var args = new ArrayList<Object>();
    String pattern = "%" + normalizeSearch(keyword) + "%";
    args.add(pattern);
    args.add(pattern);
    if (status != null) {
      sql += " AND status=?";
      args.add(status.name());
    }
    if (termId != null) {
      sql += " AND EXISTS(SELECT 1 FROM subject_terms st WHERE st.subject_id=subjects.subject_id AND st.term_id=?)";
      args.add(termId);
    }
    sql += " ORDER BY subject_code LIMIT ? OFFSET ?";
    args.add(size);
    args.add(Math.multiplyExact(Math.max(1, page) - 1, size));
    try (var p = Jdbc.statement(c, sql, args.toArray()); var r = p.executeQuery()) {
      var result = new ArrayList<Subject>();
      while (r.next()) result.add(mapSubject(r));
      return result;
    }
  }

  public long count(Connection c, String keyword, SubjectStatus status) throws SQLException {
    return count(c, keyword, status, null);
  }

  public long count(Connection c, String keyword, SubjectStatus status, Long termId) throws SQLException {
    String sql = "SELECT COUNT(*) FROM subjects WHERE (lower(subject_code) LIKE ? OR lower(subject_name) LIKE ?)";
    var args = new ArrayList<Object>();
    String pattern = "%" + normalizeSearch(keyword) + "%";
    args.add(pattern);
    args.add(pattern);
    if (status != null) {
      sql += " AND status=?";
      args.add(status.name());
    }
    if (termId != null) {
      sql += " AND EXISTS(SELECT 1 FROM subject_terms st WHERE st.subject_id=subjects.subject_id AND st.term_id=?)";
      args.add(termId);
    }
    return Jdbc.scalar(c, sql, args.toArray());
  }

  public Optional<Subject> findById(long id) {
    return Jdbc.transaction(DBConnection::getConnection, c -> findById(c, id, false));
  }

  public Optional<Subject> findById(Connection c, long id, boolean lock) throws SQLException {
    try (var p = Jdbc.statement(c, "SELECT * FROM subjects WHERE subject_id=?" + (lock ? " FOR UPDATE" : ""), id);
        var r = p.executeQuery()) {
      return r.next() ? Optional.of(mapSubject(r)) : Optional.empty();
    }
  }

  public Optional<Subject> findByCode(String code) {
    return Jdbc.transaction(DBConnection::getConnection, c -> findByCode(c, code));
  }

  public Optional<Subject> findByCode(Connection c, String code) throws SQLException {
    try (var p = Jdbc.statement(c, "SELECT * FROM subjects WHERE lower(subject_code)=lower(?)", code);
        var r = p.executeQuery()) {
      return r.next() ? Optional.of(mapSubject(r)) : Optional.empty();
    }
  }

  public long insert(Subject subject) {
    return Jdbc.transaction(DBConnection::getConnection, c -> insert(c, subject));
  }

  public long insert(Connection c, Subject subject) throws SQLException {
    return Jdbc.insert(c,
        "INSERT INTO subjects(subject_code,subject_name,description,status) VALUES(?,?,?,?)",
        subject.subjectCode(), subject.subjectName(), subject.description(), subject.status().name());
  }

  public void update(Subject subject) {
    Jdbc.transaction(DBConnection::getConnection, c -> { update(c, subject); return null; });
  }

  public void update(Connection c, Subject subject) throws SQLException {
    Jdbc.update(c, "UPDATE subjects SET subject_name=?,description=? WHERE subject_id=?",
        subject.subjectName(), subject.description(), subject.subjectId());
  }

  public void updateStatus(long id, SubjectStatus status) {
    Jdbc.transaction(DBConnection::getConnection, c -> { updateStatus(c, id, status); return null; });
  }

  public void updateStatus(Connection c, long id, SubjectStatus status) throws SQLException {
    Jdbc.update(c, "UPDATE subjects SET status=? WHERE subject_id=?", status.name(), id);
  }

  public List<TeacherSubject> findTeacherSubjects(long teacherId, Boolean active) {
    throw new UnsupportedOperationException("TODO: implement Teacher eligibility workflow");
  }
  public long assignTeacherToSubject(long teacherId, long subjectId) {
    throw new UnsupportedOperationException("TODO: implement Teacher eligibility workflow");
  }
  public void deactivateTeacherSubject(long teacherSubjectId) {
    throw new UnsupportedOperationException("TODO: implement Teacher eligibility workflow");
  }

  private static String normalizeSearch(String keyword) {
    return keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
  }

  static Subject mapSubject(ResultSet rs) throws SQLException {
    return new Subject(rs.getLong("subject_id"), rs.getString("subject_code"),
        rs.getString("subject_name"), rs.getString("description"),
        SubjectStatus.valueOf(rs.getString("status")));
  }

  static TeacherSubject mapTeacherSubject(ResultSet rs) throws SQLException {
    return new TeacherSubject(rs.getLong("teacher_subject_id"), rs.getLong("teacher_id"),
        rs.getLong("subject_id"), rs.getBoolean("is_active"),
        ResultSetSupport.instant(rs, "assigned_at"),
        ResultSetSupport.instant(rs, "deactivated_at"));
  }
}
