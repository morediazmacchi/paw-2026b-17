<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="admin.tracking.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/modal.js'/>"></script>
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<main class="page">
    <h1><spring:message code="admin.tracking.heading"/></h1>
    <spring:message code="admin.reports.tabLabel" var="reportsTabLabel"/>
    <spring:message code="admin.reports.trackingTabLabel" var="trackingTabLabel"/>
    <spring:message code="admin.pendingEvents.tabLabel" var="pendingEventsTabLabel"/>
    <spring:message code="admin.users.tabLabel" var="usersTabLabel"/>
    <div class="auth-tabs">
        <a class="pill auth-tab" href="<c:url value='/admin/reportes'/>"><c:out value="${reportsTabLabel}"/></a>
        <span class="pill pill-active auth-tab"><c:out value="${trackingTabLabel}"/></span>
        <a class="pill auth-tab" href="<c:url value='/admin/eventos-pendientes'/>"><c:out value="${pendingEventsTabLabel}"/></a>
        <a class="pill auth-tab" href="<c:url value='/admin/usuarios'/>"><c:out value="${usersTabLabel}"/></a>
    </div>

        <c:if test="${not empty adminReportError}">
            <p class="alert">
                <c:choose>
                    <c:when test="${adminReportError == 'OWN_EVENT'}">
                        <spring:message code="admin.reports.ownEvent"/>
                    </c:when>
                    <c:otherwise>
                        <spring:message code="admin.reports.alreadyResolved"/>
                    </c:otherwise>
                </c:choose>
            </p>
        </c:if>

        <c:choose>
            <c:when test="${empty reports}">
                <p class="empty-state"><spring:message code="admin.tracking.empty"/></p>
            </c:when>
            <c:otherwise>
                <spring:message code="admin.reports.viewEventButton" var="viewEventButtonLabel"/>
                <spring:message code="admin.reports.dismissButton" var="dismissButtonLabel"/>
                <spring:message code="admin.reports.removeButton" var="removeButtonLabel"/>
                <spring:message code="admin.reports.removeConfirmBackButton" var="removeConfirmBackButtonLabel"/>
                <spring:message code="admin.reports.removeConfirmSubmitButton" var="removeConfirmSubmitButtonLabel"/>
                <spring:message code="event.detail.closeButton" var="closeButtonLabel"/>
                <spring:message code="admin.reports.eventHeader" var="eventHeaderLabel"/>
                <spring:message code="admin.reports.reportedBy" var="reportedByLabel"/>
                <spring:message code="admin.reports.reasonLabel" var="reasonLabel"/>
                <spring:message code="admin.tracking.requestedMessageHeader" var="requestedMessageLabel"/>
                <spring:message code="admin.tracking.requestedByHeader" var="requestedByLabel"/>
                <spring:message code="admin.tracking.requestedAtHeader" var="requestedAtLabel"/>
                <div class="responsive-table-wrap tracking-table-wrap">
                    <table class="responsive-table tracking-table">
                        <thead>
                        <tr>
                            <th><c:out value="${eventHeaderLabel}"/></th>
                            <th><c:out value="${reportedByLabel}"/></th>
                            <th><c:out value="${reasonLabel}"/></th>
                            <th><c:out value="${requestedMessageLabel}"/></th>
                            <th><c:out value="${requestedByLabel}"/></th>
                            <th><c:out value="${requestedAtLabel}"/></th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="report" items="${reports}">
                            <c:set var="reportedEvent" value="${eventsById[report.eventId]}"/>
                            <c:set var="reporter" value="${reportersById[report.reporterUserId]}"/>
                            <c:set var="requestedBy" value="${requestedByUsersById[report.changesRequestedByUserId]}"/>
                            <tr>
                                <td data-label="${eventHeaderLabel}">
                                    <c:choose>
                                        <c:when test="${not empty reportedEvent}">
                                            <a href="<c:url value='/eventos/${reportedEvent.id}'/>"><c:out value="${reportedEvent.title}"/></a>
                                        </c:when>
                                        <c:otherwise>&mdash;</c:otherwise>
                                    </c:choose>
                                </td>
                                <td data-label="${reportedByLabel}"><c:out value="${not empty reporter ? reporter.email : '—'}"/></td>
                                <td data-label="${reasonLabel}"><c:out value="${report.reason}"/></td>
                                <td data-label="${requestedMessageLabel}"><c:out value="${report.changesRequestedMessage}"/></td>
                                <td data-label="${requestedByLabel}"><c:out value="${not empty requestedBy ? requestedBy.email : '—'}"/></td>
                                <td data-label="${requestedAtLabel}"><paw:date value="${report.changesRequestedAt}" format="dateTimeTable"/></td>
                                <td class="admin-report-actions">
                                    <c:if test="${not empty reportedEvent}">
                                        <a class="btn btn-medium" href="<c:url value='/eventos/${reportedEvent.id}'/>"><c:out value="${viewEventButtonLabel}"/></a>
                                    </c:if>
                                    <form action="<c:url value='/admin/reportes/${report.id}/descartar'/>" method="post">
                                        <input type="hidden" name="origen" value="seguimiento"/>
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
                <paw:pager baseUrl="/admin/reportes/seguimiento" currentPage="${currentPage}" totalPages="${totalPages}"/>

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
                                    <input type="hidden" name="origen" value="seguimiento"/>
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
</jsp:body>
</paw:layout>
