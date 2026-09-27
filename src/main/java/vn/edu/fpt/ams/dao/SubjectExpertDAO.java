package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;
import java.sql.ResultSet;
import java.sql.SQLException;

/** JDBC operations for SubjectExpert; implementations are reserved for the next iteration. */
public class SubjectExpertDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement transactional JDBC access according to SDS");
    }

public java.util.List<SubjectExpert> findBySubjectId(long subjectId) { throw pending(); }
/** Upsert must reactivate an existing pair without changing assigned_at. */
public void assign(long subjectId, long expertId, ResponsibilityType responsibilityType) { throw pending(); }
public void updateResponsibility(long subjectExpertId, ResponsibilityType responsibilityType) { throw pending(); }
public void deactivate(long subjectId, long expertId) { throw pending(); }

static SubjectExpert mapSubjectExpert(ResultSet rs) throws SQLException {
    return new SubjectExpert(rs.getLong("subject_expert_id"), rs.getLong("subject_id"),
            rs.getLong("expert_id"), ResponsibilityType.valueOf(rs.getString("responsibility_type")),
            rs.getBoolean("is_active"), ResultSetSupport.instant(rs, "assigned_at"),
            ResultSetSupport.instant(rs, "deactivated_at"));
}

}
