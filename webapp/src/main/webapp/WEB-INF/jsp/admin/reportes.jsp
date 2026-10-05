<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="admin.reports.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/modal.js'/>"></script>
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<main class="page">
    <h1><spring:message code="admin.reports.heading"/></h1>
    <spring:message code="admin.reports.tabLabel" var="reportsTabLabel"/>
    <spring:message code="admin.reports.trackingTabLabel" var="trackingTabLabel"/>
    <spring:message code="admin.users.tabLabel" var="usersTabLabel"/>
    <div class="auth-tabs">
        <span class="pill pill-active auth-tab"><c:out value="${reportsTabLabel}"/></span>
        <a class="pill auth-tab" href="<c:url value='/admin/reportes/seguimiento'/>"><c:out value="${trackingTabLabel}"/></a>
        <a class="pill auth-tab" href="<c:url value='/admin/usuarios'/>"><c:out value="${usersTabLabel}"/></a>
    </div>

        <c:if test="${not empty adminReportError}">
            <p class="alert">
                <c:choose>
                    <c:when test="${adminReportError == 'NOT_FOUND'}">
                        <spring:message code="admin.reports.alreadyResolved"/>
                    </c:when>
                    <c:when test="${adminReportError == 'OWN_EVENT'}">
                        <spring:message code="admin.reports.ownEvent"/>
                    </c:when>
                    <c:otherwise>
                        <spring:message code="admin.reports.requestChangesInvalid"/>
                    </c:otherwise>
                </c:choose>
            </p>
        </c:if>

        <c:choose>
            <c:when test="${empty reports}">
                <p class="empty-state"><spring:message code="admin.reports.empty"/></p>
            </c:when>
            <c:otherwise>
                <spring:message code="admin.reports.viewEventButton" var="viewEventButtonLabel"/>
                <spring:message code="admin.reports.dismissButton" var="dismissButtonLabel"/>
                <spring:message code="admin.reports.removeButton" var="removeButtonLabel"/>
                <spring:message code="admin.reports.removeConfirmBackButton" var="removeConfirmBackButtonLabel"/>
                <spring:message code="admin.reports.removeConfirmSubmitButton" var="removeConfirmSubmitButtonLabel"/>
                <spring:message code="admin.reports.requestChangesButton" var="requestChangesButtonLabel"/>
                <spring:message code="admin.reports.requestChangesSubmitButton" var="requestChangesSubmitButtonLabel"/>
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
                                    <c:if test="${not empty reportedEvent}">
                                        <a class="btn btn-medium" href="<c:url value='/eventos/${reportedEvent.id}'/>"><c:out value="${viewEventButtonLabel}"/></a>
                                    </c:if>
                                    <form action="<c:url value='/admin/reportes/${report.id}/descartar'/>" method="post">
                                        <paw:button text="${dismissButtonLabel}"/>
                                    </form>
                                    <button type="button" class="btn btn-medium" data-open-modal="changesModal-${report.id}">
                                        <c:out value="${requestChangesButtonLabel}"/>
                                    </button>
                                    <button type="button" class="btn btn-medium btn-danger" data-open-modal="removeModal-${report.id}">
                                        <c:out value="${removeButtonLabel}"/>
                                    </button>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
                <paw:pager baseUrl="/admin/reportes" currentPage="${currentPage}" totalPages="${totalPages}"/>

                <c:forEach var="report" items="${reports}">
                    <div class="modal-overlay" id="changesModal-${report.id}" hidden>
                        <div class="modal-box">
                            <div class="modal-header">
                                <h2><spring:message code="admin.reports.requestChangesTitle"/></h2>
                                <button type="button" class="modal-close" data-close-modal aria-label="${closeButtonLabel}">
                                    <paw:icon name="close"/>
                                </button>
                            </div>
                            <div class="modal-body">
                                <p><c:out value="${eventsById[report.eventId].title}"/></p>
                                <p><spring:message code="admin.reports.requestChangesBody"/></p>
                                <form action="<c:url value='/admin/reportes/${report.id}/pedir-cambios'/>" method="post" novalidate="novalidate">
                                    <div class="form-group">
                                        <label for="changes-message-${report.id}"><spring:message code="admin.reports.requestChangesLabel"/></label>
                                        <textarea id="changes-message-${report.id}" name="mensaje" maxlength="1000" required="required"></textarea>
                                    </div>
                                    <div class="modal-confirm-actions">
                                        <button type="button" class="btn btn-medium" data-close-modal>
                                            <c:out value="${removeConfirmBackButtonLabel}"/>
                                        </button>
                                        <paw:button text="${requestChangesSubmitButtonLabel}"/>
                                    </div>
                                </form>
                            </div>
                        </div>
                    </div>
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
</jsp:body>
</paw:layout>
