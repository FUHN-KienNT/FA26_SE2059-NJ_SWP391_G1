package vn.edu.fpt.ams.dao;

import java.sql.*;
import java.util.*;
import vn.edu.fpt.ams.model.AcademicTerm;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.Jdbc;

/** Persistence boundary for Academic Terms and future small reference data sets. */
public class MasterDataDAO {
    public Optional<AcademicTerm> findTermById(long termId) {
        return Jdbc.transaction(DBConnection::getConnection, c -> findTermById(c, termId, false));
    }
    public Optional<AcademicTerm> findTermById(java.sql.Connection c, long termId, boolean lock)
            throws SQLException {
        try (var p = Jdbc.statement(c,
                "SELECT * FROM academic_terms WHERE term_id=?" + (lock ? " FOR UPDATE" : ""),
                termId);
             var r = p.executeQuery()) {
            return r.next() ? Optional.of(mapTerm(r)) : Optional.empty();
        }
    }
    public Optional<AcademicTerm> findTermByCode(String termCode) {
        return Jdbc.transaction(DBConnection::getConnection, c -> findTermByCode(c, termCode, false));
    }
    public Optional<AcademicTerm> findTermByCode(Connection c, String termCode, boolean lock)
            throws SQLException {
        try (var p = Jdbc.statement(c,
                "SELECT * FROM academic_terms WHERE upper(term_code)=upper(?)" +
                        (lock ? " FOR UPDATE" : ""), termCode);
             var r = p.executeQuery()) {
            return r.next() ? Optional.of(mapTerm(r)) : Optional.empty();
        }
    }
    public List<AcademicTerm> findTerms(String keyword, Boolean active) {
        return Jdbc.transaction(DBConnection::getConnection, c -> findTerms(c, keyword, active));
    }
    public List<AcademicTerm> findTerms(
            java.sql.Connection c, String keyword, Boolean active) throws SQLException {
        String sql = "SELECT * FROM academic_terms WHERE"
                + " (lower(term_code) LIKE ? OR lower(term_name) LIKE ?)";
        var args = new java.util.ArrayList<Object>();
        String pattern = "%" + (keyword == null ? ""
                : keyword.strip().toLowerCase(java.util.Locale.ROOT)) + "%";
        args.add(pattern);
        args.add(pattern);
        if (active != null) {
            sql += " AND is_active=?";
            args.add(active);
        }
        sql += " ORDER BY starts_at DESC";
        try (var p = Jdbc.statement(c, sql, args.toArray()); var r = p.executeQuery()) {
            var result = new java.util.ArrayList<AcademicTerm>();
            while (r.next()) result.add(mapTerm(r));
            return result;
        }
    }
    public Optional<AcademicTerm> findCurrentTerm() {
        return Jdbc.transaction(DBConnection::getConnection, this::findCurrentTerm);
    }
    public Optional<AcademicTerm> findCurrentTerm(Connection c) throws SQLException {
        try (var p = Jdbc.statement(c,
                "SELECT * FROM academic_terms WHERE is_current=TRUE");
             var r = p.executeQuery()) {
            return r.next() ? Optional.of(mapTerm(r)) : Optional.empty();
        }
    }
    /** Serialize service writes, including the case with no active/current row to lock. */
    public void lockTermWrites(Connection c) throws SQLException {
        if ("PostgreSQL".equals(c.getMetaData().getDatabaseProductName())) {
            try (var p = c.prepareStatement("SELECT pg_advisory_xact_lock(391, 10)");
                 var ignored = p.executeQuery()) { /* released on commit/rollback */ }
        } else {
            // H2 test fixture: every authorized write has an existing Admin account.
            try (var p = c.prepareStatement("SELECT user_id FROM users WHERE role='ADMIN' ORDER BY user_id FOR UPDATE");
                 var ignored = p.executeQuery()) { }
        }
    }
    public boolean existsActiveTermExcluding(Connection c, long termId) throws SQLException {
        return existsFlagExcluding(c, termId, "is_active");
    }
    public boolean existsCurrentTermExcluding(Connection c, long termId) throws SQLException {
        return existsFlagExcluding(c, termId, "is_current");
    }
    private boolean existsFlagExcluding(Connection c, long termId, String column) throws SQLException {
        try (var p = Jdbc.statement(c,
                "SELECT EXISTS(SELECT 1 FROM academic_terms WHERE " + column + "=TRUE AND term_id<>?)", termId);
             var r = p.executeQuery()) {
            return r.next() && r.getBoolean(1);
        }
    }
    public long insertTerm(AcademicTerm term) {
        return Jdbc.transaction(DBConnection::getConnection, c -> insertTerm(c, term));
    }
    public long insertTerm(Connection c, AcademicTerm term) throws SQLException {
        return Jdbc.insert(c,
                "INSERT INTO academic_terms(term_code,term_name,starts_at,ends_at,is_current,is_active) VALUES(?,?,?,?,?,?)",
                term.termCode(), term.termName(), term.startsAt(), term.endsAt(),
                term.current(), term.active());
    }
    public void updateTerm(AcademicTerm term) {
        Jdbc.transaction(DBConnection::getConnection, c -> { updateTerm(c, term); return null; });
    }
    public void updateTerm(Connection c, AcademicTerm term) throws SQLException {
        if (Jdbc.update(c,
                "UPDATE academic_terms SET term_code=?,term_name=?,starts_at=?,ends_at=?,is_active=?,is_current=? WHERE term_id=?",
                term.termCode(), term.termName(), term.startsAt(), term.endsAt(),
                term.active(), term.current(), term.termId()) != 1)
            throw new NoSuchElementException("Không tìm thấy học kỳ.");
    }
    public void updateTermStatus(long termId, boolean active) {
        Jdbc.transaction(DBConnection::getConnection, c -> { updateTermStatus(c, termId, active); return null; });
    }
    public void updateTermStatus(Connection c, long termId, boolean active) throws SQLException {
        if (Jdbc.update(c,
                "UPDATE academic_terms SET is_active=?,is_current=CASE WHEN ? THEN is_current ELSE FALSE END WHERE term_id=?",
                active, active, termId) != 1)
            throw new NoSuchElementException("Không tìm thấy học kỳ.");
    }
    public void markCurrentTerm(long termId) {
        Jdbc.transaction(DBConnection::getConnection, c -> { markCurrentTerm(c, termId); return null; });
    }
    public void markCurrentTerm(Connection c, long termId) throws SQLException {
        if (Jdbc.update(c,
                "UPDATE academic_terms SET is_current=TRUE WHERE term_id=? AND is_active=TRUE", termId) != 1)
            throw new IllegalArgumentException("Học kỳ hiện tại phải ở trạng thái hoạt động.");
    }

    static AcademicTerm mapTerm(ResultSet rs) throws SQLException {
        return new AcademicTerm(rs.getLong("term_id"), rs.getString("term_code"),
                rs.getString("term_name"), ResultSetSupport.instant(rs, "starts_at"),
                ResultSetSupport.instant(rs, "ends_at"), rs.getBoolean("is_current"),
                rs.getBoolean("is_active"));
    }
}
