package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.service.MasterDataService;
import vn.edu.fpt.ams.util.WebSupport;

/** UC10 Setting List. Subject Settings delegates to the existing UC13 list. */
@WebServlet("/admin/settings")
public class SettingsServlet extends HttpServlet {
    private final MasterDataService service = new MasterDataService();
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        if ("subjects".equals(req.getParameter("tab"))) {
            res.sendRedirect(req.getContextPath() + "/admin/subjects");
            return;
        }
        try {
            long actor = WebSupport.actor(req);
            String status = req.getParameter("status");
            Boolean active = null;
            if ("ACTIVE".equals(status)) active = Boolean.TRUE;
            else if ("INACTIVE".equals(status)) active = Boolean.FALSE;
            req.setAttribute("settingsTab", "terms");
            req.setAttribute("terms", service.listTerms(actor, req.getParameter("keyword"), active));
            WebSupport.csrf(req);
            req.getRequestDispatcher("/WEB-INF/views/settings/list.jsp").forward(req,res);
        } catch (RuntimeException e) {
            WebSupport.failure(req, res, e);
        }
    }
}
