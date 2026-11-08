package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import vn.edu.fpt.ams.service.GroupGradeService;
import vn.edu.fpt.ams.util.WebSupport;

/** The peer review form never receives another member's submitted ratings. */
@WebServlet("/student/peer-reviews")
public class PeerReviewServlet extends HttpServlet {
    private final GroupGradeService service;
    public PeerReviewServlet() { this(new GroupGradeService()); }
    PeerReviewServlet(GroupGradeService service) { this.service=service; }

    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res) throws ServletException,IOException {
        res.setHeader("Cache-Control","no-store");
        try {
            singleton(req);
            show(req,WebSupport.actor(req));
            req.getRequestDispatcher("/WEB-INF/views/student/peer-reviews.jsp").forward(req,res);
        } catch(RuntimeException ex) { WebSupport.failure(req,res,ex); }
    }

    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res) throws ServletException,IOException {
        req.setCharacterEncoding("UTF-8");res.setHeader("Cache-Control","no-store");
        try {
            singleton(req);WebSupport.checkCsrf(req);
            long actor=WebSupport.actor(req),target=WebSupport.id(req.getParameter("targetId"));
            Map<Long,GroupGradeService.ReviewInput> input=new LinkedHashMap<>();
            for(String key:req.getParameterMap().keySet()) if(key.startsWith("score_")) {
                long peer=WebSupport.id(key.substring(6));String score=req.getParameter(key);
                if(score==null || score.isBlank()) continue;
                input.put(peer,new GroupGradeService.ReviewInput(score,req.getParameter("feedback_"+peer),
                        revision(req.getParameter("revision_"+peer))));
            }
            service.savePeerReviews(actor,target,input);
            req.getSession().setAttribute("actionNotice","Đã lưu đánh giá đồng đội.");
            res.setStatus(303);res.setHeader("Location",req.getContextPath()+"/student/peer-reviews?targetId="+target
                    +"&page="+WebSupport.page(req.getParameter("page"))+"&reviewPage="+WebSupport.page(req.getParameter("reviewPage")));
        } catch(IllegalArgumentException | java.util.ConcurrentModificationException ex) {
            res.setStatus(ex instanceof java.util.ConcurrentModificationException?409:400);
            req.setAttribute("peerError",ex.getMessage());
            req.setAttribute("peerConflict",ex instanceof java.util.ConcurrentModificationException);
            req.setAttribute("peerInput",req.getParameterMap());
            try { show(req,WebSupport.actor(req));req.getRequestDispatcher("/WEB-INF/views/student/peer-reviews.jsp").forward(req,res); }
            catch(RuntimeException denied) { WebSupport.failure(req,res,denied); }
        } catch(RuntimeException ex) { WebSupport.failure(req,res,ex); }
    }

    private void show(HttpServletRequest req,long actor) {
        String raw=req.getParameter("targetId");
        req.setAttribute("peerPage",service.peerPage(actor,raw==null?null:WebSupport.id(raw),
                WebSupport.page(req.getParameter("page")),WebSupport.page(req.getParameter("reviewPage"))));
    }

    private static long revision(String value) {
        if(value==null || !value.matches("0|[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Phiên bản đánh giá không hợp lệ.");
        try{return Long.parseLong(value);}catch(NumberFormatException ex){throw new IllegalArgumentException("Phiên bản đánh giá không hợp lệ.");}
    }

    private static void singleton(HttpServletRequest req) {
        for(var values:req.getParameterMap().values()) if(values==null || values.length!=1)
            throw new IllegalArgumentException("Biểu mẫu có trường bị lặp.");
    }
}
