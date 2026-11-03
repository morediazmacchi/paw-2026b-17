<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="id" required="true" type="java.lang.Long" %>
<%@ attribute name="nombre" required="true" %>
<%@ attribute name="precio" required="false" type="java.lang.Double" %>
<%-- gratis: lo dice el evento (Event.isFree), la tag no lo vuelve a deducir del precio. --%>
<%@ attribute name="gratis" required="false" type="java.lang.Boolean" %>
<%@ attribute name="fecha" required="true" type="java.util.Date" %>
<%@ attribute name="categoria" required="false" %>
<%@ attribute name="ubicacion" required="false" %>
<%@ attribute name="imagenId" required="false" type="java.lang.Long" %>
<%-- favorito/returnTo: si no vienen (paginas donde no hace falta el corazon,
     ej. Mis inscripciones si algun dia reusa esta card) simplemente no se
     dibuja el boton. --%>
<%@ attribute name="favorito" required="false" type="java.lang.Boolean" %>
<%@ attribute name="returnTo" required="false" %>
<%-- estado: el de la inscripcion propia en este evento (CONFIRMED, PENDING, WAITLISTED o
     REJECTED). Si no viene, la persona no tiene inscripcion y no se dibuja nada. --%>
<%@ attribute name="estado" required="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>

<%-- El corazon va FUERA del <a>: un <button> adentro de un link es HTML
     invalido (contenido interactivo anidado) y el click termina disparando
     los dos a la vez. Por eso el wrapper: el <a> de siempre adentro, el
     form del corazon superpuesto arriba como hermano. --%>
<div class="card-wrapper">
    <a class="card" href="<c:url value='/eventos/${id}'/>">
        <div class="card-placeholder">
            <c:if test="${not empty imagenId}">
                <img class="card-img" src="<c:url value='/image/${imagenId}'/>" alt="<c:out value='${nombre}'/>" />
            </c:if>
            <span class="card-precio">
                <c:choose>
                    <c:when test="${gratis}"><spring:message code="event.price.free"/></c:when>
                    <c:otherwise><paw:price value="${precio}"/></c:otherwise>
                </c:choose>
            </span>
        </div>
        <div class="card-body">
            <c:if test="${not empty categoria}">
                <span class="card-categoria"><c:out value="${categoria}"/></span>
            </c:if>
            <h3 class="card-nombre"><c:out value="${nombre}" /></h3>
            <p class="card-meta">
                <paw:date value="${fecha}" format="dateTime"/>
                <c:if test="${not empty ubicacion}"> &middot; <c:out value="${ubicacion}"/></c:if>
            </p>
        </div>
    </a>
    <c:if test="${not empty estado}">
        <span class="status-pill card-status status-pill-${fn:toLowerCase(estado)}"><spring:message code="event.status.${estado}"/></span>
    </c:if>
    <sec:authorize access="isAuthenticated()">
        <c:url value="/eventos/${id}/favorito" var="favAddUrl"/>
        <c:url value="/eventos/${id}/favorito/quitar" var="favRemoveUrl"/>
        <spring:message code="event.favorite.add" var="favAddLabel"/>
        <spring:message code="event.favorite.remove" var="favRemoveLabel"/>
        <%-- Formulario comun (sin fetch): POST, el controller redirige a returnTo (la
             misma pagina donde esta la card) y se recarga con el corazon ya actualizado. --%>
        <form class="card-favorite-form" action="${favorito ? favRemoveUrl : favAddUrl}" method="post">
            <input type="hidden" name="returnTo" value="${fn:escapeXml(returnTo)}"/>
            <button type="submit" class="card-favorite-btn${favorito ? ' card-favorite-btn-active' : ''}"
                    aria-label="${favorito ? favRemoveLabel : favAddLabel}">
                <span class="favorite-icon-outline"${favorito ? ' hidden' : ''}><paw:icon name="heart"/></span>
                <span class="favorite-icon-filled"${favorito ? '' : ' hidden'}><paw:icon name="heart-filled"/></span>
            </button>
        </form>
    </sec:authorize>
</div>
