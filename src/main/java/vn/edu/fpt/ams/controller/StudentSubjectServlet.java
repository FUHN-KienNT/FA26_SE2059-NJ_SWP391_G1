package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.StudentSubjectService;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet("/student/subjects")
public class StudentSubjectServlet extends HttpServlet {
    private final StudentSubjectService service;
    public StudentSubjectServlet(){this(new StudentSubjectService());}
    StudentSubjectServlet(StudentSubjectService service){this.service=service;}
    private long student(HttpServletRequest req){
        var s=req.getSession(false);
        if(s==null||s.getAttribute("role")!=UserRole.STUDENT||s.getAttribute("accountStatus")!=AccountStatus.ACTIVE)
            throw new SecurityException("Vui lòng đăng nhập bằng tài khoản sinh viên đang hoạt động.");
        return WebSupport.actor(req);
    }
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res)throws ServletException,IOException{
        res.setHeader("Cache-Control","no-store");
        try{
            long actor=student(req);
            if(req.getParameter("id")!=null){
                req.setAttribute("offering",service.detail(actor,WebSupport.id(req.getParameter("id"))));
                req.getRequestDispatcher("/WEB-INF/views/student/subject-detail.jsp").forward(req,res);
            }else{
                req.setAttribute("subjects",service.list(actor,req.getParameter("view"),req.getParameter("q")));
                req.getRequestDispatcher("/WEB-INF/views/student/subjects.jsp").forward(req,res);
            }
        }catch(RuntimeException ex){WebSupport.failure(req,res,ex);}
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res)throws ServletException,IOException{
        res.setHeader("Cache-Control","no-store");
        try{
            long actor=student(req);WebSupport.checkCsrf(req);
            if(!"register".equals(req.getParameter("action")))throw new IllegalArgumentException("Thao tác môn học không hợp lệ.");
            long offering=WebSupport.id(req.getParameter("id"));
            try{service.register(actor,offering);}
            catch(IllegalArgumentException ex){
                req.setAttribute("error",ex.getMessage());req.setAttribute("offering",service.detail(actor,offering));
                res.setStatus(400);req.getRequestDispatcher("/WEB-INF/views/student/subject-detail.jsp").forward(req,res);return;
            }
            res.sendRedirect(req.getContextPath()+"/student/subjects?id="+offering+"&registered=1");
        }catch(RuntimeException ex){WebSupport.failure(req,res,ex);}
    }
}
