package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import vn.edu.fpt.ams.model.AssignmentOfferStatus;
import vn.edu.fpt.ams.service.AssignmentOfferService;

/** Giảng viên xem lời mời dạy lớp bổ sung và nhận hoặc từ chối lời mời còn hạn. */
@WebServlet("/teacher/offers")
public class AssignmentOfferServlet extends HttpServlet {
    private final AssignmentOfferService service = new AssignmentOfferService();

    /** Mở danh sách lời mời của giảng viên trong phiên, có lọc theo từ khóa. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        long teacherId = (Long) request.getSession().getAttribute("userId");
        String query = request.getParameter("q");
        try {
            request.setAttribute("offers", service.list(teacherId, query));
            request.setAttribute("query", query == null ? "" : query);
            Object notice = request.getSession().getAttribute("offerNotice");
            if (notice != null) {
                request.setAttribute("notice", notice);
                request.getSession().removeAttribute("offerNotice");
            }
            request.getRequestDispatcher("/WEB-INF/views/teacher/offers.jsp").forward(request, response);
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
        }
    }

    /** Ghi nhận hoặc từ chối một lời mời rồi quay lại danh sách. */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        long teacherId = (Long) request.getSession().getAttribute("userId");
        String query = request.getParameter("q");
        try {
            String rawDecision = request.getParameter("decision");
            if (rawDecision == null || rawDecision.isBlank()) {
                throw new IllegalArgumentException("Chọn nhận hoặc từ chối");
            }
            AssignmentOfferStatus decision;
            long offerId;
            try {
                decision = AssignmentOfferStatus.valueOf(rawDecision);
                offerId = Long.parseLong(request.getParameter("offerId"));
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new IllegalArgumentException("Lời mời không hợp lệ");
            }
            service.respond(teacherId, offerId, decision, request.getParameter("note"));
            request.getSession().setAttribute("offerNotice",
                    decision == AssignmentOfferStatus.ACCEPTED ? "Bạn đã nhận lời mời dạy lớp bổ sung."
                            : "Bạn đã từ chối lời mời dạy lớp bổ sung.");
            response.sendRedirect(request.getContextPath() + "/teacher/offers" + querySuffix(query));
        } catch (IllegalArgumentException ex) {
            request.setAttribute("error", ex.getMessage());
            request.setAttribute("offers", service.list(teacherId, query));
            request.setAttribute("query", query == null ? "" : query);
            request.getRequestDispatcher("/WEB-INF/views/teacher/offers.jsp").forward(request, response);
        }
    }

    /** Ghép từ khóa tìm kiếm vào địa chỉ quay lại, bỏ qua khi không có từ khóa. */
    private static String querySuffix(String query) {
        if (query == null || query.isBlank()) return "";
        return "?q=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
    }
}
