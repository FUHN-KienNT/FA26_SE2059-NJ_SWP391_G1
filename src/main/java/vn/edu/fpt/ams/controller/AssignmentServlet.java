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
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Vui lòng đăng nhập để tiếp tục.");
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
                if (assignmentId <= 0) throw new IllegalArgumentException("Mã bài tập không hợp lệ.");
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
                    throw new IllegalArgumentException("Thao tác bài tập không hợp lệ.");
                String rawClassId = req.getParameter("classId");
                if (rawClassId == null || rawClassId.isBlank()) {
                    res.sendRedirect(req.getContextPath() + "/teacher/dashboard");
                    return;
                }
                long classId = WebSupport.id(rawClassId);
                var filter = AssignmentListFilter.from(req.getParameter("search"), req.getParameter("purpose"),
                        req.getParameter("responseType"), req.getParameter("status"));
                var page = service.listPage(teacherId, classId, filter);
                req.setAttribute("assignmentPage", page);
                req.setAttribute("assignments", page.assignments());
            }
        } catch (IllegalArgumentException ex) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bài tập, lớp hoặc bộ lọc không hợp lệ.");
            return;
        } catch (NoSuchElementException ex) {
            // Do not disclose whether an inaccessible assignment belongs to another Teacher.
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy bài tập hoặc bạn không có quyền truy cập.");
            return;
        } catch (SecurityException ex) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
            return;
        } catch (IllegalStateException ex) {
            if(ex instanceof AssignmentEditConflictException) { res.sendError(409,ex.getMessage()); return; }
            getServletContext().log("Assignment page could not be loaded", ex);
            res.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            if ("/WEB-INF/views/assignments/form.jsp".equals(view))
                req.setAttribute("createError", "Không thể tải biểu mẫu. Vui lòng thử lại.");
            else if ("/WEB-INF/views/assignments/lifecycle.jsp".equals(view))
                req.setAttribute("lifecycleError", "Không thể tải trang xác nhận. Vui lòng thử lại.");
            else if ("/WEB-INF/views/assignments/detail.jsp".equals(view))
                req.setAttribute("assignmentDetailError", "Không thể tải bài tập này. Vui lòng thử lại.");
            else
                req.setAttribute("assignmentListError", "Không thể tải danh sách bài tập. Vui lòng thử lại.");
        }
        req.getRequestDispatcher(view).forward(req, res);
    }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        HttpSession session = req.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Long)
                || session.getAttribute("accountStatus") != AccountStatus.ACTIVE) {
            res.sendError(401, "Vui lòng đăng nhập để tiếp tục."); return;
        }
        if (session.getAttribute("role") != UserRole.TEACHER) { res.sendError(403); return; }
        String action=req.getParameter("action");
        if ("restore".equals(action)) {
            res.sendError(400, "Bài tập đã hủy không thể chuyển về bản nháp."); return;
        }
        if("publish".equals(action) || "cancel".equals(action)) {
            lifecyclePost(req,res,session,action); return;
        }
        boolean editing="update".equals(req.getParameter("action"));
        if (!editing && !"create".equals(req.getParameter("action"))) {
            res.sendError(501, "Thao tác bài tập này chưa được hỗ trợ."); return;
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
                req.setAttribute("createError", "Phiên đăng nhập đã thay đổi hoặc biểu mẫu đã hết hạn. Chưa có dữ liệu nào được lưu. Nội dung bạn nhập được giữ bên dưới. Hãy kiểm tra và nhấn "+(editing?"Lưu thay đổi":"Lưu bản nháp")+" lần nữa.");
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
            res.sendError(404,"Không tìm thấy bài tập hoặc bạn không có quyền truy cập."); return;
        } catch (SecurityException ex) {
            res.sendError(403, ex.getMessage()); return;
        } catch (IllegalArgumentException ex) {
            res.sendError(400, "Lớp hoặc dữ liệu bài tập không hợp lệ."); return;
        } catch (IllegalStateException ex) {
            if(ex instanceof AssignmentEditConflictException) {
                res.setStatus(409); req.setAttribute("editConflict",true); req.setAttribute("createError",ex.getMessage());
            } else {
            getServletContext().log("Assignment draft could not be saved", ex);
            res.setStatus(503);
            req.setAttribute("createError", "Không thể lưu bản nháp. Dữ liệu bạn nhập được giữ bên dưới. Lớp và mẫu bài tập sẽ được kiểm tra lại khi bạn thử lưu.");
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
                req.setAttribute("lifecycleError","Phiên đăng nhập đã thay đổi hoặc trang xác nhận đã hết hạn. Chưa có dữ liệu nào thay đổi. Hãy kiểm tra bài tập đã lưu và xác nhận lại.");
                WebSupport.csrf(req);
                req.getRequestDispatcher("/WEB-INF/views/assignments/lifecycle.jsp").forward(req,res);
                return;
            }
            if("publish".equals(action)) {
                service.publishAssignment(teacherId,assignmentId,req.getParameter("revision"));
                session.setAttribute("publishedAssignmentId",assignmentId);
            } else if("cancel".equals(action)) {
                service.cancelAssignment(teacherId,assignmentId,req.getParameter("revision"));
                session.setAttribute("cancelledAssignmentId",assignmentId);
            }
            // A new lifecycle action supersedes earlier notices for this assignment.
            String currentNotice="publish".equals(action)?"publishedAssignmentId":"cancelledAssignmentId";
            for(String key:List.of("createdAssignmentId","updatedAssignmentId","publishedAssignmentId",
                    "cancelledAssignmentId","restoredAssignmentId"))
                if(!key.equals(currentNotice) && Long.valueOf(assignmentId).equals(session.getAttribute(key)))
                    session.removeAttribute(key);
            String detail=AssignmentDetailView.listUrl(req.getContextPath(),page.detail().assignment().classId(),filters)
                    +"&mode=view&assignmentId="+assignmentId;
            res.sendRedirect(detail); return;
        } catch(ValidationException ex) {
            res.setStatus(400);
            req.setAttribute("lifecycleErrors",ex.errors());
            req.setAttribute("lifecycleError","Bài tập chưa được thay đổi. Hãy sửa thông tin đã lưu hoặc mở lại trang xác nhận rồi thử lại.");
        } catch(NoSuchElementException ex) {
            res.sendError(404,"Không tìm thấy bài tập hoặc bạn không có quyền truy cập."); return;
        } catch(SecurityException ex) {
            res.sendError(403,ex.getMessage()); return;
        } catch(IllegalArgumentException ex) {
            res.sendError(400,"Bài tập hoặc bộ lọc không hợp lệ."); return;
        } catch(IllegalStateException ex) {
            if(ex instanceof AssignmentEditConflictException) {
                res.setStatus(409); req.setAttribute("lifecycleError",ex.getMessage());
            } else {
                getServletContext().log("Assignment lifecycle action could not be saved",ex);
                res.setStatus(503);
                req.setAttribute("lifecycleError","Không thể lưu thao tác. Hãy kiểm tra bài tập hiện tại và thử lại.");
            }
        }
        // A stale confirmation must show the latest saved state before an explicit retry.
        try { req.setAttribute("lifecyclePage",service.lifecyclePage(teacherId,assignmentId,action)); }
        catch(NoSuchElementException ex) { res.sendError(404,"Không tìm thấy bài tập hoặc bạn không có quyền truy cập."); return; }
        catch(SecurityException ex) { res.sendError(403,ex.getMessage()); return; }
        catch(IllegalStateException ex) {
            req.removeAttribute("lifecyclePage"); res.setStatus(503);
            req.setAttribute("lifecycleError","Không thể tải phiên bản bài tập mới nhất. Hãy mở lại trang xác nhận khi kết nối dữ liệu hoạt động.");
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
                    "Mẫu đã chọn #" + templateId, "", BigDecimal.ZERO));
        } catch (IllegalArgumentException ex) {
            // No valid template was submitted; the normal required selection remains blank.
        }
        return new AssignmentCreatePage(classId, 0, "Lớp #" + classId,
                "Thông tin lớp tạm thời không khả dụng", "", "", templates);
    }
}
