package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.service.TemplateService;
import vn.edu.fpt.ams.service.SubjectClassService;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet("/expert/templates")
public class TemplateServlet extends HttpServlet {
    private final TemplateService service = new TemplateService();
    private final SubjectClassService classes = new SubjectClassService();
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String view = "/WEB-INF/views/assignment-templates/list.jsp";
        try {
            String mode = req.getParameter("mode");
            if ("view".equals(mode) || "create".equals(mode) || "edit".equals(mode) || "status".equals(mode)) {
                view = "/WEB-INF/views/assignment-templates/detail.jsp";
                req.setAttribute("mode", mode);
            } else {
                long actor=WebSupport.actor(req);
                req.setAttribute("account",new UserDAO().findById(actor).orElseThrow());
                req.setAttribute("expertPage","templates");
                Long subjectId=optionalId(req.getParameter("subjectId"));
                req.setAttribute("templates", service.listVisible(actor,subjectId));
                req.setAttribute("subjects", classes.listAssignedSubjects(actor));
            }
        } catch (UnsupportedOperationException | IllegalArgumentException ex) {
            req.setAttribute("featurePending", "Data loading is pending in this initial iteration.");
        }
        req.getRequestDispatcher(view).forward(req, res);
    }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // Mutations remain unavailable until ownership checks and JDBC transactions are implemented.
        res.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "TODO: complete SDS workflow before enabling writes");
    }
    private static Long optionalId(String value){
        if(value==null||value.isBlank()) return null;
        try{long id=Long.parseLong(value); if(id<=0) throw new NumberFormatException(); return id;}
        catch(NumberFormatException e){throw new IllegalArgumentException("Invalid Subject filter");}
    }
}
