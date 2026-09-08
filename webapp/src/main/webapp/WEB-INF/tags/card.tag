<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="id" required="true" type="java.lang.Long" %>
<%@ attribute name="nombre" required="true" %>
<%@ attribute name="precio" required="false" type="java.lang.Double" %>
<%@ attribute name="fecha" required="true" type="java.util.Date" %>
<%@ attribute name="categoria" required="false" %>
<%@ attribute name="ubicacion" required="false" %>
<%@ attribute name="imagenId" required="false" type="java.lang.Long" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>

<a class="card" href="<c:url value='/eventos/${id}'/>">
    <div class="card-placeholder">
        <c:if test="${not empty imagenId}">
            <img class="card-img" src="<c:url value='/image/${imagenId}'/>" alt="<c:out value='${nombre}'/>" />
        </c:if>
        <span class="card-precio">
            <c:choose>
                <c:when test="${empty precio or precio == 0}"><spring:message code="event.price.free"/></c:when>
                <c:otherwise>$<fmt:formatNumber value="${precio}" type="number" minFractionDigits="0" maxFractionDigits="0" /></c:otherwise>
            </c:choose>
        </span>
    </div>
    <div class="card-body">
        <c:if test="${not empty categoria}">
            <span class="card-categoria"><c:out value="${categoria}"/></span>
        </c:if>
        <h3 class="card-nombre"><c:out value="${nombre}" /></h3>
        <p class="card-meta">
            <fmt:formatDate value="${fecha}" pattern="dd/MM/yyyy · HH:mm" />hs
            <c:if test="${not empty ubicacion}"> &middot; <c:out value="${ubicacion}"/></c:if>
        </p>
    </div>
</a>
