package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;

/** JDBC operations for AssignmentTemplate; implementations are reserved for the next iteration. */
public class TemplateDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement transactional JDBC access according to SDS");
    }

public java.util.List<AssignmentTemplate> findBySubjectId(long subjectId) { throw pending(); }
public java.util.Optional<AssignmentTemplate> findById(long templateId) { throw pending(); }
public long insert(AssignmentTemplate template, long expertId) { throw pending(); }
public void update(AssignmentTemplate template, long expertId) { throw pending(); }
public void updateStatus(long templateId, TemplateStatus status, long expertId) { throw pending(); }

}
