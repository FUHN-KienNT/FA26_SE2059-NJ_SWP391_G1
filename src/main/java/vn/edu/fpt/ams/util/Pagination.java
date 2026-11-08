package vn.edu.fpt.ams.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Page numbers and links shared by the assignment and submission screens. */
public final class Pagination {
    private Pagination() {}

    /** Zero represents a gap; first/last arrows provide access outside this bounded window. */
    public static List<Integer> pages(int currentPage, int totalPages) {
        if (totalPages < 1 || currentPage < 1 || currentPage > totalPages)
            throw new IllegalArgumentException("Trang phân trang không hợp lệ.");

        var candidates = new TreeSet<Integer>();

        if (totalPages <= 7) {
            for (int page = 1; page <= totalPages; page++) candidates.add(page);
        } else {
            int start = Math.max(1, Math.min(currentPage - 1, totalPages - 4));
            for (long page = start; page < (long) start + 3; page++)
                candidates.add((int) page);

            candidates.add(totalPages - 1);
            candidates.add(totalPages);
        }

        var result = new ArrayList<Integer>();
        int previous = 0;

        for (int page : candidates) {
            if (previous > 0 && page - previous > 1)
                result.add(page - previous == 2 ? previous + 1 : 0);

            result.add(page);
            previous = page;
        }

        return List.copyOf(result);
    }

    /** Replace a single paging key while retaining raw filter encoding and the fragment. */
    public static String url(String baseUrl, String pageParameter, int page) {
        Objects.requireNonNull(baseUrl, "Cần có địa chỉ phân trang.");
        if (pageParameter == null || !pageParameter.matches("[A-Za-z][A-Za-z0-9_]*") || page < 1)
            throw new IllegalArgumentException("Tham số phân trang không hợp lệ.");

        int fragmentStart = baseUrl.indexOf('#');
        String fragment = fragmentStart < 0 ? "" : baseUrl.substring(fragmentStart);
        String address = fragmentStart < 0 ? baseUrl : baseUrl.substring(0, fragmentStart);
        int queryStart = address.indexOf('?');
        String path = queryStart < 0 ? address : address.substring(0, queryStart);
        String query = queryStart < 0 ? "" : address.substring(queryStart + 1);
        var parameters = new ArrayList<String>();

        for (String parameter : query.split("&", -1)) {
            if (parameter.isEmpty()) continue;
            int equals = parameter.indexOf('=');
            String name = equals < 0 ? parameter : parameter.substring(0, equals);
            if (!pageParameter.equals(name)) parameters.add(parameter);
        }

        parameters.add(pageParameter + "=" + page);
        return path + "?" + String.join("&", parameters) + fragment;
    }
}
