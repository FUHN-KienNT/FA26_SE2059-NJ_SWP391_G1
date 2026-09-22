package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;

/** JDBC operations for SubjectTerm; implementations are reserved for the next iteration. */
public class SubjectTermDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement transactional JDBC access according to SDS");
    }

public java.util.Optional<SubjectTerm> findBySubjectAndTerm(long subjectId, long termId) { throw pending(); }
public long insert(SubjectTerm term) { throw pending(); }
public void update(SubjectTerm term) { throw pending(); }

}
