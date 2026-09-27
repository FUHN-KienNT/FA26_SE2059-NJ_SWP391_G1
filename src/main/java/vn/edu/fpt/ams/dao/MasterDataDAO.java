package vn.edu.fpt.ams.dao;

import java.util.List;
import java.util.Optional;
import java.sql.ResultSet;
import java.sql.SQLException;
import vn.edu.fpt.ams.model.AcademicTerm;

/** Persistence boundary for Academic Terms and future small reference data sets. */
public class MasterDataDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement Master Data JDBC access");
    }

    public Optional<AcademicTerm> findTermById(long termId) { throw pending(); }
    public Optional<AcademicTerm> findTermByCode(String termCode) { throw pending(); }
    public List<AcademicTerm> findTerms(String keyword, Boolean active) { throw pending(); }
    public Optional<AcademicTerm> findCurrentTerm() { throw pending(); }
    public long insertTerm(AcademicTerm term) { throw pending(); }
    public void updateTerm(AcademicTerm term) { throw pending(); }
    public void updateTermStatus(long termId, boolean active) { throw pending(); }
    public void markCurrentTerm(long termId) { throw pending(); }

    static AcademicTerm mapTerm(ResultSet rs) throws SQLException {
        return new AcademicTerm(rs.getLong("term_id"), rs.getString("term_code"),
                rs.getString("term_name"), ResultSetSupport.instant(rs, "starts_at"),
                ResultSetSupport.instant(rs, "ends_at"), rs.getBoolean("is_current"),
                rs.getBoolean("is_active"));
    }
}
