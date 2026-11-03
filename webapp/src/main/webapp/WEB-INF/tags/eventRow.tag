<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="id" required="true" type="java.lang.Long" %>
<%@ attribute name="nombre" required="true" %>
<%@ attribute name="fecha" required="true" type="java.util.Date" %>
<%@ attribute name="ubicacion" required="false" %>
<%@ attribute name="precio" required="false" type="java.lang.Double" %>
<%-- gratis: lo dice el evento (Event.isFree), la tag no lo vuelve a deducir del precio. --%>
<%@ attribute name="gratis" required="false" type="java.lang.Boolean" %>
<%@ attribute name="imagenId" required="false" type="java.lang.Long" %>
<%-- Solo la usa "Mis eventos" (los privados no aparecen en ningun otro
     listado que use este tag, asi que en el resto siempre viene false). --%>
<%@ attribute name="privado" required="false" type="java.lang.Boolean" %>
<%-- Idem privado: marca la fila de un evento cancelado (Mis eventos, Mis inscripciones, Mis favoritos). --%>
<%@ attribute name="cancelado" required="false" type="java.lang.Boolean" %>
<%-- Revision previa a publicarse (ver EventApprovalStatus): solo la usa "Mis
     eventos" (en el resto de los listados el evento ya esta aprobado, porque
     EventAccessServiceImpl.canView() no deja ver uno pendiente/rechazado a
     nadie mas que al creador/admin). --%>
<%@ attribute name="pendiente" required="false" type="java.lang.Boolean" %>
<%@ attribute name="rechazado" required="false" type="java.lang.Boolean" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>

<%-- Fila horizontal: miniatura + titulo (linkean al detalle) mas metadata, y a
     la derecha lo que traiga el body (los botones de accion cambian segun la
     pantalla: Cancelar en "Mis inscripciones", Editar/Eliminar en "Mis eventos",
     Quitar en "Favoritos"). Por eso no es <paw:card>: esa es un link entero,
     esto necesita un boton al lado que no puede ir adentro de un <a>. --%>
<div class="event-row${(cancelado or rechazado) ? ' event-row-cancelled' : ''}">
    <a class="event-row-link" href="<c:url value='/eventos/${id}'/>">
        <div class="event-row-thumb">
            <c:if test="${not empty imagenId}">
                <img src="<c:url value='/image/${imagenId}'/>" alt="<c:out value='${nombre}'/>" />
            </c:if>
        </div>
        <div class="event-row-info">
            <h3 class="event-row-title">
                <c:out value="${nombre}" />
                <c:if test="${privado}">
                    <span class="event-row-private-tag">(<spring:message code="event.publish.privateLabel"/>)</span>
                </c:if>
                <c:if test="${cancelado}">
                    <span class="event-row-cancelled-tag">(<spring:message code="auth.events.cancelledTag"/>)</span>
                </c:if>
                <c:if test="${pendiente}">
                    <span class="event-row-pending-tag">(<spring:message code="auth.events.pendingApprovalTag"/>)</span>
                </c:if>
                <c:if test="${rechazado}">
                    <span class="event-row-cancelled-tag">(<spring:message code="auth.events.rejectedTag"/>)</span>
                </c:if>
            </h3>
            <p class="event-row-meta">
                <paw:date value="${fecha}" format="weekdayDateTime"/>
                <c:if test="${not empty ubicacion}"> &middot; <c:out value="${ubicacion}"/></c:if>
                <c:if test="${not empty precio}">
                    &middot;
                    <span class="event-row-price">
                        <c:choose>
                            <c:when test="${gratis}"><spring:message code="event.price.free"/></c:when>
                            <c:otherwise><paw:price value="${precio}"/></c:otherwise>
                        </c:choose>
                    </span>
                </c:if>
            </p>
        </div>
    </a>
    <div class="event-row-actions">
        <jsp:doBody/>
    </div>
</div>
