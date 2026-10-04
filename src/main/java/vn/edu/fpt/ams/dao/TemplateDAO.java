package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;
import java.util.*;
import vn.edu.fpt.ams.dto.ExpertTemplateDTO;
import vn.edu.fpt.ams.util.Jdbc;

/** JDBC operations for AssignmentTemplate; implementations are reserved for the next iteration. */
public class TemplateDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement transactional JDBC access according to SDS");
    }

public java.util.List<AssignmentTemplate> findBySubjectId(long subjectId) { throw pending(); }
public java.util.Optional<AssignmentTemplate> findById(long templateId) { throw pending(); }
/** INSERT persists created_at and updated_at from the supplied immutable snapshot. */
public long insert(AssignmentTemplate template, long expertId) { throw pending(); }
public void update(AssignmentTemplate template, long expertId) { throw pending(); }
public void updateStatus(long templateId, TemplateStatus status, long expertId) { throw pending(); }

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

static AssignmentTemplate mapTemplate(ResultSet rs) throws SQLException {
    return new AssignmentTemplate(rs.getLong("template_id"), rs.getLong("subject_id"),
            rs.getString("title"), rs.getString("instructions"),
            rs.getBigDecimal("maximum_score"), TemplateStatus.valueOf(rs.getString("status")),
            ResultSetSupport.instant(rs, "created_at"),
            ResultSetSupport.instant(rs, "updated_at"));
}

}
