package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;

/** JDBC operations for AcademicTerm; implementations are reserved for the next iteration. */
public class AcademicTermDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement transactional JDBC access according to SDS");
    }

public java.util.Optional<AcademicTerm> findById(long id) { throw pending(); }
public java.util.Optional<AcademicTerm> findByCode(String code) { throw pending(); }
public java.util.List<AcademicTerm> findAll(String keyword, Boolean active) { throw pending(); }
public java.util.Optional<AcademicTerm> findCurrentTerm() { throw pending(); }
public long insert(AcademicTerm term) { throw pending(); }
public void update(AcademicTerm term) { throw pending(); }
public void updateStatus(long id, boolean active) { throw pending(); }
public void markCurrent(long id) { throw pending(); }

}
