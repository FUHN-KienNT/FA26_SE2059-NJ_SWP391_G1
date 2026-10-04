package vn.edu.fpt.ams.dao;

import java.sql.*;
import java.util.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

/** SQL for Subject Expert responsibility rows. */
public class SubjectExpertDAO {
  public List<SubjectExpert> findBySubjectId(long subjectId) {
    return Jdbc.transaction(DBConnection::getConnection, c -> findBySubjectId(c, subjectId, false));
  }

  public List<SubjectExpert> findBySubjectId(Connection c, long subjectId, boolean lock)
      throws SQLException {
    String sql = "SELECT * FROM subject_experts WHERE subject_id=? ORDER BY subject_expert_id"
        + (lock ? " FOR UPDATE" : "");
    try (var p = Jdbc.statement(c, sql, subjectId); var r = p.executeQuery()) {
      var result = new ArrayList<SubjectExpert>();
      while (r.next()) result.add(mapSubjectExpert(r));
      return result;
    }
  }

  /** Reactivates an existing pair without replacing its stable identity or assigned_at value. */
  public void assign(long subjectId, long expertId, ResponsibilityType type) {
    Jdbc.transaction(DBConnection::getConnection,
        c -> { assign(c, subjectId, expertId, type); return null; });
  }

  public void assign(Connection c, long subjectId, long expertId, ResponsibilityType type)
      throws SQLException {
    int updated = Jdbc.update(c,
        "UPDATE subject_experts SET responsibility_type=?,is_active=TRUE,deactivated_at=NULL"
            + " WHERE subject_id=? AND expert_id=?",
        type.name(), subjectId, expertId);
    if (updated == 0)
      Jdbc.update(c,
          "INSERT INTO subject_experts(subject_id,expert_id,responsibility_type) VALUES(?,?,?)",
          subjectId, expertId, type.name());
  }

  public void updateResponsibility(long subjectExpertId, ResponsibilityType type) {
    Jdbc.transaction(DBConnection::getConnection,
        c -> { updateResponsibility(c, subjectExpertId, type); return null; });
  }

  public void updateResponsibility(Connection c, long subjectExpertId, ResponsibilityType type)
      throws SQLException {
    Jdbc.update(c,
        "UPDATE subject_experts SET responsibility_type=? WHERE subject_expert_id=? AND is_active=TRUE",
        type.name(), subjectExpertId);
  }

  public void deactivate(long subjectId, long expertId) {
    Jdbc.transaction(DBConnection::getConnection,
        c -> { deactivate(c, subjectId, expertId); return null; });
  }

  public void deactivate(Connection c, long subjectId, long expertId) throws SQLException {
    Jdbc.update(c,
        "UPDATE subject_experts SET is_active=FALSE,deactivated_at=CURRENT_TIMESTAMP"
            + " WHERE subject_id=? AND expert_id=? AND is_active=TRUE",
        subjectId, expertId);
  }

  static SubjectExpert mapSubjectExpert(ResultSet rs) throws SQLException {
    return new SubjectExpert(rs.getLong("subject_expert_id"), rs.getLong("subject_id"),
        rs.getLong("expert_id"),
        ResponsibilityType.valueOf(rs.getString("responsibility_type")),
        rs.getBoolean("is_active"), ResultSetSupport.instant(rs, "assigned_at"),
        ResultSetSupport.instant(rs, "deactivated_at"));
  }
}
