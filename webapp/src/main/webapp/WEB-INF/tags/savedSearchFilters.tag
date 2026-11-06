<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="search" required="true" type="ar.edu.itba.paw.webapp.util.SavedSearchView" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>

<%-- Los filtros de una busqueda guardada, uno por renglon, en palabras: lo usan "Mis listas" y la pantalla de
     confirmar la baja. Solo se escribe lo que la lista realmente filtra; sin ningun filtro dice que son todos
     los eventos. --%>
<ul class="saved-search-filters">
    <c:if test="${not empty search.categoryNames}">
        <li><spring:message code="auth.lists.filter.categories"><spring:argument value="${search.categoriesText}"/></spring:message></li>
    </c:if>
    <c:if test="${not empty search.barrioNames}">
        <li><spring:message code="auth.lists.filter.barrios"><spring:argument value="${search.barriosText}"/></spring:message></li>
    </c:if>
    <c:choose>
        <c:when test="${not empty search.minPrice and not empty search.maxPrice}">
            <li><spring:message code="auth.lists.filter.priceBetween"><spring:argument value="${search.minPrice}"/><spring:argument value="${search.maxPrice}"/></spring:message></li>
        </c:when>
        <c:when test="${not empty search.minPrice}">
            <li><spring:message code="auth.lists.filter.priceFrom"><spring:argument value="${search.minPrice}"/></spring:message></li>
        </c:when>
        <c:when test="${not empty search.maxPrice}">
            <li><spring:message code="auth.lists.filter.priceTo"><spring:argument value="${search.maxPrice}"/></spring:message></li>
        </c:when>
    </c:choose>
    <c:if test="${not empty search.fromDate}">
        <c:set var="fromText"><paw:date value="${search.fromDate}" format="date"/></c:set>
        <li><spring:message code="auth.lists.filter.dateFrom"><spring:argument value="${fromText}"/></spring:message></li>
    </c:if>
    <c:if test="${not empty search.lastDate}">
        <c:set var="toText"><paw:date value="${search.lastDate}" format="date"/></c:set>
        <li><spring:message code="auth.lists.filter.dateTo"><spring:argument value="${toText}"/></spring:message></li>
    </c:if>
    <c:if test="${search.withoutFilters}">
        <li><spring:message code="auth.lists.filter.none"/></li>
    </c:if>
</ul>
