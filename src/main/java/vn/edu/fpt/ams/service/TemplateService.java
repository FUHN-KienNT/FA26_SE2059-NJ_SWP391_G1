package vn.edu.fpt.ams.service;

import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.ValidationUtil;
import vn.edu.fpt.ams.util.*;

/** Workflow boundary; unfinished writes fail closed instead of claiming success. */
public class TemplateService {
    private final Jdbc.Connections connections;
    private final TemplateDAO dao = new TemplateDAO();

    public TemplateService(){this(DBConnection::getConnection);}
    public TemplateService(Jdbc.Connections source){connections=source;}

    private static UnsupportedOperationException pending() { return new UnsupportedOperationException("TODO: implement SDS workflow and authorization"); }
    public java.util.List<AssignmentTemplate> listTemplates(long actorId, long subjectId) { throw pending(); }
    public java.util.List<ExpertTemplateDTO> listVisible(long actorId, Long subjectId) {
        if(actorId<=0) throw new IllegalArgumentException("Expert ID must be positive");
        if(subjectId!=null&&subjectId<=0) throw new IllegalArgumentException("Subject ID must be positive");
        return Jdbc.transaction(connections,c->{
            if(Access.active(c,actorId)!=UserRole.EXPERT)
                throw new SecurityException("Expert access is required");
            return dao.findVisibleByExpert(c,actorId,subjectId);
        });
    }
    public AssignmentTemplate findTemplate(long actorId, long templateId) { throw pending(); }
    public long createTemplate(long expertId, AssignmentTemplate template) { throw pending(); }
    public void updateTemplate(long expertId, AssignmentTemplate template) { throw pending(); }
    public void changeStatus(long expertId, long templateId, TemplateStatus status) { throw pending(); }
}
