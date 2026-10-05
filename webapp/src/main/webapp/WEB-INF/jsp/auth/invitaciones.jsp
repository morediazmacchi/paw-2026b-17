<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="auth.invitations.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<div class="account-layout">
    <paw:accountSidebar active="invitations" registrationsCount="${registrationsCount}" favoritesCount="${favoritesCount}" eventsCount="${eventsCount}" invitationsCount="${invitationsCount}"/>

    <main class="account-main">
        <h1><spring:message code="auth.invitations.heading"/></h1>
        <p class="auth-subheading"><spring:message code="auth.invitations.subheading"/></p>

        <c:choose>
            <c:when test="${empty invitedEvents}">
                <p class="empty-state"><spring:message code="auth.invitations.empty"/></p>
            </c:when>
            <c:otherwise>
                <div class="event-row-list">
                    <c:forEach var="event" items="${invitedEvents}">
                        <paw:eventRow id="${event.id}" nombre="${event.title}" fecha="${event.eventDate}"
                                      ubicacion="${event.neighborhood.displayName}" precio="${event.price}"
                                      imagenId="${event.imageId}" cancelado="${event.cancelled}"/>
                    </c:forEach>
                </div>
                <paw:pager baseUrl="/perfil/invitaciones" currentPage="${currentPage}" totalPages="${totalPages}"/>
            </c:otherwise>
        </c:choose>
    </main>
</div>
</jsp:body>
</paw:layout>
