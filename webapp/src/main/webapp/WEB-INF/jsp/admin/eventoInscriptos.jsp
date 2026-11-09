<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="admin.eventRegistrations.title">
<paw:navbar>
    <spring:message code="admin.eventRegistrations.backButton" var="backLabel"/>
    <a class="btn btn-medium" href="<c:url value='/admin/eventos'/>" data-history-back><c:out value="${backLabel}"/></a>
</paw:navbar>

<main class="page">
    <h1><spring:message code="admin.eventRegistrations.heading" arguments="${event.title}" htmlEscape="true"/></h1>
    <p class="auth-subheading">
        <spring:message code="admin.eventRegistrations.countLabel" arguments="${totalRegistrations}"/>
    </p>

    <c:choose>
        <c:when test="${empty registrations}">
            <p class="empty-state"><spring:message code="admin.eventRegistrations.empty"/></p>
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
                            <td><paw:date value="${registration.registeredAt}" format="dateTimeTable"/></td>
                            <td>
                                <span class="pill${registration.confirmed ? ' pill-active' : ''}"><spring:message code="event.attendees.status.${registration.status}"/></span>
                            </td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:otherwise>
    </c:choose>
    <paw:pager baseUrl="/admin/eventos/${event.id}/inscriptos" currentPage="${currentPage}" totalPages="${totalPages}"/>
</main>
</paw:layout>
