package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import vn.edu.fpt.ams.model.SettingType;
import vn.edu.fpt.ams.service.SystemSettingService;
import vn.edu.fpt.ams.util.WebSupport;

/** Shared system choices, independent of Academic Terms and Subjects. */
@WebServlet("/admin/settings")
public class SettingsServlet extends HttpServlet {
  private final SystemSettingService service;

  public SettingsServlet() { this(new SystemSettingService()); }

  SettingsServlet(SystemSettingService service) { this.service = Objects.requireNonNull(service); }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    String tab = req.getParameter("tab");
    if ("terms".equals(tab)) {
      res.sendRedirect(req.getContextPath() + "/admin/terms");
      return;
    }
    if ("subjects".equals(tab)) {
      res.sendRedirect(req.getContextPath() + "/admin/subjects");
      return;
    }
    try {
      long actor = WebSupport.actor(req);
      String mode = req.getParameter("mode");
      if (mode == null || mode.isBlank()) {
        prepareList(req, actor);
        req.getRequestDispatcher("/WEB-INF/views/settings/list.jsp").forward(req, res);
      } else if ("view".equals(mode) || "edit".equals(mode)) {
        req.setAttribute("mode", mode);
        req.setAttribute("setting", service.detail(actor, WebSupport.id(req.getParameter("id"))));
        WebSupport.csrf(req);
        req.getRequestDispatcher("/WEB-INF/views/settings/detail.jsp").forward(req, res);
      } else {
        throw new IllegalArgumentException("Màn hình thiết lập không hợp lệ.");
      }
    } catch (RuntimeException e) {
      WebSupport.failure(req, res, e);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    req.setCharacterEncoding("UTF-8");
    try {
      WebSupport.checkCsrf(req);
      long actor = WebSupport.actor(req);
      long id = WebSupport.id(req.getParameter("id"));
      String action = req.getParameter("action");
      if ("update".equals(action)) {
        service.update(actor, id, req.getParameter("name"), priority(req.getParameter("priority")));
      } else if ("status".equals(action)) {
        String raw = req.getParameter("active");
        if (!"true".equals(raw) && !"false".equals(raw))
          throw new IllegalArgumentException("Trạng thái thiết lập không hợp lệ.");
        service.setActive(actor, id, Boolean.parseBoolean(raw));
      } else {
        throw new IllegalArgumentException("Thao tác thiết lập không hợp lệ.");
      }
      if ("status".equals(action) && "list".equals(req.getParameter("returnTo"))) {
        res.sendRedirect(req.getContextPath() + "/admin/settings?saved=1"
            + queryParameter(req, "keyword") + queryParameter(req, "type")
            + queryParameter(req, "status"));
      } else {
        res.sendRedirect(req.getContextPath() + "/admin/settings?mode=view&id=" + id + "&saved=1");
      }
    } catch (IllegalArgumentException e) {
      redisplay(req, res, e);
    } catch (RuntimeException e) {
      WebSupport.failure(req, res, e);
    }
  }

  private void prepareList(HttpServletRequest req, long actor) {
    SettingType type = WebSupport.option(SettingType.class, req.getParameter("type"));
    String status = req.getParameter("status");
    Boolean active = null;
    if ("ACTIVE".equals(status)) active = true;
    else if ("INACTIVE".equals(status)) active = false;
    else if (status != null && !status.isBlank())
      throw new IllegalArgumentException("Trạng thái thiết lập không hợp lệ.");
    req.setAttribute("settings", service.list(actor, req.getParameter("keyword"), type, active));
    WebSupport.csrf(req);
  }

  private void redisplay(HttpServletRequest req, HttpServletResponse res, IllegalArgumentException error)
      throws ServletException, IOException {
    try {
      long actor = WebSupport.actor(req);
      req.setAttribute("error", error.getMessage());
      if ("status".equals(req.getParameter("action"))
          && "list".equals(req.getParameter("returnTo"))) {
        prepareList(req, actor);
        res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        req.getRequestDispatcher("/WEB-INF/views/settings/list.jsp").forward(req, res);
        return;
      }
      req.setAttribute("mode", "update".equals(req.getParameter("action")) ? "edit" : "view");
      req.setAttribute("setting", service.detail(actor, WebSupport.id(req.getParameter("id"))));
      req.setAttribute("submitted", "update".equals(req.getParameter("action")));
      WebSupport.csrf(req);
      res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      req.getRequestDispatcher("/WEB-INF/views/settings/detail.jsp").forward(req, res);
    } catch (RuntimeException retry) {
      WebSupport.failure(req, res, retry);
    }
  }

  private static int priority(String value) {
    try { return Integer.parseInt(value); }
    catch (RuntimeException e) {
      throw new IllegalArgumentException("Độ ưu tiên phải là số nguyên từ 1 đến 9999.");
    }
  }

  private static String queryParameter(HttpServletRequest req, String name) {
    String value = req.getParameter(name);
    return value == null || value.isBlank() ? ""
        : "&" + name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
