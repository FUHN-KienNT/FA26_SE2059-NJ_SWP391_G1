package vn.edu.fpt.ams.web;

import java.nio.file.Path;
import org.apache.jasper.JspC;
import org.junit.jupiter.api.Test;

class JspCompilationTest {
  /** Compile the enrollment page using Tomcat's default JSP language level. */
  @Test
  void enrollmentViewCompilesWithTomcatDefaults() throws Exception {
    var compiler = new JspC();
    compiler.setUriroot(Path.of("src/main/webapp").toAbsolutePath().toString());
    compiler.setJspFiles("WEB-INF/views/enrollments/detail.jsp,WEB-INF/views/teacher/offers.jsp");
    compiler.setOutputDir(Path.of("target/jsp-default-test").toAbsolutePath().toString());
    compiler.setClassPath(
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
    compiler.setCompile(true);
    compiler.setFailOnError(true);
    compiler.execute();
  }

  @Test
  void allJspViewsCompileOnJava17() throws Exception {
    var compiler = new JspC();
    compiler.setUriroot(Path.of("src/main/webapp").toAbsolutePath().toString());
    compiler.setOutputDir(Path.of("target/jsp-test").toAbsolutePath().toString());
    compiler.setClassPath(
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
    compiler.setCompilerSourceVM("17");
    compiler.setCompilerTargetVM("17");
    compiler.setCompile(true);
    compiler.setFailOnError(true);
    compiler.execute();
  }
}
