package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.StudentObjectiveService;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet("/student/assignments")
public class StudentAssignmentServlet extends HttpServlet {
    private final StudentObjectiveService service;
    public StudentAssignmentServlet() { this(new StudentObjectiveService()); }
    StudentAssignmentServlet(StudentObjectiveService service) { this.service=service; }

    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res) throws ServletException,IOException {
        res.setHeader("Cache-Control","no-store");
        try {
            long student=student(req);
            String assignmentId=req.getParameter("assignmentId");
            if(assignmentId==null) req.setAttribute("assignments",service.list(student));
            else req.setAttribute("assignmentPage",service.page(student,WebSupport.id(assignmentId),
                    req.getParameter("submissionId")==null?0:WebSupport.id(req.getParameter("submissionId"))));
            req.getRequestDispatcher("/WEB-INF/views/student/objective.jsp").forward(req,res);
        } catch(RuntimeException ex) { WebSupport.failure(req,res,ex); }
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res) throws ServletException,IOException {
        req.setCharacterEncoding("UTF-8");res.setHeader("Cache-Control","no-store");
        try {
            long student=student(req);WebSupport.checkCsrf(req);
            long assignment=WebSupport.id(req.getParameter("assignmentId"));
            long submission;
            if("start".equals(req.getParameter("action"))) submission=service.start(student,assignment);
            else if("save".equals(req.getParameter("action"))) {
                var saved=service.save(student,assignment,WebSupport.id(req.getParameter("submissionId")),revision(req),answers(req));
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"finished\":"+saved.finished()+",\"serverTime\":"+saved.serverTime().toEpochMilli()
                        +",\"deadline\":"+saved.deadline().toEpochMilli()+",\"revision\":"+saved.revision()+"}");
                return;
            }
            else if("submit".equals(req.getParameter("action"))) {
                long id=WebSupport.id(req.getParameter("submissionId"));
                try { submission=service.submit(student,assignment,id,revision(req),answers(req)); }
                catch(IllegalArgumentException ex) {
                    res.setStatus(400);req.setAttribute("formError",ex.getMessage());
                    req.setAttribute("assignmentPage",service.page(student,assignment,id));
                    req.getRequestDispatcher("/WEB-INF/views/student/objective.jsp").forward(req,res);return;
                }
            } else throw new IllegalArgumentException("Thao tác bài tập không hợp lệ.");
            res.setStatus(303);
            res.setHeader("Location",req.getContextPath()+"/student/assignments?assignmentId="+assignment+"&submissionId="+submission);
        } catch(ConcurrentModificationException ex) { res.sendError(409,ex.getMessage()); }
        catch(RuntimeException ex) { WebSupport.failure(req,res,ex); }
    }
    private static long revision(HttpServletRequest req) {
        try {
            String value=req.getParameter("answerRevision");
            if(value==null || !value.matches("[0-9]{1,18}"))throw new IllegalArgumentException();
            return Long.parseLong(value);
        } catch(RuntimeException ex) { throw new IllegalArgumentException("Phiên bản đáp án không hợp lệ. Vui lòng tải lại trang."); }
    }
    private static Map<Long,List<String>> answers(HttpServletRequest req) {
        Map<Long,List<String>> answers=new HashMap<>();
        req.getParameterMap().forEach((name,values)->{
            if(name.startsWith("answer_")) {
                long id=WebSupport.id(name.substring(7));
                if(answers.put(id,List.of(values))!=null) throw new IllegalArgumentException("Câu hỏi bị trùng lặp.");
            }
        });
        return answers;
    }
    private static long student(HttpServletRequest req) {
        var session=req.getSession(false);
        if(session==null || session.getAttribute("role")!=UserRole.STUDENT
                || session.getAttribute("accountStatus")!=AccountStatus.ACTIVE)
            throw new SecurityException("Vui lòng đăng nhập bằng tài khoản sinh viên đang hoạt động.");
        return WebSupport.actor(req);
    }
}
