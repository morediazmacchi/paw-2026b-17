<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="baseUrl" required="true" %>
<%@ attribute name="currentPage" required="true" type="java.lang.Integer" %>
<%@ attribute name="totalPages" required="true" type="java.lang.Integer" %>
<%-- Opcionales, para una pantalla con dos paginadores (ver auth/inscripciones.jsp): el nombre del
     parametro de ESTE paginador (por defecto "page") y el de otro paginador con su valor actual, que
     se repite en los links para que paginar uno no resetee al otro. --%>
<%@ attribute name="pageParam" required="false" %>
<%@ attribute name="otherParam" required="false" %>
<%@ attribute name="otherPage" required="false" type="java.lang.Integer" %>
<%-- Opcional: otros parametros (nombre -> valor) que se repiten en los links, por ejemplo la busqueda y el orden de
     una lista filtrada (ver admin/eventos.jsp). --%>
<%@ attribute name="extraParams" required="false" type="java.util.Map" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>

<%-- Paginador "anterior / N de M / siguiente" (mismo markup y clases que el de
     la bandeja). baseUrl es el path sin ?page= (ej. "/perfil/favoritos"): se
     le agrega solo el parametro y el context path. No dibuja nada con una sola pagina. --%>
<c:set var="pageParamName" value="${empty pageParam ? 'page' : pageParam}"/>
<c:if test="${totalPages > 1}">
    <nav class="pager">
        <c:if test="${currentPage > 1}">
            <a class="pager-arrow" href="<c:url value='${baseUrl}'><c:param name='${pageParamName}' value='${currentPage - 1}'/><c:if test="${not empty otherParam}"><c:param name='${otherParam}' value='${otherPage}'/></c:if><c:forEach var='extra' items='${extraParams}'><c:param name='${extra.key}' value='${extra.value}'/></c:forEach></c:url>">
                <spring:message code="event.list.prevPage"/>
            </a>
        </c:if>
        <span class="pager-number pager-number-active"><c:out value="${currentPage}"/> / <c:out value="${totalPages}"/></span>
        <c:if test="${currentPage < totalPages}">
            <a class="pager-arrow" href="<c:url value='${baseUrl}'><c:param name='${pageParamName}' value='${currentPage + 1}'/><c:if test="${not empty otherParam}"><c:param name='${otherParam}' value='${otherPage}'/></c:if><c:forEach var='extra' items='${extraParams}'><c:param name='${extra.key}' value='${extra.value}'/></c:forEach></c:url>">
                <spring:message code="event.list.nextPage"/>
            </a>
        </c:if>
    </nav>
</c:if>
