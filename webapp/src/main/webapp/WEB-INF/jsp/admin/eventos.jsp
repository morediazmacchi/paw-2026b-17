<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="admin.allEvents.title">
<jsp:body>
<paw:navbar backToEvents="true"/>

<main class="page">
    <h1><spring:message code="admin.allEvents.heading"/></h1>
    <spring:message code="admin.reports.tabLabel" var="reportsTabLabel"/>
    <spring:message code="admin.reports.trackingTabLabel" var="trackingTabLabel"/>
    <spring:message code="admin.pendingEvents.tabLabel" var="pendingEventsTabLabel"/>
    <spring:message code="admin.allEvents.tabLabel" var="allEventsTabLabel"/>
    <spring:message code="admin.users.tabLabel" var="usersTabLabel"/>
    <div class="auth-tabs">
        <a class="pill auth-tab" href="<c:url value='/admin/reportes'/>"><c:out value="${reportsTabLabel}"/></a>
        <a class="pill auth-tab" href="<c:url value='/admin/reportes/seguimiento'/>"><c:out value="${trackingTabLabel}"/></a>
        <a class="pill auth-tab" href="<c:url value='/admin/eventos-pendientes'/>"><c:out value="${pendingEventsTabLabel}"/></a>
        <span class="pill pill-active auth-tab"><c:out value="${allEventsTabLabel}"/></span>
        <a class="pill auth-tab" href="<c:url value='/admin/usuarios'/>"><c:out value="${usersTabLabel}"/></a>
    </div>

    <c:choose>
        <c:when test="${empty events}">
            <p class="empty-state"><spring:message code="admin.allEvents.empty"/></p>
        </c:when>
        <c:otherwise>
            <spring:message code="admin.allEvents.viewEventButton" var="viewEventButtonLabel"/>
            <spring:message code="admin.allEvents.viewRegistrationsButton" var="viewRegistrationsButtonLabel"/>
            <spring:message code="admin.allEvents.removedTag" var="removedTagLabel"/>
            <div class="event-row-list">
                <c:forEach var="stats" items="${events}">
                    <c:set var="event" value="${stats.event}"/>
                    <paw:eventRow id="${event.id}" nombre="${event.title}" fecha="${event.eventDate}"
                                  ubicacion="${event.neighborhood.displayName}" precio="${event.price}" gratis="${event.free}"
                                  imagenId="${event.imageId}" privado="${event.inviteOnly}"
                                  cancelado="${event.cancelled and not event.removedByAdmin}"
                                  pendiente="${event.pendingApproval}" rechazado="${event.rejected}"
                                  cambiosPedidos="${event.changesRequested}">
                        <%-- Dado de baja por un admin es distinto de un self-cancel: el eventRow no
                             distingue los dos (ambos son "cancelado" para el publico), asi que esto
                             agrega la etiqueta puntual que le interesa a quien modera. --%>
                        <c:if test="${event.removedByAdmin}">
                            <span class="event-row-cancelled-tag">(<c:out value="${removedTagLabel}"/>)</span>
                        </c:if>
                        <a class="btn btn-medium" href="<c:url value='/eventos/${event.id}'/>">
                            <c:out value="${viewEventButtonLabel}"/>
                        </a>
                        <a class="btn btn-medium btn-accent" href="<c:url value='/admin/eventos/${event.id}/inscriptos'/>">
                            <c:out value="${viewRegistrationsButtonLabel}"/>
                            (<c:out value="${stats.confirmedCount}"/><c:if test="${not empty stats.pendingCount and stats.pendingCount > 0}">
                                +<c:out value="${stats.pendingCount}"/></c:if>)
                        </a>
                    </paw:eventRow>
                </c:forEach>
            </div>
            <paw:pager baseUrl="/admin/eventos" currentPage="${currentPage}" totalPages="${totalPages}"/>
        </c:otherwise>
    </c:choose>
</main>
</jsp:body>
</paw:layout>
