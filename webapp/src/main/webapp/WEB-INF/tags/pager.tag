<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="baseUrl" required="true" %>
<%@ attribute name="currentPage" required="true" type="java.lang.Integer" %>
<%@ attribute name="totalPages" required="true" type="java.lang.Integer" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>

<%-- Paginador "anterior / N de M / siguiente" (mismo markup y clases que el de
     la bandeja). baseUrl es el path sin ?page= (ej. "/perfil/favoritos"): se
     le agrega solo el parametro y el context path. No dibuja nada con una sola pagina. --%>
<c:if test="${totalPages > 1}">
    <nav class="pager">
        <c:if test="${currentPage > 1}">
            <a class="pager-arrow" href="<c:url value='${baseUrl}'><c:param name='page' value='${currentPage - 1}'/></c:url>">
                <spring:message code="event.list.prevPage"/>
            </a>
        </c:if>
        <span class="pager-number pager-number-active"><c:out value="${currentPage}"/> / <c:out value="${totalPages}"/></span>
        <c:if test="${currentPage < totalPages}">
            <a class="pager-arrow" href="<c:url value='${baseUrl}'><c:param name='page' value='${currentPage + 1}'/></c:url>">
                <spring:message code="event.list.nextPage"/>
            </a>
        </c:if>
    </nav>
</c:if>
