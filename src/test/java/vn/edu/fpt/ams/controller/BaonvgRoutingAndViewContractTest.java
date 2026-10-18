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
    assertTrue(terms.contains("Trạng thái học kỳ"));
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
  void allBaonvgAdminViewsUseTheSharedShell() throws Exception {
    for (String relative : List.of(
        "settings/list.jsp", "settings/detail.jsp", "master-data/list.jsp", "master-data/detail.jsp", "master-data/switch.jsp", "subjects/list.jsp",
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
        "master-data/switch.jsp", "subjects/list.jsp", "subjects/detail.jsp", "subjects/form.jsp", "subjects/experts.jsp",
        "subjects/status.jsp", "subjects/term.jsp")) {
      String view = Files.readString(VIEWS.resolve(relative));
      assertFalse(view.contains("aria-label=\"Loại thiết lập\""), relative);
      assertTrue(view.contains("class=\"bao-context\""), relative);
    }
    for (String relative : List.of("master-data/list.jsp", "master-data/detail.jsp",
        "master-data/switch.jsp", "subjects/list.jsp", "subjects/detail.jsp", "subjects/form.jsp",
        "subjects/experts.jsp", "subjects/status.jsp", "subjects/term.jsp")) {
      assertFalse(Files.readString(VIEWS.resolve(relative)).contains("<span>Cài đặt</span>"), relative);
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
  void academicTermCreateOffersStatusButNotSetCurrent() throws Exception {
    String view = Files.readString(VIEWS.resolve("master-data/detail.jsp"));
    String createFields = view.substring(view.indexOf("if(create){"), view.indexOf("else{", view.indexOf("if(create){")));
    assertTrue(createFields.contains("name=\"active\""));
    assertFalse(createFields.contains("name=\"current\""));
    assertFalse(createFields.contains("Đặt làm học kỳ hiện tại"));
    assertTrue(view.contains("name=\"current\" value=\"true\""), "Edit retains its current flag without offering a toggle");
  }

  @Test
  void academicTermSwitchRequiresAnExplicitReviewedPost() throws Exception {
    String list = Files.readString(VIEWS.resolve("master-data/list.jsp"));
    String confirmation = Files.readString(VIEWS.resolve("master-data/switch.jsp"));
    assertTrue(list.contains("mode=switch"));
    assertTrue(list.contains("Chuyển học kỳ để ngừng"));
    assertTrue(confirmation.contains("Học kỳ sẽ ngừng hoạt động"));
    assertTrue(confirmation.contains("Học kỳ sẽ hoạt động và thành hiện tại"));
    assertTrue(confirmation.contains("name=\"action\" value=\"switch\""));
    assertTrue(confirmation.contains("name=\"previousTermId\""));
    assertTrue(confirmation.contains("name=\"csrf\""));
    assertTrue(confirmation.contains("Xác nhận chuyển học kỳ"));
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
