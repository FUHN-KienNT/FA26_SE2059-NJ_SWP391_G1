package vn.edu.fpt.ams.dao;
import java.sql.*;
import java.util.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.util.Jdbc;
/** Data access only. Caller owns the connection and transaction. */
public final class TemplateLookupDAO {
  public static Map<Long,String> findSubjectLabels(Connection c, long actor, UserRole role) throws SQLException {
          String sql;
          if (role == UserRole.EXPERT)
            sql =
                "SELECT s.subject_id,s.subject_code,s.subject_name FROM subjects s JOIN"
                    + " subject_experts e ON e.subject_id=s.subject_id WHERE e.expert_id=? AND"
                    + " e.is_active=TRUE ORDER BY s.subject_code";
          else if (role == UserRole.TEACHER)
            sql =
                "SELECT DISTINCT s.subject_id,s.subject_code,s.subject_name FROM subjects s JOIN"
                    + " subject_terms st ON st.subject_id=s.subject_id JOIN subject_classes sc ON"
                    + " sc.subject_term_id=st.subject_term_id WHERE sc.teacher_id=? AND"
                    + " sc.status='ACTIVE' ORDER BY s.subject_code";
          else throw new IllegalArgumentException("Unsupported role");
          var out = new java.util.LinkedHashMap<Long, String>();
          try (var p = vn.edu.fpt.ams.util.Jdbc.statement(c, sql, actor);
              var r = p.executeQuery()) {
            while (r.next()) out.put(r.getLong(1), r.getString(2) + " — " + r.getString(3));
          }
          return out;

  }
  public static long countTeacherClasses(Connection c, long actorId, long subjectId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM subject_classes sc JOIN subject_terms st ON"
                    + " st.subject_term_id=sc.subject_term_id WHERE sc.teacher_id=? AND"
                    + " sc.status='ACTIVE' AND st.subject_id=?", actorId, subjectId);
  }

  public static long countSubject(Connection c, long subjectId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM subjects WHERE subject_id=?", subjectId);
  }
}
