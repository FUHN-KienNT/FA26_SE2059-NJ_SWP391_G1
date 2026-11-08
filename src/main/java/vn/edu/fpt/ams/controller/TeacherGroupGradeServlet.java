package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.List;
import vn.edu.fpt.ams.dto.TeacherSubmissionFilter;
import vn.edu.fpt.ams.service.GroupGradeService;
import vn.edu.fpt.ams.service.TeacherSubmissionService;
import vn.edu.fpt.ams.util.TeacherSubmissionNavigation;
import vn.edu.fpt.ams.util.WebSupport;

/** Explicit teacher review/publication of a shared Objective score. */
@WebServlet("/teacher/group-grades")
public class TeacherGroupGradeServlet extends HttpServlet {
    private final GroupGradeService grades;
    private final TeacherSubmissionService submissions;
    public TeacherGroupGradeServlet() { this(new GroupGradeService(),new TeacherSubmissionService()); }
    TeacherGroupGradeServlet(GroupGradeService grades,TeacherSubmissionService submissions) {
        this.grades=grades;this.submissions=submissions;
    }

    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res) throws ServletException,IOException {
        req.setCharacterEncoding("UTF-8");res.setHeader("Cache-Control","no-store");
        long actor=0,assignment=0,submission=0;
        try {
            for(var values:req.getParameterMap().values()) if(values==null || values.length!=1)
                throw new IllegalArgumentException("Biểu mẫu có trường bị lặp.");
            WebSupport.checkCsrf(req);actor=WebSupport.actor(req);
            assignment=WebSupport.id(req.getParameter("assignmentId"));submission=WebSupport.id(req.getParameter("submissionId"));
            String action=req.getParameter("action");
            if(List.of("publish","revise","restore").contains(action==null?"":action)
                    && !"yes".equals(req.getParameter("confirmRelease")))
                throw new IllegalArgumentException("Vui lòng xác nhận công bố điểm.");
            grades.objectiveGrade(actor,assignment,submission,revision(req.getParameter("revision")),action,
                    req.getParameter("score"),req.getParameter("feedback"),req.getParameter("changeReason"));
            req.getSession().setAttribute("actionNotice","Đã cập nhật điểm bài nhóm và kết quả từng thành viên.");
            res.setStatus(303);res.setHeader("Location",TeacherSubmissionNavigation.detailUrl(req.getContextPath(),assignment,submission,filter(req)));
        } catch(IllegalArgumentException | ConcurrentModificationException ex) {
            if(actor==0 || assignment==0 || submission==0) {WebSupport.failure(req,res,ex);return;}
            try {
                req.setAttribute("submissionDetail",submissions.detailPage(actor,assignment,submission));
                req.setAttribute("submissionFilters",filter(req));
                req.setAttribute("groupGradeError",ex.getMessage());
                req.setAttribute("groupGradeConflict",ex instanceof ConcurrentModificationException);
                req.setAttribute("groupScoreInput",req.getParameter("score"));
                req.setAttribute("groupFeedbackInput",req.getParameter("feedback"));
                req.setAttribute("groupReasonInput",req.getParameter("changeReason"));
                res.setStatus(ex instanceof ConcurrentModificationException?409:400);
                req.getRequestDispatcher("/WEB-INF/views/submissions/detail.jsp").forward(req,res);
            } catch(RuntimeException denied) {WebSupport.failure(req,res,denied);}
        } catch(RuntimeException ex) {WebSupport.failure(req,res,ex);}
    }

    private static TeacherSubmissionFilter filter(HttpServletRequest req) {
        return TeacherSubmissionFilter.from(req.getParameter("search"),req.getParameter("status"),req.getParameter("page"));
    }

    private static long revision(String raw) {
        if(raw==null || !raw.matches("0|[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Phiên bản điểm không hợp lệ.");
        try{return Long.parseLong(raw);}catch(NumberFormatException ex){throw new IllegalArgumentException("Phiên bản điểm không hợp lệ.");}
    }
}
