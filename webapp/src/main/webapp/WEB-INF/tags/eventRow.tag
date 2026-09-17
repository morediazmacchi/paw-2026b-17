<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="id" required="true" type="java.lang.Long" %>
<%@ attribute name="nombre" required="true" %>
<%@ attribute name="fecha" required="true" type="java.util.Date" %>
<%@ attribute name="ubicacion" required="false" %>
<%@ attribute name="precio" required="false" type="java.lang.Double" %>
<%@ attribute name="imagenId" required="false" type="java.lang.Long" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>

<%-- Fila horizontal: miniatura + titulo (linkean al detalle) mas metadata, y a
     la derecha lo que traiga el body (los botones de accion cambian segun la
     pantalla: Cancelar en "Mis inscripciones", Editar/Eliminar en "Mis eventos",
     Quitar en "Favoritos"). Por eso no es <paw:card>: esa es un link entero,
     esto necesita un boton al lado que no puede ir adentro de un <a>. --%>
<div class="event-row">
    <a class="event-row-link" href="<c:url value='/eventos/${id}'/>">
        <div class="event-row-thumb">
            <c:if test="${not empty imagenId}">
                <img src="<c:url value='/image/${imagenId}'/>" alt="<c:out value='${nombre}'/>" />
            </c:if>
        </div>
        <div class="event-row-info">
            <h3 class="event-row-title"><c:out value="${nombre}" /></h3>
            <p class="event-row-meta">
                <fmt:formatDate value="${fecha}" pattern="EEE dd/MM · HH:mm" />hs
                <c:if test="${not empty ubicacion}"> &middot; <c:out value="${ubicacion}"/></c:if>
                <c:if test="${not empty precio}">
                    &middot;
                    <span class="event-row-price">
                        <c:choose>
                            <c:when test="${precio == 0}"><spring:message code="event.price.free"/></c:when>
                            <c:otherwise>$<fmt:formatNumber value="${precio}" type="number" minFractionDigits="0" maxFractionDigits="0" /></c:otherwise>
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
