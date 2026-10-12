package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.math.BigDecimal;
import vn.edu.fpt.ams.service.AssignmentService;
import vn.edu.fpt.ams.dto.AssignmentListFilter;
import vn.edu.fpt.ams.model.AccountStatus;
import vn.edu.fpt.ams.model.UserRole;
import vn.edu.fpt.ams.dto.AssignmentForm;
import vn.edu.fpt.ams.dto.AssignmentCreatePage;
import vn.edu.fpt.ams.dto.AssignmentEditPage;
import vn.edu.fpt.ams.dto.AssignmentLifecyclePage;
import vn.edu.fpt.ams.util.AssignmentDetailView;
import vn.edu.fpt.ams.util.AssignmentEditConflictException;
import vn.edu.fpt.ams.util.AssignmentDraftValidator.ValidationException;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet("/teacher/assignments")
public class AssignmentServlet extends HttpServlet {
    private final AssignmentService service;
    public AssignmentServlet() { this(new AssignmentService()); }
    AssignmentServlet(AssignmentService service) { this.service = service; }
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setHeader("Cache-Control", "no-store");
        HttpSession session = req.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Long)
                || session.getAttribute("accountStatus") != AccountStatus.ACTIVE) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sign-in is required.");
            return;
        }
        if (session.getAttribute("role") != UserRole.TEACHER) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String view = "/WEB-INF/views/assignments/list.jsp";
        try {
            long teacherId = (Long) session.getAttribute("userId");
            String mode = req.getParameter("mode");
            if ("view".equals(mode)) {
                view = "/WEB-INF/views/assignments/detail.jsp";
                long assignmentId = Long.parseLong(req.getParameter("assignmentId"));
                if (assignmentId <= 0) throw new IllegalArgumentException("Invalid assignment ID.");
                var filter = AssignmentListFilter.from(req.getParameter("search"), req.getParameter("purpose"),
                        req.getParameter("responseType"), req.getParameter("status"));
                var page = service.detailPage(teacherId, assignmentId);
                req.setAttribute("assignmentDetail", page);
                req.setAttribute("assignmentFilters", filter);
            } else if ("create".equals(mode)) {
                view = "/WEB-INF/views/assignments/form.jsp";
                long classId = WebSupport.id(req.getParameter("classId"));
                req.setAttribute("createPage", service.createPage(teacherId, classId));
                req.setAttribute("assignmentForm", AssignmentForm.empty(classId));
                WebSupport.csrf(req);
            } else if ("edit".equals(mode)) {
                view="/WEB-INF/views/assignments/form.jsp";
                req.setAttribute("assignmentEdit",true);
                long assignmentId=WebSupport.id(req.getParameter("assignmentId"));
                var page=service.editPage(teacherId,assignmentId);
                editContext(req,page);
                req.setAttribute("assignmentForm",page.form());
                WebSupport.csrf(req);
            } else if ("publish".equals(mode) || "cancel".equals(mode)) {
                view = "/WEB-INF/views/assignments/lifecycle.jsp";
                req.setAttribute("lifecycleAction",mode);
                long assignmentId=WebSupport.id(req.getParameter("assignmentId"));
                req.setAttribute("assignmentFilters",readFilters(req));
                req.setAttribute("lifecyclePage",service.lifecyclePage(teacherId,assignmentId,mode));
                WebSupport.csrf(req);
            } else {
                if (mode != null && !mode.isBlank() && !"list".equals(mode))
                    throw new IllegalArgumentException("Unknown assignment action.");
                String rawClassId = req.getParameter("classId");
                Long classId = rawClassId == null || rawClassId.isBlank() ? null : Long.valueOf(rawClassId);
                var filter = AssignmentListFilter.from(req.getParameter("search"), req.getParameter("purpose"),
                        req.getParameter("responseType"), req.getParameter("status"));
                var page = service.listPage(teacherId, classId, filter);
                req.setAttribute("assignmentPage", page);
                req.setAttribute("assignments", page.assignments());
            }
        } catch (IllegalArgumentException ex) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid assignment, class or filters.");
            return;
        } catch (NoSuchElementException ex) {
            // Do not disclose whether an inaccessible assignment belongs to another Teacher.
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "Assignment not found or unavailable.");
            return;
        } catch (SecurityException ex) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
            return;
        } catch (IllegalStateException ex) {
            if(ex instanceof AssignmentEditConflictException) { res.sendError(409,ex.getMessage()); return; }
            getServletContext().log("Assignment page could not be loaded", ex);
            res.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            if ("/WEB-INF/views/assignments/form.jsp".equals(view))
                req.setAttribute("createError", "The form could not be loaded. Please try again.");
            else if ("/WEB-INF/views/assignments/lifecycle.jsp".equals(view))
                req.setAttribute("lifecycleError", "The confirmation could not be loaded. Please try again.");
            else if ("/WEB-INF/views/assignments/detail.jsp".equals(view))
                req.setAttribute("assignmentDetailError", "This assignment could not be loaded. Please try again.");
            else
                req.setAttribute("assignmentListError", "Assignments could not be loaded. Please try again.");
        }
        req.getRequestDispatcher(view).forward(req, res);
    }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        HttpSession session = req.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Long)
                || session.getAttribute("accountStatus") != AccountStatus.ACTIVE) {
            res.sendError(401, "Sign-in is required."); return;
        }
        if (session.getAttribute("role") != UserRole.TEACHER) { res.sendError(403); return; }
        String action=req.getParameter("action");
        if("publish".equals(action) || "cancel".equals(action)) {
            lifecyclePost(req,res,session,action); return;
        }
        boolean editing="update".equals(req.getParameter("action"));
        if (!editing && !"create".equals(req.getParameter("action"))) {
            res.sendError(501, "This assignment action is not available yet."); return;
        }
        Map<String, String> values = new HashMap<>();
        for (String field : AssignmentForm.FIELDS)
            values.put(field, req.getParameter(field) == null ? "" : req.getParameter(field));
        AssignmentForm form = new AssignmentForm(values);
        req.setAttribute("assignmentForm", form);
        req.setAttribute("assignmentEdit",editing);
        boolean validToken;
        try { WebSupport.checkCsrf(req); validToken = true; }
        catch (SecurityException ex) { validToken = false; }
        try {
            long teacherId = (Long) session.getAttribute("userId");
            long assignmentId=editing?WebSupport.id(form.value("assignmentId")):0;
            long classId;
            if(editing) {
                var page=service.editPage(teacherId,assignmentId);
                editContext(req,page); classId=page.context().classId();
            } else {
                classId=WebSupport.id(form.value("classId"));
                req.setAttribute("createPage",service.createPage(teacherId,classId));
            }
            if (!validToken) {
                // Never save this POST. Redisplay with the current session token for an explicit retry.
                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                WebSupport.csrf(req);
                req.setAttribute("createError", "Your sign-in session changed or this form expired. Nothing has been saved. Your entered content is kept below. Review it and click "+(editing?"Save Changes":"Save Draft")+" again.");
            } else {
                long id;
                if(editing) {
                    service.updateAssignment(teacherId,assignmentId,form); id=assignmentId;
                    session.setAttribute("updatedAssignmentId",id);
                    if(Long.valueOf(id).equals(session.getAttribute("createdAssignmentId"))) session.removeAttribute("createdAssignmentId");
                } else { id=service.createAssignment(teacherId,form); session.setAttribute("createdAssignmentId",id); }
                res.sendRedirect(req.getContextPath() + "/teacher/assignments?mode=view&classId=" + classId + "&assignmentId=" + id);
                return;
            }
        } catch (ValidationException ex) {
            res.setStatus(400);
            req.setAttribute("formErrors", ex.errors());
        } catch (NoSuchElementException ex) {
            res.sendError(404,"Assignment not found or unavailable."); return;
        } catch (SecurityException ex) {
            res.sendError(403, ex.getMessage()); return;
        } catch (IllegalArgumentException ex) {
            res.sendError(400, "Invalid class or assignment data."); return;
        } catch (IllegalStateException ex) {
            if(ex instanceof AssignmentEditConflictException) {
                res.setStatus(409); req.setAttribute("editConflict",true); req.setAttribute("createError",ex.getMessage());
            } else {
            getServletContext().log("Assignment draft could not be saved", ex);
            res.setStatus(503);
            req.setAttribute("createError", "The draft could not be saved. Your entered data is kept below. Class and template availability will be checked again when you retry.");
            }
            if (req.getAttribute("createPage") == null) {
                req.setAttribute("createPage", retryContext(form));
                req.setAttribute("createContextUnavailable", true);
            }
        }
        req.getRequestDispatcher("/WEB-INF/views/assignments/form.jsp").forward(req, res);
    }

    /** Confirmation GETs never write; only an authorized, token-checked POST can change state. */
    private void lifecyclePost(HttpServletRequest req,HttpServletResponse res,HttpSession session,String action)
            throws ServletException,IOException {
        req.setAttribute("lifecycleAction",action);
        long assignmentId=0;
        long teacherId=(Long)session.getAttribute("userId");
        try {
            assignmentId=WebSupport.id(req.getParameter("assignmentId"));
            var filters=readFilters(req);
            req.setAttribute("assignmentFilters",filters);
            var page=service.lifecyclePage(teacherId,assignmentId,action);
            req.setAttribute("lifecyclePage",page);
            try { WebSupport.checkCsrf(req); }
            catch(SecurityException ex) {
                res.setStatus(403);
                req.setAttribute("lifecycleError","Your sign-in session changed or this confirmation expired. Nothing was changed. Review the saved assignment and confirm again.");
                WebSupport.csrf(req);
                req.getRequestDispatcher("/WEB-INF/views/assignments/lifecycle.jsp").forward(req,res);
                return;
            }
            if("publish".equals(action)) {
                service.publishAssignment(teacherId,assignmentId,req.getParameter("revision"));
                session.setAttribute("publishedAssignmentId",assignmentId);
            } else {
                service.cancelAssignment(teacherId,assignmentId,req.getParameter("revision"));
                session.setAttribute("cancelledAssignmentId",assignmentId);
            }
            // Do not repeat an earlier Create/Edit notice after a later lifecycle action.
            for(String key:List.of("createdAssignmentId","updatedAssignmentId"))
                if(Long.valueOf(assignmentId).equals(session.getAttribute(key))) session.removeAttribute(key);
            String detail=AssignmentDetailView.listUrl(req.getContextPath(),page.detail().assignment().classId(),filters)
                    +"&mode=view&assignmentId="+assignmentId;
            res.sendRedirect(detail); return;
        } catch(ValidationException ex) {
            res.setStatus(400);
            req.setAttribute("lifecycleErrors",ex.errors());
            req.setAttribute("lifecycleError","The assignment was not changed. Correct the saved configuration or reopen the confirmation, then try again.");
        } catch(NoSuchElementException ex) {
            res.sendError(404,"Assignment not found or unavailable."); return;
        } catch(SecurityException ex) {
            res.sendError(403,ex.getMessage()); return;
        } catch(IllegalArgumentException ex) {
            res.sendError(400,"Invalid assignment or filters."); return;
        } catch(IllegalStateException ex) {
            if(ex instanceof AssignmentEditConflictException) {
                res.setStatus(409); req.setAttribute("lifecycleError",ex.getMessage());
            } else {
                getServletContext().log("Assignment lifecycle action could not be saved",ex);
                res.setStatus(503);
                req.setAttribute("lifecycleError","The action could not be saved. Review the current assignment and try again.");
            }
        }
        // A stale confirmation must show the latest saved state before an explicit retry.
        try { req.setAttribute("lifecyclePage",service.lifecyclePage(teacherId,assignmentId,action)); }
        catch(NoSuchElementException ex) { res.sendError(404,"Assignment not found or unavailable."); return; }
        catch(SecurityException ex) { res.sendError(403,ex.getMessage()); return; }
        catch(IllegalStateException ex) {
            req.removeAttribute("lifecyclePage"); res.setStatus(503);
            req.setAttribute("lifecycleError","The latest assignment could not be loaded. Reopen the confirmation when the database is available.");
        }
        req.getRequestDispatcher("/WEB-INF/views/assignments/lifecycle.jsp").forward(req,res);
    }

    private static AssignmentListFilter readFilters(HttpServletRequest req) {
        return AssignmentListFilter.from(req.getParameter("search"),req.getParameter("purpose"),
                req.getParameter("responseType"),req.getParameter("status"));
    }

    private static void editContext(HttpServletRequest req,AssignmentEditPage page) {
        req.setAttribute("createPage",page.context());
        req.setAttribute("savedResponseType",page.form().value("responseType"));
        req.setAttribute("savedEssayContent",!page.form().value("scopeText").isBlank() || !"[]".equals(page.form().value("tasksJson")));
        req.setAttribute("savedObjectiveContent",!"[]".equals(page.form().value("questionsJson")));
    }

    /** Display submitted values during an outage; this context never grants write access. */
    private static AssignmentCreatePage retryContext(AssignmentForm form) {
        long classId;
        try { classId = WebSupport.id(form.value("classId")); }
        catch (IllegalArgumentException ex) { classId = 0; }
        List<AssignmentCreatePage.TemplateOption> templates = List.of();
        try {
            long templateId = WebSupport.id(form.value("templateId"));
            templates = List.of(new AssignmentCreatePage.TemplateOption(templateId,
                    "Selected template #" + templateId, "", BigDecimal.ZERO));
        } catch (IllegalArgumentException ex) {
            // No valid template was submitted; the normal required selection remains blank.
        }
        return new AssignmentCreatePage(classId, 0, "Class #" + classId,
                "Context temporarily unavailable", "", "", templates);
    }
}
