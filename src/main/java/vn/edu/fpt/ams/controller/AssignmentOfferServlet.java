package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import vn.edu.fpt.ams.dto.AssignmentOfferView;
import vn.edu.fpt.ams.model.AssignmentOfferStatus;
import vn.edu.fpt.ams.service.AssignmentOfferService;
import vn.edu.fpt.ams.util.WebSupport;

/** Teacher list, history and verified accept/decline actions for additional class offers. */
@WebServlet("/teacher/offers")
public class AssignmentOfferServlet extends HttpServlet {
    private final AssignmentOfferService service;
    /** Use the application's database-backed consent workflow. */
    public AssignmentOfferServlet() { this(new AssignmentOfferService()); }
    /** Supply a consent boundary for servlet security tests. */
    AssignmentOfferServlet(AssignmentOfferService service) { this.service=service; }

    /** Show only the signed-in Teacher's offers and consume any response notice once. */
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res) throws ServletException,IOException {
        res.setHeader("Cache-Control","no-store");
        try {
            long teacher=WebSupport.actor(req);
            String keyword=filter(req.getParameter("keyword"));
            var status=WebSupport.option(AssignmentOfferStatus.class,req.getParameter("status"));
            var all=service.list(teacher);
            req.setAttribute("offers",all.stream().filter(item->status==null||item.status()==status)
                    .filter(item->matches(item,keyword)).toList());
            req.setAttribute("keyword",keyword);
            req.setAttribute("status",status);
            req.setAttribute("pendingCount",all.stream().filter(item->item.status()==AssignmentOfferStatus.PENDING).count());
            Object notice=req.getSession().getAttribute("offerNotice");
            req.getSession().removeAttribute("offerNotice");
            req.setAttribute("notice",notice);
            req.getRequestDispatcher("/WEB-INF/views/teacher/offers.jsp").forward(req,res);
        } catch(RuntimeException ex) { WebSupport.failure(req,res,ex); }
    }

    /** Verify CSRF and act as the session recipient, never a submitted teacher identifier. */
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res) throws IOException {
        req.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control","no-store");
        try {
            long teacher=WebSupport.actor(req);
            WebSupport.checkCsrf(req);
            var decision=WebSupport.option(AssignmentOfferStatus.class,req.getParameter("decision"));
            var result=service.respond(teacher,WebSupport.id(req.getParameter("offerId")),decision,req.getParameter("note"));
            String notice=switch(result) {
                case ACCEPTED -> "Đã nhận lời mời. Lớp bổ sung đã được hoàn tất và xuất hiện trong Lớp phụ trách.";
                case DECLINED -> "Đã từ chối lời mời. Yêu cầu được giữ lại để chuyên gia chính tiếp tục xử lý.";
                case EXPIRED -> "Lời mời đã hết hạn, không thể phản hồi. Yêu cầu được giữ lại để chuyên gia chính xử lý.";
            };
            req.getSession().setAttribute("offerNotice",notice);
            res.sendRedirect(req.getContextPath()+"/teacher/offers");
        } catch(RuntimeException ex) { WebSupport.failure(req,res,ex); }
    }

    /** Bound and normalize the optional list search. */
    private static String filter(String raw) {
        String value=raw==null?"":raw.strip();
        if(value.length()>100) throw new IllegalArgumentException("Từ khóa tối đa 100 ký tự.");
        return value;
    }

    /** Search source/additional class, subject, term, sender and displayed status. */
    private static boolean matches(AssignmentOfferView item,String keyword) {
        String text=String.join(" ",item.sourceClassCode(),item.subjectCode(),item.subjectName(),item.termCode(),
                item.offeredByName(),item.createdClassCode()==null?"":item.createdClassCode(),
                AssignmentOfferService.statusLabel(item.status()));
        return text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }
}
