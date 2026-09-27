package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;
import java.sql.ResultSet;
import java.sql.SQLException;

/** JDBC operations for SubjectTerm; implementations are reserved for the next iteration. */
public class SubjectTermDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement transactional JDBC access according to SDS");
    }

public java.util.Optional<SubjectTerm> findBySubjectAndTerm(long subjectId, long termId) { throw pending(); }
public java.util.Optional<SubjectTerm> findById(long subjectTermId) { throw pending(); }
public long insert(SubjectTerm term) { throw pending(); }
public void update(SubjectTerm term) { throw pending(); }

static SubjectTerm mapSubjectTerm(ResultSet rs) throws SQLException {
    return new SubjectTerm(rs.getLong("subject_term_id"), rs.getLong("subject_id"),
            rs.getLong("term_id"), rs.getBoolean("registration_enabled"),
            ResultSetSupport.instant(rs, "registration_opens_at"),
            ResultSetSupport.instant(rs, "registration_closes_at"));
}

}
