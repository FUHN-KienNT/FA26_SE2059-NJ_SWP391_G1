package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;
import java.sql.ResultSet;
import java.sql.SQLException;

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

static AssignmentTemplate mapTemplate(ResultSet rs) throws SQLException {
    return new AssignmentTemplate(rs.getLong("template_id"), rs.getLong("subject_id"),
            rs.getString("title"), rs.getString("instructions"),
            rs.getBigDecimal("maximum_score"), TemplateStatus.valueOf(rs.getString("status")),
            ResultSetSupport.instant(rs, "created_at"),
            ResultSetSupport.instant(rs, "updated_at"));
}

}
