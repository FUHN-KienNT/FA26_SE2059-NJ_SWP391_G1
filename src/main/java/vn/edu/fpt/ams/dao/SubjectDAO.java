package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;

/** JDBC operations for Subject; implementations are reserved for the next iteration. */
public class SubjectDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement transactional JDBC access according to SDS");
    }

public java.util.List<Subject> findAll(String keyword, SubjectStatus status) { throw pending(); }
public java.util.Optional<Subject> findById(long id) { throw pending(); }
public java.util.Optional<Subject> findByCode(String code) { throw pending(); }
public long insert(Subject subject) { throw pending(); }
public void update(Subject subject) { throw pending(); }
public void updateStatus(long id, SubjectStatus status) { throw pending(); }

}
