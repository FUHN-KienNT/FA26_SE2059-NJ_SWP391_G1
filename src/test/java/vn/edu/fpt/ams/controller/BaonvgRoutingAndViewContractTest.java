package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.annotation.WebServlet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaonvgRoutingAndViewContractTest {
  private static final Path VIEWS = Path.of("src/main/webapp/WEB-INF/views");

  @Test
  void expertDashboardAndAssignedClassesKeepTheirRoutes() {
    assertRoute(RoleDashboardServlet.class, "/expert/dashboard");
    assertRoute(SubjectClassServlet.class, "/expert/classes");
  }

  @Test
  void settingsAndMasterDataKeepTheirRoutes() {
    assertRoute(SettingsServlet.class, "/admin/settings");
    assertRoute(MasterDataServlet.class, "/admin/master-data");
  }

  @Test
  void classDropdownsSubmitTheSameFormAndPreserveAllFilters() throws Exception {
    String view = Files.readString(VIEWS.resolve("subject-classes/list.jsp"));
    assertTrue(view.contains("name=\"keyword\""));
    assertTrue(view.contains("value=\"<%=WebSupport.html(request.getParameter(\"keyword\"))%>\""));
    assertTrue(view.contains("name=\"subjectId\" onchange=\"this.form.requestSubmit()\""));
    assertTrue(view.contains("name=\"termId\" onchange=\"this.form.requestSubmit()\""));
    assertTrue(view.contains("String.valueOf(subject.subjectId()).equals(selectedSubject)?\"selected\":\"\""));
    assertTrue(view.contains("String.valueOf(term.termId()).equals(selectedTerm)?\"selected\":\"\""));
    assertTrue(view.contains("Không có lớp phù hợp với bộ lọc hiện tại."));
  }

  @Test
  void settingsExplainAcademicTermFilterScope() throws Exception {
    String view = Files.readString(VIEWS.resolve("settings/list.jsp"));
    assertTrue(view.contains("Trạng thái học kỳ"));
    assertTrue(view.contains("Bộ lọc này chỉ áp dụng cho học kỳ"));
    assertTrue(view.contains("Không có học kỳ phù hợp."));
  }

  @Test
  void allBaonvgAdminViewsUseTheSharedShell() throws Exception {
    for (String relative : List.of(
        "settings/list.jsp", "master-data/detail.jsp", "subjects/list.jsp",
        "subjects/detail.jsp", "subjects/form.jsp", "subjects/experts.jsp",
        "subjects/status.jsp", "subjects/term.jsp")) {
      String view = Files.readString(VIEWS.resolve(relative));
      assertTrue(view.contains("/WEB-INF/views/admin/shell.jspf"), relative);
    }
    assertFalse(Files.exists(VIEWS.resolve("master-data/list.jsp")));
  }

  private static void assertRoute(Class<?> servlet, String route) {
    WebServlet mapping = servlet.getAnnotation(WebServlet.class);
    assertNotNull(mapping, servlet.getSimpleName());
    assertTrue(Arrays.asList(mapping.value()).contains(route),
        servlet.getSimpleName() + " must map " + route);
  }
}
