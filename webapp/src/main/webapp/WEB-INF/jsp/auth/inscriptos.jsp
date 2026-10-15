<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="auth.pendingRegistrations.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<div class="account-layout">
    <paw:accountSidebar active="events" registrationsCount="${registrationsCount}" favoritesCount="${favoritesCount}" eventsCount="${eventsCount}" invitationsCount="${invitationsCount}"/>

    <main class="account-main">
        <h1><spring:message code="auth.pendingRegistrations.heading"/></h1>
        <p class="auth-subheading"><spring:message code="auth.pendingRegistrations.subheading" arguments="${event.title}" htmlEscape="true"/></p>

        <c:if test="${approved}">
            <p class="alert alert-success"><spring:message code="auth.pendingRegistrations.approved"/></p>
        </c:if>
        <c:if test="${rejected}">
            <p class="alert alert-success"><spring:message code="auth.pendingRegistrations.rejected"/></p>
        </c:if>
        <c:if test="${fullNotice}">
            <p class="alert"><spring:message code="auth.pendingRegistrations.full"/></p>
        </c:if>
        <%-- Se puede aceptar hasta que se llena el cupo (confirmados + invitaciones);
             ni los pendientes ni la lista de espera ocupan lugar. --%>
        <c:if test="${not empty availableSpots}">
            <p class="auth-subheading">
                <c:choose>
                    <c:when test="${noSpotsLeft}"><spring:message code="auth.pendingRegistrations.noSpots"/></c:when>
                    <c:otherwise><spring:message code="auth.pendingRegistrations.spotsLeft" arguments="${availableSpots}"/></c:otherwise>
                </c:choose>
            </p>
        </c:if>

        <c:choose>
            <c:when test="${not event.requiresApproval}">
                <p class="empty-state"><spring:message code="auth.pendingRegistrations.notApplicable"/></p>
            </c:when>
            <c:when test="${empty pendingRegistrations}">
                <p class="empty-state"><spring:message code="auth.pendingRegistrations.empty"/></p>
            </c:when>
            <c:otherwise>
                <spring:message code="auth.pendingRegistrations.approveButton" var="approveButtonLabel"/>
                <spring:message code="auth.pendingRegistrations.rejectButton" var="rejectButtonLabel"/>
                <div class="pending-registration-list">
                    <c:forEach var="registration" items="${pendingRegistrations}">
                        <div class="pending-registration-row">
                            <fmt:formatDate value="${registration.registeredAt}" pattern="dd/MM/yyyy HH:mm" var="requestedAt"/>
                            <div class="pending-registration-info">
                                <p class="pending-registration-name"><c:out value="${registration.name}"/></p>
                                <p class="pending-registration-meta">
                                    <c:out value="${registration.email}"/> &middot;
                                    <spring:message code="auth.pendingRegistrations.requestedAt" arguments="${requestedAt}"/>
                                </p>
                            </div>
                            <div class="pending-registration-actions">
                                <form action="<c:url value='/perfil/eventos/${event.id}/inscriptos/${registration.id}/rechazar'/>" method="post">
                                    <paw:button text="${rejectButtonLabel}" cssClass="btn-danger"/>
                                </form>
                                <form action="<c:url value='/perfil/eventos/${event.id}/inscriptos/${registration.id}/aceptar'/>" method="post">
                                    <paw:button text="${approveButtonLabel}" cssClass="btn-accent" disabled="${noSpotsLeft}"/>
                                </form>
                            </div>
                        </div>
                    </c:forEach>
                </div>
            </c:otherwise>
        </c:choose>

        <p class="auth-subheading">
            <a class="btn-link" href="<c:url value='/perfil/eventos'/>" data-history-back>
                <spring:message code="auth.pendingRegistrations.backToEvents"/>
            </a>
        </p>
    </main>
</div>
</jsp:body>
</paw:layout>
