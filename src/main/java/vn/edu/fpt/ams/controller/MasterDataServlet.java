package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import vn.edu.fpt.ams.model.AcademicTerm;
import vn.edu.fpt.ams.service.MasterDataService;
import vn.edu.fpt.ams.util.Vietnamese;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet("/admin/master-data")
public class MasterDataServlet extends HttpServlet {
    private final MasterDataService service;

    public MasterDataServlet() {
        this(new MasterDataService());
    }

    MasterDataServlet(MasterDataService service) {
        this.service = Objects.requireNonNull(service);
    }
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            String mode = req.getParameter("mode");
            if (mode == null || mode.isBlank()) {
                res.sendRedirect(req.getContextPath() + "/admin/terms");
                return;
            }
            if (!Set.of("view", "create", "edit", "switch").contains(mode))
                throw new IllegalArgumentException("Màn hình học kỳ không hợp lệ.");
            long actor = WebSupport.actor(req);
            req.setAttribute("mode", mode);
            if ("create".equals(mode)) service.listTerms(actor, null, null);
            if (!"create".equals(mode))
                req.setAttribute("term", service.getTerm(actor, WebSupport.id(req.getParameter("id"))));
            if ("switch".equals(mode)) {
                res.sendRedirect(req.getContextPath() + "/admin/terms");
                return;
            }
            if ("edit".equals(mode)) {
                AcademicTerm selected = (AcademicTerm) req.getAttribute("term");
                if (!selected.endsAt().isAfter(Instant.now())) {
                    res.sendRedirect(req.getContextPath() + "/admin/master-data?mode=view&id=" + selected.termId());
                    return;
                }
            }
            WebSupport.csrf(req);
            req.getRequestDispatcher("/WEB-INF/views/master-data/detail.jsp").forward(req, res);
        } catch (RuntimeException ex) {
            WebSupport.failure(req, res, ex);
        }
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {
        try {
            WebSupport.checkCsrf(req);
            long actor = WebSupport.actor(req);
            String action = req.getParameter("action");
            long id;
            switch (action == null ? "" : action) {
                case "create" -> id = service.createTerm(actor, form(req, 0));
                case "update" -> {
                    id = WebSupport.id(req.getParameter("termId"));
                    service.updateTerm(actor, form(req, id));
                }
                case "current" -> {
                    id = WebSupport.id(req.getParameter("termId"));
                    service.markCurrent(actor, id);
                }
                case "status" -> {
                    id = WebSupport.id(req.getParameter("termId"));
                    String active = req.getParameter("active");
                    if (!"true".equals(active) && !"false".equals(active))
                        throw new IllegalArgumentException("Trạng thái học kỳ không hợp lệ.");
                    service.setTermStatus(actor, id, "true".equals(active));
                }
                case "switch" -> {
                    id = WebSupport.id(req.getParameter("termId"));
                    service.switchCurrentTerm(actor, id,
                            expectedActiveId(req.getParameter("previousTermId")));
                }
                default -> throw new IllegalArgumentException("Thao tác học kỳ không hợp lệ.");
            }
            res.sendRedirect(req.getContextPath() + "/admin/master-data?mode=view&id=" + id + "&saved=1");
        } catch (IllegalArgumentException ex) {
            redisplay(req, res, ex);
        } catch (RuntimeException ex) {
            WebSupport.failure(req, res, ex);
        }
    }

    private void redisplay(HttpServletRequest req, HttpServletResponse res, IllegalArgumentException error)
            throws ServletException, IOException {
        String action = req.getParameter("action");
        if ("create".equals(action) || "update".equals(action)) {
            req.setAttribute("mode", "create".equals(action) ? "create" : "edit");
            req.setAttribute("submittedTerm", formUnchecked(req));
            req.setAttribute("error", Vietnamese.label(error.getMessage()));
            WebSupport.csrf(req);
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            req.getRequestDispatcher("/WEB-INF/views/master-data/detail.jsp").forward(req, res);
        } else if ("status".equals(action) || "current".equals(action) || "switch".equals(action)) {
            long actor = WebSupport.actor(req);
            req.setAttribute("terms", service.listTerms(actor, null, null));
            req.setAttribute("calendarTerms", service.listTerms(actor, null, null));
            req.setAttribute("error", Vietnamese.label(error.getMessage()));
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            req.getRequestDispatcher("/WEB-INF/views/master-data/list.jsp").forward(req, res);
        } else {
            WebSupport.failure(req, res, error);
        }
    }

    private static long expectedActiveId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id > 0) return id;
        } catch (RuntimeException ignored) { }
        throw new IllegalArgumentException("Học kỳ hoạt động không hợp lệ. Vui lòng xem lại trước khi chuyển.");
    }

    private static AcademicTerm form(HttpServletRequest req, long id) {
        AcademicTerm value = formUnchecked(req);
        if (value.startsAt() == null || value.endsAt() == null)
            throw new IllegalArgumentException("Vui lòng nhập ngày bắt đầu và ngày kết thúc hợp lệ.");
        return new AcademicTerm(id, value.termCode(), value.termName(), value.startsAt(),
                value.endsAt(), value.current(), value.active());
    }

    private static AcademicTerm formUnchecked(HttpServletRequest req) {
        long id = 0;
        try { if (req.getParameter("termId") != null) id = Long.parseLong(req.getParameter("termId")); }
        catch (NumberFormatException ignored) { }
        Instant start = date(req.getParameter("startsAt"), false);
        Instant end = date(req.getParameter("endsAt"), true);
        return new AcademicTerm(id, req.getParameter("termCode"), req.getParameter("termName"),
                start, end, false, req.getParameter("active") != null);
    }

    private static Instant date(String raw, boolean endOfDay) {
        try {
            if (raw == null || raw.isBlank()) return null;
            LocalDate date = LocalDate.parse(raw);
            ZoneId zone = vn.edu.fpt.ams.util.TermTimeline.ZONE;
            return (endOfDay ? date.plusDays(1).atStartOfDay(zone).minusNanos(1000)
                    : date.atStartOfDay(zone)).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
