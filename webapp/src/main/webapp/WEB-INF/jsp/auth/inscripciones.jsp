<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="auth.registrations.title"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</head>
<body>

<paw:navbar>
    <spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>">&larr; <c:out value="${backToEventsLabel}"/></a>
</paw:navbar>

<div class="account-layout">
    <paw:accountSidebar active="registrations" registrationsCount="${registrationsCount}"/>

    <main class="account-main">
        <h1><spring:message code="auth.registrations.heading"/></h1>
        <p class="auth-subheading"><spring:message code="auth.registrations.subheading"/></p>

        <c:if test="${cancelled}">
            <p class="alert"><spring:message code="auth.registrations.cancelledMessage"/></p>
        </c:if>

        <c:choose>
            <c:when test="${empty registrations}">
                <p class="empty-state"><spring:message code="auth.registrations.empty"/></p>
            </c:when>
            <c:otherwise>
                <spring:message code="auth.registrations.cancelButton" var="cancelButtonLabel"/>
                <div class="event-row-list">
                    <c:forEach var="registration" items="${registrations}">
                        <c:set var="event" value="${eventsById[registration.eventId]}"/>
                        <c:if test="${not empty event}">
                            <paw:eventRow id="${event.id}" nombre="${event.title}" fecha="${event.eventDate}"
                                          ubicacion="${event.neighborhood.displayName}" precio="${event.price}"
                                          imagenId="${event.imageId}">
                                <form action="<c:url value='/perfil/inscripciones/${registration.id}/cancelar'/>" method="post">
                                    <paw:button text="${cancelButtonLabel}" cssClass="btn-danger"/>
                                </form>
                            </paw:eventRow>
                        </c:if>
                    </c:forEach>
                </div>
            </c:otherwise>
        </c:choose>
    </main>
</div>

</body>
</html>
