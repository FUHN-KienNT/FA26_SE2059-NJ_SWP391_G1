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
    assertRoute(AcademicTermServlet.class, "/admin/terms");
  }

  @Test
  void assignedSubjectsAndClassesUseTwoReadOnlyViews() throws Exception {
    String subjects = Files.readString(VIEWS.resolve("subject-classes/list.jsp"));
    String classes = Files.readString(VIEWS.resolve("subject-classes/classes.jsp"));
    assertTrue(subjects.contains("Môn học được phân công"));
    assertTrue(subjects.contains("name=\"role\""));
    assertTrue(subjects.contains("name=\"status\""));
    assertTrue(subjects.contains("mode=subject&amp;subjectId="));
    assertFalse(subjects.contains("<details"));
    assertTrue(classes.contains("name=\"subjectId\""));
    assertTrue(classes.contains("name=\"termId\""));
    assertTrue(classes.contains("name=\"keyword\""));
    assertTrue(classes.contains("String.valueOf(term.termId()).equals(termId)?\"selected\":\"\""));
    assertTrue(classes.contains("Không có lớp phù hợp"));
    assertFalse(classes.contains("<th>Đăng ký</th>"));
    assertFalse(classes.contains("mode=edit"));
  }

  @Test
  void academicTermsAndSettingsHaveSeparateLists() throws Exception {
    String view = Files.readString(VIEWS.resolve("settings/list.jsp"));
    String terms = Files.readString(VIEWS.resolve("master-data/list.jsp"));
    assertTrue(view.contains("Danh sách thiết lập"));
    assertTrue(view.contains("Tất cả nhóm"));
    assertFalse(view.contains("Học kỳ"));
    assertTrue(terms.contains("Theo lịch"));
    assertTrue(terms.contains("Không có học kỳ phù hợp."));
  }

  @Test
  void settingsViewsShowGroupTypeValueAndValidatedEditStatus() throws Exception {
    String list = Files.readString(VIEWS.resolve("settings/list.jsp"));
    String detail = Files.readString(VIEWS.resolve("settings/detail.jsp"));
    assertTrue(list.contains("name=\"action\" value=\"status\""));
    assertTrue(list.contains("setting.active()?\"Vô hiệu hóa\":\"Kích hoạt\""));
    assertTrue(list.contains("data-confirm=\"Xác nhận vô hiệu hóa thiết lập này?"));
    assertTrue(list.contains("name=\"csrf\""));
    assertTrue(detail.contains("name=\"action\" value=\"update\""));
    assertTrue(list.contains("Nhóm thiết lập"));
    assertTrue(list.contains("<th>Loại</th>"));
    assertTrue(detail.contains("Tên loại hiển thị"));
    assertTrue(detail.contains("<select name=\"active\" required>"));
    assertTrue(detail.contains("settings-edit.js"));
    assertTrue(detail.contains("setting-status-form"));
  }

  @Test
  void subjectEditSeparatesGlobalStatusFromTermRegistrationAndListOnlyOffersViewEdit() throws Exception {
    String list = Files.readString(VIEWS.resolve("subjects/list.jsp"));
    String form = Files.readString(VIEWS.resolve("subjects/form.jsp"));
    String controls = Files.readString(VIEWS.resolve("subjects/edit-controls.jspf"));
    assertFalse(list.contains("subject-tabs"));
    assertFalse(list.contains("tab=terms"));
    assertTrue(list.contains("Học kỳ đã thiết lập"));
    assertTrue(list.contains("name=\"termId\""));
    assertTrue(form.contains("subjects/create-term.jspf"));
    assertFalse(list.contains("mode=status"));
    assertFalse(list.contains("mode=term"));
    assertFalse(list.contains("mode=experts"));
    assertTrue(form.contains("subjects/edit-controls.jspf"));
    assertTrue(controls.contains("Vô hiệu hóa môn học"));
    assertTrue(controls.contains("name=\"action\" value=\"status\""));
    assertTrue(controls.contains("name=\"action\" value=\"term\""));
    assertTrue(controls.contains("name=\"action\" value=\"experts\""));
    assertTrue(controls.contains("data-subject-status"));
    assertTrue(controls.contains("name=\"csrf\""));
    assertTrue(controls.contains("tất cả học kỳ"));
    assertTrue(controls.contains("kỳ được chọn"));
  }

  @Test
  void allBaonvgAdminViewsUseTheSharedShell() throws Exception {
    for (String relative : List.of(
        "settings/list.jsp", "settings/detail.jsp", "master-data/list.jsp", "master-data/detail.jsp", "subjects/list.jsp",
        "subjects/detail.jsp", "subjects/form.jsp", "subjects/experts.jsp",
        "subjects/status.jsp", "subjects/term.jsp")) {
      String view = Files.readString(VIEWS.resolve(relative));
      assertTrue(view.contains("/WEB-INF/views/admin/shell.jspf"), relative);
    }
    assertTrue(Files.exists(VIEWS.resolve("master-data/list.jsp")));
  }

  @Test
  void adminNavigationHighlightsIndependentModules() throws Exception {
    String shell = Files.readString(VIEWS.resolve("admin/shell.jspf"));
    assertTrue(shell.contains("boolean baoSubjects"));
    assertTrue(shell.contains("boolean baoTerms"));
    assertTrue(shell.contains("boolean baoSettings"));
    assertTrue(shell.contains("/admin/terms"));
    assertTrue(shell.contains("/admin/subjects"));
    assertTrue(shell.contains("/admin/settings"));
    assertFalse(shell.contains("bao-settings-children"));
    assertFalse(shell.contains("<details"));
    assertFalse(shell.contains("<summary"));
    assertFalse(shell.contains("⌄"));
    assertFalse(shell.contains("ams-sidebar-note"));
    assertTrue(shell.contains("<span>Quản trị viên</span>"));
    for (String relative : List.of("settings/list.jsp", "settings/detail.jsp", "master-data/list.jsp", "master-data/detail.jsp",
        "subjects/list.jsp", "subjects/detail.jsp", "subjects/form.jsp", "subjects/experts.jsp",
        "subjects/status.jsp", "subjects/term.jsp")) {
      String view = Files.readString(VIEWS.resolve(relative));
      assertFalse(view.contains("aria-label=\"Loại thiết lập\""), relative);
      assertTrue(view.contains("class=\"bao-context\""), relative);
    }
    for (String relative : List.of("master-data/list.jsp", "master-data/detail.jsp",
        "subjects/list.jsp", "subjects/detail.jsp", "subjects/form.jsp",
        "subjects/experts.jsp", "subjects/status.jsp", "subjects/term.jsp")) {
      assertFalse(Files.readString(VIEWS.resolve(relative)).contains("<span>Cài đặt</span>"), relative);
    }
  }

  @Test
  void academicTermCalendarHasNoManualCurrentControls() throws Exception {
    String view=Files.readString(VIEWS.resolve("master-data/detail.jsp"));
    for(String field:List.of("termCode","termName","startsAt","endsAt","termId","csrf","active"))
      assertTrue(view.contains("name=\""+field+"\""),field);
    assertFalse(view.contains("name=\"current\""));
    assertFalse(view.contains("data-term-four-months"));
    assertFalse(view.contains("academic-terms.js"));
    assertTrue(view.contains("shown!=null&&shown.active()?\"checked\":\"\""), "New terms require an explicit enabled selection");
    assertTrue(view.contains("TermTimeline.ZONE"));
    assertTrue(view.contains("if(ended)"));
    String list=Files.readString(VIEWS.resolve("master-data/list.jsp"));
    assertFalse(list.contains("mode=switch"));
    assertFalse(list.contains("Đặt hiện tại"));
    assertTrue(list.contains("if(!ended)"));
    assertTrue(list.contains("Theo lịch"));
    assertTrue(list.contains("Sử dụng"));
  }

  @Test
  void allAdminEntryScreensShareTheSameNavigationComponent() throws Exception {
    for (String relative : List.of("admin/dashboard.jsp", "users/list.jsp", "users/detail.jsp",
        "settings/list.jsp", "settings/detail.jsp", "master-data/list.jsp", "master-data/detail.jsp", "subjects/list.jsp")) {
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
