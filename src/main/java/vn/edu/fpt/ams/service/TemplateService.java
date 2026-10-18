package vn.edu.fpt.ams.service;
import vn.edu.fpt.ams.dao.*;

import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

/** Authorized Template workflows. Assignment snapshots are never rewritten here. */
public class TemplateService {
  public TemplatePage page(long actor, long subject, String keyword, TemplateStatus status, int requestedPage) {
    if (requestedPage < 1 || requestedPage > 100000)
      throw new IllegalArgumentException("Số trang không hợp lệ.");
    if (keyword != null && keyword.length() > 200)
      throw new IllegalArgumentException("Từ khóa tìm kiếm tối đa 200 ký tự.");
    return Jdbc.transaction(connections, c -> {
      UserRole role = Access.active(c, actor);
      if (role != UserRole.EXPERT && role != UserRole.TEACHER)
        throw new SecurityException("Templates are restricted to Experts and Teachers");
      if (subject == 0) return new TemplatePage(java.util.List.of(), 0, 1, 10);
      read(c, actor, subject);
      TemplateStatus visibleStatus = role == UserRole.TEACHER ? TemplateStatus.ACTIVE : status;
      String search = keyword == null ? "" : keyword.strip();
      long total = dao.countMatching(c, subject, search, visibleStatus);
      int page = (int)Math.min(requestedPage, Math.max(1, (total + 9) / 10));
      return new TemplatePage(dao.searchPage(c, subject, search, visibleStatus, (page - 1) * 10, 10),
          total, page, 10);
    });
  }
    public java.util.List<ExpertTemplateDTO> listVisible(long actorId, Long subjectId) {
        if(actorId<=0) throw new IllegalArgumentException("Expert ID must be positive");
        if(subjectId!=null&&subjectId<=0) throw new IllegalArgumentException("Subject ID must be positive");
        return Jdbc.transaction(connections,c->{
            if(Access.active(c,actorId)!=UserRole.EXPERT)
                throw new SecurityException("Expert access is required");
            return dao.findVisibleByExpert(c,actorId,subjectId);
        });
    }

  private final TemplateDAO dao = new TemplateDAO();
  private final vn.edu.fpt.ams.util.Jdbc.Connections connections;

  public TemplateService() {
    this(vn.edu.fpt.ams.util.DBConnection::getConnection);
  }

  public TemplateService(vn.edu.fpt.ams.util.Jdbc.Connections connections) {
    this.connections = connections;
  }

  public java.util.Map<Long, String> subjects(long actor) {
    return vn.edu.fpt.ams.util.Jdbc.transaction(
        connections,
        c -> {
          UserRole role = Access.active(c, actor);
          if (role != UserRole.EXPERT && role != UserRole.TEACHER)
            throw new SecurityException("Templates are restricted to Experts and Teachers");
          return TemplateLookupDAO.findSubjectLabels(c, actor, role);
        });
  }

  public ResponsibilityType responsibility(long actor, long subject) {
    return vn.edu.fpt.ams.util.Jdbc.transaction(connections, c -> Access.expert(c, actor, subject));
  }

  private UserRole read(java.sql.Connection c, long actor, long subject)
      throws java.sql.SQLException {
    UserRole role = Access.active(c, actor);
    if (role == UserRole.EXPERT) Access.expert(c, actor, subject);
    else if (role != UserRole.TEACHER
        || TemplateLookupDAO.countTeacherClasses(c, actor, subject)
            == 0) throw new SecurityException("No access to this Subject's Templates");
    return role;
  }

  public java.util.List<AssignmentTemplate> listTemplates(long actor, long subject) {
    return vn.edu.fpt.ams.util.Jdbc.transaction(
        connections,
        c -> {
          UserRole role = read(c, actor, subject);
          return dao.findBySubjectId(c, subject).stream()
              .filter(t -> role != UserRole.TEACHER || t.status() == TemplateStatus.ACTIVE)
              .toList();
        });
  }

  public AssignmentTemplate findTemplate(long actor, long id) {
    return vn.edu.fpt.ams.util.Jdbc.transaction(
        connections,
        c -> {
          Access.active(c, actor);
          var t =
              dao.findById(c, id, false)
                  .orElseThrow(() -> new java.util.NoSuchElementException("Template not found"));
          UserRole role = read(c, actor, t.subjectId());
          if (role == UserRole.TEACHER && t.status() != TemplateStatus.ACTIVE)
            throw new SecurityException("Only active Templates are available to Teachers");
          return t;
        });
  }

  public long createTemplate(long actor, AssignmentTemplate t) {
    validate(t);
    return vn.edu.fpt.ams.util.Jdbc.transaction(
        connections,
        c -> {
          if (Access.expert(c, actor, t.subjectId()) != ResponsibilityType.PRIMARY)
            throw new SecurityException("Only Primary Experts create Templates");
          if (TemplateLookupDAO.countSubject(c, t.subjectId())
              == 0) throw new IllegalArgumentException("Subject not found");
          return dao.insert(c, t);
        });
  }

  public void updateTemplate(long actor, AssignmentTemplate t) {
    updateTemplate(actor, t, null);
  }

  public void updateTemplate(long actor, AssignmentTemplate t, TemplateStatus targetStatus) {
    validate(t);
    vn.edu.fpt.ams.util.Jdbc.transaction(
        connections,
        c -> {
          var responsibility = Access.expert(c, actor, t.subjectId());
          var old = dao.findById(c, t.templateId(), true).orElseThrow();
          if (old.subjectId() != t.subjectId())
            throw new IllegalArgumentException("Template Subject cannot change");
          if (targetStatus != null && targetStatus != old.status()
              && responsibility != ResponsibilityType.PRIMARY)
            throw new SecurityException("Only Primary Experts change Template status");
          dao.update(c, t);
          if (targetStatus != null && targetStatus != old.status())
            dao.updateStatus(c, t.templateId(), targetStatus);
          return null;
        });
  }

  public void changeStatus(long actor, long id, TemplateStatus target) {
    if (target == null) throw new IllegalArgumentException("Status required");
    vn.edu.fpt.ams.util.Jdbc.transaction(
        connections,
        c -> {
          Access.active(c, actor);
          var snapshot = dao.findById(c, id, false).orElseThrow();
          if (Access.expert(c, actor, snapshot.subjectId()) != ResponsibilityType.PRIMARY)
            throw new SecurityException("Only Primary Experts change Template status");
          var current = dao.findById(c, id, true).orElseThrow();
          if (current.status() != target) dao.updateStatus(c, id, target);
          return null;
        });
  }

  static void validate(AssignmentTemplate t) {
    if (t == null
        || t.subjectId() <= 0
        || t.title() == null
        || t.title().isBlank()
        || t.title().strip().length() > 200
        || t.instructions() == null
        || t.instructions().isBlank())
      throw new IllegalArgumentException(
          "Subject, title (1–200 characters) and instructions are required");
    if (!AssignmentRules.validMaximum(t.maximumScore()))
      throw new IllegalArgumentException("Điểm tối đa phải là số nguyên từ 1 đến 100.");
  }
}
