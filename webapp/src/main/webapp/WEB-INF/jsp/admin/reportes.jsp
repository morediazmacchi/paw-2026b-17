<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="admin.reports.title"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script src="<c:url value='/js/modal.js'/>"></script>
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</head>
<body>

<paw:navbar>
    <spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>"><c:out value="${backToEventsLabel}"/></a>
</paw:navbar>

<main class="page">
    <h1><spring:message code="admin.reports.heading"/></h1>
    <spring:message code="admin.reports.tabLabel" var="reportsTabLabel"/>
    <spring:message code="admin.users.tabLabel" var="usersTabLabel"/>
    <div class="auth-tabs">
        <span class="pill pill-active auth-tab"><c:out value="${reportsTabLabel}"/></span>
        <a class="pill auth-tab" href="<c:url value='/admin/usuarios'/>"><c:out value="${usersTabLabel}"/></a>
    </div>

        <c:choose>
            <c:when test="${empty reports}">
                <p class="empty-state"><spring:message code="admin.reports.empty"/></p>
            </c:when>
            <c:otherwise>
                <spring:message code="admin.reports.dismissButton" var="dismissButtonLabel"/>
                <spring:message code="admin.reports.removeButton" var="removeButtonLabel"/>
                <spring:message code="admin.reports.removeConfirmBackButton" var="removeConfirmBackButtonLabel"/>
                <spring:message code="admin.reports.removeConfirmSubmitButton" var="removeConfirmSubmitButtonLabel"/>
                <spring:message code="event.detail.closeButton" var="closeButtonLabel"/>
                <div class="attendees-table-wrap">
                    <table class="attendees-table">
                        <thead>
                        <tr>
                            <th><spring:message code="admin.reports.eventHeader"/></th>
                            <th><spring:message code="admin.reports.reportedBy"/></th>
                            <th><spring:message code="admin.reports.reasonLabel"/></th>
                            <th><spring:message code="admin.reports.dateLabel"/></th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="report" items="${reports}">
                            <c:set var="reportedEvent" value="${eventsById[report.eventId]}"/>
                            <c:set var="reporter" value="${reportersById[report.reporterUserId]}"/>
                            <tr>
                                <td>
                                    <c:choose>
                                        <c:when test="${not empty reportedEvent}">
                                            <a href="<c:url value='/eventos/${reportedEvent.id}'/>"><c:out value="${reportedEvent.title}"/></a>
                                        </c:when>
                                        <c:otherwise>&mdash;</c:otherwise>
                                    </c:choose>
                                </td>
                                <td><c:out value="${not empty reporter ? reporter.email : '—'}"/></td>
                                <td><c:out value="${report.reason}"/></td>
                                <td><fmt:formatDate value="${report.createdAt}" pattern="dd/MM/yyyy HH:mm"/></td>
                                <td class="admin-report-actions">
                                    <form action="<c:url value='/admin/reportes/${report.id}/descartar'/>" method="post">
                                        <paw:button text="${dismissButtonLabel}"/>
                                    </form>
                                    <button type="button" class="btn btn-medium btn-danger" data-open-modal="removeModal-${report.id}">
                                        <c:out value="${removeButtonLabel}"/>
                                    </button>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>

                <c:forEach var="report" items="${reports}">
                    <div class="modal-overlay" id="removeModal-${report.id}" hidden>
                        <div class="modal-box">
                            <div class="modal-header">
                                <h2><spring:message code="admin.reports.removeConfirmTitle"/></h2>
                                <button type="button" class="modal-close" data-close-modal aria-label="${closeButtonLabel}">
                                    <paw:icon name="close"/>
                                </button>
                            </div>
                            <div class="modal-body">
                                <p><c:out value="${eventsById[report.eventId].title}"/></p>
                                <p><spring:message code="admin.reports.removeConfirmBody"/></p>
                                <form action="<c:url value='/admin/reportes/${report.id}/dar-de-baja'/>" method="post">
                                    <label class="filter-checkbox">
                                        <input type="checkbox" name="suspenderOrganizador" value="true"/>
                                        <spring:message code="admin.reports.suspendOrganizerLabel"/>
                                    </label>
                                    <div class="modal-confirm-actions">
                                        <button type="button" class="btn btn-medium" data-close-modal>
                                            <c:out value="${removeConfirmBackButtonLabel}"/>
                                        </button>
                                        <paw:button text="${removeConfirmSubmitButtonLabel}" cssClass="btn-danger"/>
                                    </div>
                                </form>
                            </div>
                        </div>
                    </div>
                </c:forEach>
            </c:otherwise>
        </c:choose>
</main>

</body>
</html>
