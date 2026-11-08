package vn.edu.fpt.ams.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.jasper.JspC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JspCompilationTest {
  /** Compile entry points using the same default JSP language level as local Tomcat. */
  @Test
  void enrollmentViewCompilesWithTomcatDefaults() throws Exception {
    var compiler = new JspC();
    compiler.setUriroot(Path.of("src/main/webapp").toAbsolutePath().toString());
    compiler.setJspFiles(
        "WEB-INF/views/teacher/students/list.jsp,WEB-INF/views/teacher/students/detail.jsp,WEB-INF/views/teacher/groups/list.jsp,WEB-INF/views/teacher/groups/detail.jsp,WEB-INF/views/teacher/classes/list.jsp,WEB-INF/views/teacher/classes/detail.jsp,WEB-INF/views/teacher/dashboard.jsp,WEB-INF/views/submissions/detail.jsp,WEB-INF/views/student/result.jsp");
    compiler.setOutputDir(Path.of("target/jsp-default-test").toAbsolutePath().toString());
    compiler.setClassPath(
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
    compiler.setCompile(true);
    compiler.setFailOnError(true);
    compiler.execute();
  }

  @Test
  void allJspViewsCompileOnJava17(@TempDir Path output) throws Exception {
    var compiler = new JspC();
    compiler.setUriroot(Path.of("src/main/webapp").toAbsolutePath().toString());
    compiler.setOutputDir(output.toString());
    compiler.setClassPath(
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
    compiler.setCompilerSourceVM("17");
    compiler.setCompilerTargetVM("17");
    compiler.setCompile(true);
    compiler.setFailOnError(true);
    compiler.execute();

    var views = output.resolve("org/apache/jsp/WEB_002dINF/views");
    assertTrue(Files.readString(views.resolve("student/essay_jsp.java")).contains("Bài tập nhóm"));
    assertTrue(Files.readString(views.resolve("student/objective_jsp.java")).contains("Bài tập nhóm"));
    var submission = Files.readString(views.resolve("submissions/detail_jsp.java"));
    assertTrue(submission.contains("Chấm và công bố điểm bài nhóm"));
    assertTrue(submission.contains("Điểm của từng thành viên"));
  }
}
