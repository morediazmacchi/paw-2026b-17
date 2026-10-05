<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="event.attendees.title"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>

<paw:navbar>
    <spring:message code="event.attendees.backButton" var="backLabel"/>
    <a class="btn btn-medium" href="<c:url value='/perfil/eventos'/>"><c:out value="${backLabel}"/></a>
</paw:navbar>

<main class="page">
    <h1><spring:message code="event.attendees.heading" arguments="${event.title}" htmlEscape="true"/></h1>
    <p class="auth-subheading">
        <spring:message code="event.attendees.countLabel" arguments="${fn:length(registrations)}"/>
    </p>

    <c:if test="${param.revoked != null}">
        <p class="alert alert-success"><spring:message code="event.attendees.revoked"/></p>
    </c:if>

    <spring:message code="event.attendees.revokeButton" var="revokeButtonLabel"/>
    <c:choose>
        <c:when test="${empty registrations}">
            <p class="empty-state"><spring:message code="event.attendees.empty"/></p>
        </c:when>
        <c:otherwise>
            <div class="attendees-table-wrap">
                <table class="attendees-table">
                    <thead>
                    <tr>
                        <th><spring:message code="event.attendees.nameHeader"/></th>
                        <th><spring:message code="event.attendees.emailHeader"/></th>
                        <th><spring:message code="event.attendees.dateHeader"/></th>
                        <th><spring:message code="event.attendees.statusHeader"/></th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach var="registration" items="${registrations}">
                        <tr>
                            <td>
                                <c:choose>
                                    <c:when test="${not empty registration.name}"><c:out value="${registration.name}"/></c:when>
                                    <c:otherwise>&mdash;</c:otherwise>
                                </c:choose>
                            </td>
                            <td><c:out value="${registration.email}"/></td>
                            <td><fmt:formatDate value="${registration.registeredAt}" pattern="dd/MM/yyyy HH:mm"/></td>
                            <td>
                                <c:choose>
                                    <c:when test="${registration.status == 'CONFIRMED'}">
                                        <span class="pill pill-active"><spring:message code="event.attendees.statusConfirmed"/></span>
                                    </c:when>
                                    <c:when test="${registration.status == 'PENDING'}">
                                        <span class="pill"><spring:message code="event.attendees.statusPending"/></span>
                                    </c:when>
                                    <c:when test="${registration.status == 'REJECTED'}">
                                        <span class="pill"><spring:message code="event.attendees.statusRejected"/></span>
                                    </c:when>
                                    <c:otherwise>
                                        <span class="pill"><spring:message code="event.attendees.statusWaitlisted"/></span>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <%-- Solo a quien tiene el lugar confirmado se lo puede sacar: el
                                     lugar libre pasa solo al primero de la lista de espera. --%>
                                <c:if test="${registration.status == 'CONFIRMED'}">
                                    <form action="<c:url value='/perfil/eventos/${event.id}/inscriptos/${registration.id}/revocar'/>" method="post">
                                        <paw:button text="${revokeButtonLabel}" cssClass="btn-danger"/>
                                    </form>
                                </c:if>
                            </td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:otherwise>
    </c:choose>
</main>

</body>
</html>
