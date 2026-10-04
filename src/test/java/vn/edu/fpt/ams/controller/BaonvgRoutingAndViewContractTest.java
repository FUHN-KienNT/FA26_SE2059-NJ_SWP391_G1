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

  @Test
  void baoSettingsNavigationKeepsDomainsSeparateWithoutDuplicateTabs() throws Exception {
    String shell = Files.readString(VIEWS.resolve("admin/shell.jspf"));
    assertTrue(shell.contains("bao-settings-children"));
    assertTrue(shell.contains("boolean baoSubjects"));
    assertTrue(shell.contains("boolean baoTerms"));
    assertTrue(shell.contains("bao-settings-label"));
    assertFalse(shell.contains("<details"));
    assertFalse(shell.contains("<summary"));
    assertFalse(shell.contains("⌄"));
    assertFalse(shell.contains("ams-sidebar-note"));
    assertTrue(shell.contains("<span>Quản trị viên</span>"));
    for (String relative : List.of("settings/list.jsp", "master-data/detail.jsp",
        "subjects/list.jsp", "subjects/detail.jsp", "subjects/form.jsp", "subjects/experts.jsp",
        "subjects/status.jsp", "subjects/term.jsp")) {
      String view = Files.readString(VIEWS.resolve(relative));
      assertFalse(view.contains("aria-label=\"Loại thiết lập\""), relative);
      assertTrue(view.contains("class=\"bao-context\""), relative);
    }
  }

  @Test
  void academicTermRedesignKeepsExistingPostFieldsAndReadonlyStatus() throws Exception {
    String view = Files.readString(VIEWS.resolve("master-data/detail.jsp"));
    for (String field : List.of("termCode", "termName", "startsAt", "endsAt", "termId", "csrf"))
      assertTrue(view.contains("name=\"" + field + "\""), field);
    assertTrue(view.contains("bao-term-layout"));
    assertTrue(view.contains("bao-state-list"));
    assertTrue(view.contains("if(create)"));
    assertTrue(view.contains("bao-focused-form"));
    assertFalse(view.contains("bao-callout"));
    assertTrue(view.contains("Quay lại chi tiết"));
    assertTrue(view.contains("view?\"Chi tiết\":\"Chỉnh sửa\""));
    assertTrue(view.contains("create?list:detail"));
    assertTrue(view.contains("name=\"active\" value=\"true\""));
    assertTrue(view.contains("name=\"current\" value=\"true\""));
    assertTrue(view.contains("/admin/master-data"));
  }

  @Test
  void allAdminEntryScreensShareTheSameNavigationComponent() throws Exception {
    for (String relative : List.of("admin/dashboard.jsp", "users/list.jsp", "users/detail.jsp",
        "settings/list.jsp", "master-data/detail.jsp", "subjects/list.jsp")) {
      String view = Files.readString(VIEWS.resolve(relative));
      assertTrue(view.contains("/WEB-INF/views/admin/shell.jspf"), relative);
      assertFalse(view.contains("<aside"), relative);
    }
    String shell = Files.readString(VIEWS.resolve("admin/shell.jspf"));
    assertTrue(shell.contains("ams-admin-header"));
    assertTrue(shell.contains("ams-admin-layout"));
    assertTrue(shell.contains("ams-admin-sidebar"));
  }

  private static void assertRoute(Class<?> servlet, String route) {
    WebServlet mapping = servlet.getAnnotation(WebServlet.class);
    assertNotNull(mapping, servlet.getSimpleName());
    assertTrue(Arrays.asList(mapping.value()).contains(route),
        servlet.getSimpleName() + " must map " + route);
  }
}
