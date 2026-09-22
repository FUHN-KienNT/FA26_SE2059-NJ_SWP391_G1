package vn.edu.fpt.ams.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.model.*;

@WebFilter(urlPatterns={"/admin/*", "/expert/*", "/teacher/*", "/student/*"})
public class AuthFilter implements Filter {
    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        HttpSession session = req.getSession(false);
        Object id = session == null ? null : session.getAttribute("userId");
        Object role = session == null ? null : session.getAttribute("role");
        Object status = session == null ? null : session.getAttribute("accountStatus");
        if (!(id instanceof Long) || !(role instanceof UserRole) || status != AccountStatus.ACTIVE) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sign-in is required"); return;
        }
        String path = req.getServletPath();
        String area = path.substring(1, path.indexOf('/', 1));
        if (!((area.equals("admin") && role == UserRole.ADMIN) || (area.equals("expert") && role == UserRole.EXPERT)
                || (area.equals("teacher") && role == UserRole.TEACHER) || (area.equals("student") && role == UserRole.STUDENT))) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN); return;
        }
        chain.doFilter(request, response);
    }
}
