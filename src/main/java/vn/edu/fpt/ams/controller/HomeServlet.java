package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/home")
public class HomeServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setHeader("Cache-Control", "no-store");
    var session = request.getSession(false);
    Object role = session == null ? null : session.getAttribute("role");
    response.sendRedirect(
        request.getContextPath()
            + (role instanceof vn.edu.fpt.ams.model.UserRole r
                ? vn.edu.fpt.ams.util.AuthSession.landing(r)
                : "/auth/signin"));
  }
}
