package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dao.EnrollmentDAO;
import vn.edu.fpt.ams.dto.ImportLineResult.Outcome;

class EnrollmentImportTest {
    /** Mixed outcomes keep original line numbers, identities, reasons and matching totals. */
    @Test void reportsEveryDataRowAndDeduplicatesEmails() {
        var calls = new ArrayList<String>();
        var result = service(true, calls).importStudentsFromCsv(7, 1, csv(
                "\uFEFFemail,name\n\"new@example.test\",New Student\n\nexisting@example.test\ninvalid\nmissing@example.test\nNEW@example.test\nfull@example.test\n"));
        assertEquals(1, result.imported());
        assertEquals(2, result.skipped());
        assertEquals(3, result.rejected());
        assertEquals(List.of(2,4,5,6,7,8), result.lines().stream().map(row -> row.lineNumber()).toList());
        assertEquals(Outcome.IMPORTED, result.lines().get(0).outcome());
        assertEquals("new@example.test", result.lines().get(0).email());
        assertEquals(Outcome.SKIPPED, result.lines().get(1).outcome());
        assertTrue(result.lines().get(1).reason().contains("ghi danh"));
        assertEquals("invalid", result.lines().get(2).email());
        assertTrue(result.lines().get(3).reason().contains("Không có tài khoản"));
        assertTrue(result.lines().get(4).reason().contains("trùng"));
        assertEquals("Lớp đã đủ sĩ số.", result.lines().get(5).reason());
        assertEquals(4, calls.size());
        assertEquals(result.lines().size(), result.imported()+result.skipped()+result.rejected());
        assertThrows(UnsupportedOperationException.class, () -> result.lines().clear());
    }

    /** An email beginning with email is still data when there is no header row. */
    @Test void doesNotDiscardEmailPrefixWithoutHeader() {
        var result = service(true, new ArrayList<>()).importStudentsFromCsv(7,1,csv("email.student@example.test\n"));
        assertEquals(1,result.imported());
        assertEquals(1,result.lines().get(0).lineNumber());
    }

    /** Empty and header-only uploads return a meaningful empty detail list. */
    @Test void supportsHeaderOnlyAndBlankFiles() {
        for (String input : List.of("", "\n\n", "email,name\n\n")) {
            var result=service(true,new ArrayList<>()).importStudentsFromCsv(7,1,csv(input));
            assertTrue(result.lines().isEmpty());
            assertEquals(0,result.imported()+result.skipped()+result.rejected());
        }
    }

    /** Ownership is checked before any row can be placed or reported as successful. */
    @Test void rejectsForeignClassBeforeProcessing() {
        var calls=new ArrayList<String>();
        assertThrows(IllegalArgumentException.class, () ->
                service(false,calls).importStudentsFromCsv(7,99,csv("new@example.test")));
        assertTrue(calls.isEmpty());
    }

    /** Provide predictable placement outcomes without modifying a database. */
    private EnrollmentService service(boolean owned,List<String> calls) {
        return new EnrollmentService(new EnrollmentDAO() {
            /** Restrict import to the fixture's authorized class. */
            @Override public boolean teacherOwnsClass(long teacher,long classId) { return owned; }
        }) {
            /** Simulate committed placement, duplicate registration and expected validation failures. */
            @Override public long addStudentByEmail(long teacher,long classId,String email) {
                calls.add(email);
                if (email.equals("existing@example.test")) throw new IllegalArgumentException("This student already has an active enrollment for the subject term");
                if (email.equals("missing@example.test")) throw new IllegalArgumentException("Không có tài khoản sử dụng email này");
                if (email.equals("full@example.test")) throw new IllegalArgumentException("The class has no available seat");
                return 1;
            }
        };
    }

    /** Encode a CSV fixture exactly as a UTF-8 upload stream. */
    private ByteArrayInputStream csv(String input) {
        return new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    }
}
