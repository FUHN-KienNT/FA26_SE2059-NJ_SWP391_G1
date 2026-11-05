package vn.edu.fpt.ams.service;

import java.sql.Connection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import vn.edu.fpt.ams.dao.StudentClassDAO;
import vn.edu.fpt.ams.dto.StudentClassFilter;
import vn.edu.fpt.ams.dto.StudentClassPage;
import vn.edu.fpt.ams.dto.StudentClassPage.Counts;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.Jdbc;

/** Loads a consistent read-only snapshot without starting or finalizing an attempt. */
public class StudentClassService {
    private final Jdbc.Connections connections;
    private final StudentClassDAO dao = new StudentClassDAO();

    public StudentClassService() {
        this(DBConnection::getConnection);
    }

    public StudentClassService(Jdbc.Connections connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    public StudentClassPage load(long studentId, Long classId, StudentClassFilter filter) {
        if (studentId <= 0) throw unauthorized();

        Objects.requireNonNull(filter, "filter");

        return Jdbc.transaction(
                connections,
                c -> {
                    c.setReadOnly(true);
                    c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

                    if (!dao.activeStudent(c, studentId)) throw unauthorized();

                    var now = dao.currentTime(c);
                    var classes = dao.classes(c, studentId);
                    var selected =
                            classId == null
                                    ? (classes.isEmpty() ? null : classes.get(0))
                                    : classes.stream()
                                            .filter(item -> item.classId() == classId)
                                            .findFirst()
                                            .orElseThrow(
                                                    () ->
                                                            new NoSuchElementException(
                                                                    "Không tìm thấy lớp học của bạn."));

                    if (selected == null)
                        return new StudentClassPage(
                                null,
                                classes,
                                filter.withPage(1),
                                now,
                                new Counts(0, 0, 0, 0),
                                List.of(),
                                0,
                                1,
                                0);

                    long selectedId = selected.classId();
                    var counts = dao.counts(c, studentId, selectedId, now);
                    long total = dao.count(c, studentId, selectedId, now, filter);
                    int totalPages =
                            (int)
                                    Math.min(
                                            Integer.MAX_VALUE,
                                            total / StudentClassFilter.PAGE_SIZE
                                                    + (total % StudentClassFilter.PAGE_SIZE == 0
                                                            ? 0
                                                            : 1));
                    int page = Math.min(filter.page(), Math.max(1, totalPages));
                    var resolvedFilter = filter.withPage(page);
                    var assignments =
                            dao.assignments(c, studentId, selectedId, now, resolvedFilter);

                    return new StudentClassPage(
                            selected,
                            classes,
                            resolvedFilter,
                            now,
                            counts,
                            assignments,
                            total,
                            page,
                            totalPages);
                });
    }

    private static SecurityException unauthorized() {
        return new SecurityException("Vui lòng đăng nhập bằng tài khoản sinh viên đang hoạt động.");
    }
}
