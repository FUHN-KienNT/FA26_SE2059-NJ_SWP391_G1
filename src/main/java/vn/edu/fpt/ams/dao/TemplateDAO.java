package vn.edu.fpt.ams.dao;

import java.sql.ResultSet;
import java.sql.Connection;
import java.util.*;
import java.sql.SQLException;
import vn.edu.fpt.ams.model.*;
import java.sql.SQLException;
import vn.edu.fpt.ams.dto.ExpertTemplateDTO;
import vn.edu.fpt.ams.util.Jdbc;

/** JDBC persistence; TemplateService owns permissions and preserves Assignment snapshots. */
public class TemplateDAO {
  private static String searchClause(TemplateStatus status) {
    return " WHERE subject_id=? AND lower(title) LIKE ? ESCAPE '!'"
        + (status == null ? "" : " AND status=?");
  }

  private static Object[] searchArguments(long subject, String keyword, TemplateStatus status) {
    var values = new ArrayList<Object>();
    values.add(subject);
    values.add("%" + keyword.toLowerCase(Locale.ROOT).replace("!", "!!")
        .replace("%", "!%").replace("_", "!_") + "%");
    if (status != null) values.add(status.name());
    return values.toArray();
  }

  public long countMatching(Connection c, long subject, String keyword, TemplateStatus status) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM assignment_templates" + searchClause(status),
        searchArguments(subject, keyword, status));
  }

  public List<AssignmentTemplate> searchPage(Connection c, long subject, String keyword,
      TemplateStatus status, int offset, int size) throws SQLException {
    var values = new ArrayList<Object>(Arrays.asList(searchArguments(subject, keyword, status)));
    values.add(size);
    values.add(offset);
    try (var p = Jdbc.statement(c, "SELECT * FROM assignment_templates" + searchClause(status)
        + " ORDER BY updated_at DESC,template_id LIMIT ? OFFSET ?", values.toArray());
        var r = p.executeQuery()) {
      var result = new ArrayList<AssignmentTemplate>();
      while (r.next()) result.add(mapTemplate(r));
      return result;
    }
  }
public List<ExpertTemplateDTO> findVisibleByExpert(Connection c,long expertId,Long subjectId)
        throws SQLException {
    String sql="SELECT t.template_id,s.subject_id,s.subject_code,s.subject_name,t.title,"
            + "t.maximum_score,t.status,t.updated_at FROM assignment_templates t"
            + " JOIN subjects s ON s.subject_id=t.subject_id"
            + " JOIN subject_experts se ON se.subject_id=s.subject_id"
            + " WHERE se.expert_id=? AND se.is_active=TRUE";
    var args=new ArrayList<Object>(); args.add(expertId);
    if(subjectId!=null){ sql+=" AND s.subject_id=?"; args.add(subjectId); }
    sql+=" ORDER BY s.subject_code,t.title";
    try(var p=Jdbc.statement(c,sql,args.toArray());var r=p.executeQuery()){
        var out=new ArrayList<ExpertTemplateDTO>();
        while(r.next()) out.add(new ExpertTemplateDTO(r.getLong(1),r.getLong(2),r.getString(3),
                r.getString(4),r.getString(5),r.getBigDecimal(6),TemplateStatus.valueOf(r.getString(7)),
                r.getTimestamp(8).toInstant()));
        return out;
    }
}


  public java.util.List<AssignmentTemplate> findBySubjectId(long id) {
    return vn.edu.fpt.ams.util.Jdbc.transaction(
        vn.edu.fpt.ams.util.DBConnection::getConnection, c -> findBySubjectId(c, id));
  }

  public java.util.List<AssignmentTemplate> findBySubjectId(java.sql.Connection c, long id)
      throws SQLException {
    try (var p =
            vn.edu.fpt.ams.util.Jdbc.statement(
                c,
                "SELECT * FROM assignment_templates WHERE subject_id=? ORDER BY updated_at"
                    + " DESC,template_id",
                id);
        var r = p.executeQuery()) {
      var list = new java.util.ArrayList<AssignmentTemplate>();
      while (r.next()) list.add(mapTemplate(r));
      return list;
    }
  }

  public java.util.Optional<AssignmentTemplate> findById(long id) {
    return vn.edu.fpt.ams.util.Jdbc.transaction(
        vn.edu.fpt.ams.util.DBConnection::getConnection, c -> findById(c, id, false));
  }

  public java.util.Optional<AssignmentTemplate> findById(
      java.sql.Connection c, long id, boolean lock) throws SQLException {
    try (var p =
            vn.edu.fpt.ams.util.Jdbc.statement(
                c,
                "SELECT * FROM assignment_templates WHERE template_id=?"
                    + (lock ? " FOR UPDATE" : ""),
                id);
        var r = p.executeQuery()) {
      return r.next() ? java.util.Optional.of(mapTemplate(r)) : java.util.Optional.empty();
    }
  }

  public long insert(AssignmentTemplate t, long expertId) {
    return vn.edu.fpt.ams.util.Jdbc.transaction(
        vn.edu.fpt.ams.util.DBConnection::getConnection, c -> insert(c, t));
  }

  public long insert(java.sql.Connection c, AssignmentTemplate t) throws SQLException {
    return vn.edu.fpt.ams.util.Jdbc.insert(
        c,
        "INSERT INTO assignment_templates(subject_id,title,instructions,maximum_score,status)"
            + " VALUES(?,?,?,?,?)",
        t.subjectId(),
        t.title(),
        t.instructions(),
        t.maximumScore(),
        "ACTIVE");
  }

  public void update(AssignmentTemplate t, long expertId) {
    vn.edu.fpt.ams.util.Jdbc.transaction(
        vn.edu.fpt.ams.util.DBConnection::getConnection,
        c -> {
          update(c, t);
          return null;
        });
  }

  public void update(java.sql.Connection c, AssignmentTemplate t) throws SQLException {
    vn.edu.fpt.ams.util.Jdbc.update(
        c,
        "UPDATE assignment_templates SET"
            + " title=?,instructions=?,maximum_score=?,updated_at=CURRENT_TIMESTAMP WHERE"
            + " template_id=?",
        t.title(),
        t.instructions(),
        t.maximumScore(),
        t.templateId());
  }

  public void updateStatus(long id, TemplateStatus status, long expertId) {
    vn.edu.fpt.ams.util.Jdbc.transaction(
        vn.edu.fpt.ams.util.DBConnection::getConnection,
        c -> {
          updateStatus(c, id, status);
          return null;
        });
  }

  public void updateStatus(java.sql.Connection c, long id, TemplateStatus status)
      throws SQLException {
    vn.edu.fpt.ams.util.Jdbc.update(
        c,
        "UPDATE assignment_templates SET status=?,updated_at=CURRENT_TIMESTAMP WHERE template_id=?",
        status.name(),
        id);
  }

  static AssignmentTemplate mapTemplate(ResultSet rs) throws SQLException {
    return new AssignmentTemplate(
        rs.getLong("template_id"),
        rs.getLong("subject_id"),
        rs.getString("title"),
        rs.getString("instructions"),
        rs.getBigDecimal("maximum_score"),
        TemplateStatus.valueOf(rs.getString("status")),
        ResultSetSupport.instant(rs, "created_at"),
        ResultSetSupport.instant(rs, "updated_at"));
  }
}
