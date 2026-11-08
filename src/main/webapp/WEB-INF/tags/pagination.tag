<%@ tag pageEncoding="UTF-8" body-content="empty" import="vn.edu.fpt.ams.util.Pagination,vn.edu.fpt.ams.util.WebSupport" %>
<%@ attribute name="currentPage" type="java.lang.Integer" required="true" %>
<%@ attribute name="totalPages" type="java.lang.Number" required="true" %>
<%@ attribute name="baseUrl" type="java.lang.String" required="true" %>
<%@ attribute name="pageParameter" type="java.lang.String" required="false" %>
<%@ attribute name="ariaLabel" type="java.lang.String" required="false" %>
<%
    int selectedPage = currentPage;
    int lastPage = (int) Math.min(Integer.MAX_VALUE, totalPages.longValue());
    String parameter = pageParameter == null ? "page" : pageParameter;
    String label = ariaLabel == null ? "Phân trang" : ariaLabel;
%>
<% if (lastPage > 1) { %>
    <nav class="ams-pagination" aria-label="<%= WebSupport.html(label) %>">
        <% if (selectedPage > 1) { %>
            <a class="ams-page-control" href="<%= WebSupport.html(Pagination.url(baseUrl, parameter, 1)) %>" aria-label="Trang đầu" title="Trang đầu">&lt;&lt;</a>
            <a class="ams-page-control" href="<%= WebSupport.html(Pagination.url(baseUrl, parameter, selectedPage - 1)) %>" rel="prev" aria-label="Trang trước" title="Trang trước">&lt;</a>
        <% } else { %>
            <span class="ams-page-control" aria-disabled="true" aria-label="Trang đầu">&lt;&lt;</span>
            <span class="ams-page-control" aria-disabled="true" aria-label="Trang trước">&lt;</span>
        <% } %>

        <% for (int number : Pagination.pages(selectedPage, lastPage)) { %>
            <% if (number == 0) { %>
                <span class="ams-page-gap" aria-hidden="true">...</span>
            <% } else if (number == selectedPage) { %>
                <span class="ams-page-number is-current" aria-current="page" aria-label="Trang <%= number %>"><%= number %></span>
            <% } else { %>
                <a class="ams-page-number" href="<%= WebSupport.html(Pagination.url(baseUrl, parameter, number)) %>" aria-label="Đến trang <%= number %>"><%= number %></a>
            <% } %>
        <% } %>

        <% if (selectedPage < lastPage) { %>
            <a class="ams-page-control" href="<%= WebSupport.html(Pagination.url(baseUrl, parameter, selectedPage + 1)) %>" rel="next" aria-label="Trang sau" title="Trang sau">&gt;</a>
            <a class="ams-page-control" href="<%= WebSupport.html(Pagination.url(baseUrl, parameter, lastPage)) %>" aria-label="Trang cuối" title="Trang cuối">&gt;&gt;</a>
        <% } else { %>
            <span class="ams-page-control" aria-disabled="true" aria-label="Trang sau">&gt;</span>
            <span class="ams-page-control" aria-disabled="true" aria-label="Trang cuối">&gt;&gt;</span>
        <% } %>
    </nav>
<% } %>
