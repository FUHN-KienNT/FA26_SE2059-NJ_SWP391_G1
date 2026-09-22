package vn.edu.fpt.ams.dao;

import java.util.List;
import java.util.Optional;
import vn.edu.fpt.ams.model.SubjectClass;

/** JDBC boundary for class identity, capacity and Teacher assignment. */
public class SubjectClassDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement Subject Class JDBC access");
    }

    public Optional<SubjectClass> findById(long classId) { throw pending(); }
    public Optional<SubjectClass> findByCode(String classCode) { throw pending(); }
    public List<SubjectClass> findBySubjectTerm(long subjectTermId) { throw pending(); }
    public List<SubjectClass> findManagedByExpert(long expertId, Long subjectId, Long termId) { throw pending(); }
    public List<SubjectClass> findAssignedToTeacher(long teacherId) { throw pending(); }
    public long insert(SubjectClass subjectClass) { throw pending(); }
    public void update(SubjectClass subjectClass) { throw pending(); }
    /** Run under an authorization and transaction boundary; Teacher must be active and eligible. */
    public void updateTeacher(long classId, long teacherId) { throw pending(); }
}
