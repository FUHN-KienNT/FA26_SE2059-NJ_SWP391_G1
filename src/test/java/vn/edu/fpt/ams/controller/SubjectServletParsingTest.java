package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.model.ResponsibilityType;

class SubjectServletParsingTest {
  @Test
  void sameExpertCannotBePrimaryAndSupporting() {
    var request = request(new String[] {"2"}, new String[] {"2"});
    assertThrows(
        IllegalArgumentException.class,
        () -> SubjectServlet.assignments(request));
  }

  @Test
  void repeatedExpertInputIsRejectedInsteadOfSilentlyDeduplicated() {
    var request = request(new String[] {"2", "2"}, null);
    assertThrows(
        IllegalArgumentException.class,
        () -> SubjectServlet.assignments(request));
  }

  @Test
  void distinctResponsibilitiesAreParsed() {
    var result =
        SubjectServlet.assignments(
            request(new String[] {"2", "3"}, new String[] {"5"}));
    assertEquals(ResponsibilityType.PRIMARY, result.get(2L));
    assertEquals(ResponsibilityType.PRIMARY, result.get(3L));
    assertEquals(ResponsibilityType.SUPPORTING, result.get(5L));
  }

  @Test
  void mutuallyExclusiveResponsibilitySelectorsAreParsed() {
    var parameters = new LinkedHashMap<String, String[]>();
    parameters.put("responsibility_2", new String[] {"PRIMARY"});
    parameters.put("responsibility_3", new String[] {"SUPPORTING"});
    parameters.put("responsibility_5", new String[] {"NOT_ASSIGNED"});
    var result = SubjectServlet.responsibilityAssignments(request(parameters));
    assertEquals(2, result.size());
    assertEquals(ResponsibilityType.PRIMARY, result.get(2L));
    assertEquals(ResponsibilityType.SUPPORTING, result.get(3L));
    assertFalse(result.containsKey(5L));
  }

  @Test
  void duplicateOrInvalidResponsibilitySelectionIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SubjectServlet.responsibilityAssignments(
                request(Map.of("responsibility_2", new String[] {"PRIMARY", "SUPPORTING"}))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SubjectServlet.responsibilityAssignments(
                request(Map.of("responsibility_2", new String[] {"UNKNOWN"}))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SubjectServlet.responsibilityAssignments(
                request(Map.of("responsibility_invalid", new String[] {"PRIMARY"}))));
  }

  private static HttpServletRequest request(
      String[] primary, String[] supporting) {
    return (HttpServletRequest)
        Proxy.newProxyInstance(
            HttpServletRequest.class.getClassLoader(),
            new Class[] {HttpServletRequest.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getParameterValues"))
                return "primaryExperts".equals(args[0]) ? primary : supporting;
              throw new UnsupportedOperationException(method.getName());
            });
  }

  private static HttpServletRequest request(Map<String, String[]> parameters) {
    return (HttpServletRequest)
        Proxy.newProxyInstance(
            HttpServletRequest.class.getClassLoader(),
            new Class[] {HttpServletRequest.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getParameterNames"))
                return Collections.enumeration(parameters.keySet());
              if (method.getName().equals("getParameterValues"))
                return parameters.get((String) args[0]);
              throw new UnsupportedOperationException(method.getName());
            });
  }
}
