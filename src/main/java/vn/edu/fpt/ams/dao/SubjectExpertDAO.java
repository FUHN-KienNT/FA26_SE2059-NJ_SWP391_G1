package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;

/** JDBC operations for SubjectExpert; implementations are reserved for the next iteration. */
public class SubjectExpertDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement transactional JDBC access according to SDS");
    }

public java.util.List<SubjectExpert> findBySubjectId(long subjectId) { throw pending(); }
/** Upsert must reactivate an existing pair without changing assigned_at. */
public void assign(long subjectId, long expertId) { throw pending(); }
public void deactivate(long subjectId, long expertId) { throw pending(); }

}
