package vn.edu.fpt.ams.service;

import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.ValidationUtil;

/** Workflow boundary; unfinished writes fail closed instead of claiming success. */
public class TemplateService {
    private final TemplateDAO dao = new TemplateDAO();

    private static UnsupportedOperationException pending() { return new UnsupportedOperationException("TODO: implement SDS workflow and authorization"); }
    public java.util.List<AssignmentTemplate> listTemplates(long actorId, long subjectId) { throw pending(); }
    public AssignmentTemplate findTemplate(long actorId, long templateId) { throw pending(); }
    public long createTemplate(long expertId, AssignmentTemplate template) { throw pending(); }
    public void updateTemplate(long expertId, AssignmentTemplate template) { throw pending(); }
    public void changeStatus(long expertId, long templateId, TemplateStatus status) { throw pending(); }
}
