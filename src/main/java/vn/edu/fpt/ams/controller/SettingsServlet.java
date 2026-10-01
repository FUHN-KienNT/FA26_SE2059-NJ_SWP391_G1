package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

/** Unified Settings entry; UC08 editing is implemented in the Settings owner's next task. */
@WebServlet("/admin/settings")
public class SettingsServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        String tab=req.getParameter("tab");
        req.setAttribute("settingsTab","subjects".equals(tab)?"subjects":"terms");
        req.getRequestDispatcher("/WEB-INF/views/settings/list.jsp").forward(req,res);
    }
}
