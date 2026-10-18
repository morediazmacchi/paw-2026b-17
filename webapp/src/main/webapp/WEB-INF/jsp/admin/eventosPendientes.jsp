<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="admin.pendingEvents.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/modal.js'/>"></script>
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<main class="page">
    <h1><spring:message code="admin.pendingEvents.heading"/></h1>
    <spring:message code="admin.reports.tabLabel" var="reportsTabLabel"/>
    <spring:message code="admin.reports.trackingTabLabel" var="trackingTabLabel"/>
    <spring:message code="admin.pendingEvents.tabLabel" var="pendingEventsTabLabel"/>
    <spring:message code="admin.users.tabLabel" var="usersTabLabel"/>
    <div class="auth-tabs">
        <a class="pill auth-tab" href="<c:url value='/admin/reportes'/>"><c:out value="${reportsTabLabel}"/></a>
        <a class="pill auth-tab" href="<c:url value='/admin/reportes/seguimiento'/>"><c:out value="${trackingTabLabel}"/></a>
        <span class="pill pill-active auth-tab"><c:out value="${pendingEventsTabLabel}"/></span>
        <a class="pill auth-tab" href="<c:url value='/admin/usuarios'/>"><c:out value="${usersTabLabel}"/></a>
    </div>

        <c:if test="${not empty adminApprovalError}">
            <p class="alert">
                <c:choose>
                    <c:when test="${adminApprovalError == 'ALREADY_DECIDED'}">
                        <spring:message code="admin.pendingEvents.alreadyDecided"/>
                    </c:when>
                    <c:otherwise>
                        <spring:message code="admin.pendingEvents.invalid"/>
                    </c:otherwise>
                </c:choose>
            </p>
        </c:if>

        <c:choose>
            <c:when test="${empty pendingEvents}">
                <p class="empty-state"><spring:message code="admin.pendingEvents.empty"/></p>
            </c:when>
            <c:otherwise>
                <spring:message code="admin.pendingEvents.viewEventButton" var="viewEventButtonLabel"/>
                <spring:message code="admin.pendingEvents.approveButton" var="approveButtonLabel"/>
                <spring:message code="admin.pendingEvents.rejectButton" var="rejectButtonLabel"/>
                <spring:message code="admin.pendingEvents.rejectConfirmBackButton" var="rejectConfirmBackButtonLabel"/>
                <spring:message code="admin.pendingEvents.rejectConfirmSubmitButton" var="rejectConfirmSubmitButtonLabel"/>
                <spring:message code="event.detail.closeButton" var="closeButtonLabel"/>
                <div class="attendees-table-wrap">
                    <table class="attendees-table">
                        <thead>
                        <tr>
                            <th><spring:message code="admin.pendingEvents.eventHeader"/></th>
                            <th><spring:message code="admin.pendingEvents.organizerHeader"/></th>
                            <th><spring:message code="admin.pendingEvents.dateHeader"/></th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="pendingEvent" items="${pendingEvents}">
                            <tr>
                                <td><a href="<c:url value='/eventos/${pendingEvent.id}'/>"><c:out value="${pendingEvent.title}"/></a></td>
                                <td><c:out value="${pendingEvent.creatorEmail}"/></td>
                                <td><fmt:formatDate value="${pendingEvent.createdAt}" pattern="dd/MM/yyyy HH:mm"/></td>
                                <td class="admin-report-actions">
                                    <a class="btn btn-medium" href="<c:url value='/eventos/${pendingEvent.id}'/>"><c:out value="${viewEventButtonLabel}"/></a>
                                    <form action="<c:url value='/admin/eventos-pendientes/${pendingEvent.id}/aprobar'/>" method="post">
                                        <paw:button text="${approveButtonLabel}"/>
                                    </form>
                                    <button type="button" class="btn btn-medium btn-danger" data-open-modal="rejectModal-${pendingEvent.id}">
                                        <c:out value="${rejectButtonLabel}"/>
                                    </button>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
                <paw:pager baseUrl="/admin/eventos-pendientes" currentPage="${currentPage}" totalPages="${totalPages}"/>

                <c:forEach var="pendingEvent" items="${pendingEvents}">
                    <div class="modal-overlay" id="rejectModal-${pendingEvent.id}" hidden>
                        <div class="modal-box">
                            <div class="modal-header">
                                <h2><spring:message code="admin.pendingEvents.rejectConfirmTitle"/></h2>
                                <button type="button" class="modal-close" data-close-modal aria-label="${closeButtonLabel}">
                                    <paw:icon name="close"/>
                                </button>
                            </div>
                            <div class="modal-body">
                                <p><c:out value="${pendingEvent.title}"/></p>
                                <p><spring:message code="admin.pendingEvents.rejectConfirmBody"/></p>
                                <form action="<c:url value='/admin/eventos-pendientes/${pendingEvent.id}/rechazar'/>" method="post" novalidate="novalidate">
                                    <div class="form-group">
                                        <label for="reject-motivo-${pendingEvent.id}"><spring:message code="admin.pendingEvents.rejectReasonLabel"/></label>
                                        <textarea id="reject-motivo-${pendingEvent.id}" name="motivo" maxlength="500" required="required"></textarea>
                                    </div>
                                    <div class="modal-confirm-actions">
                                        <button type="button" class="btn btn-medium" data-close-modal>
                                            <c:out value="${rejectConfirmBackButtonLabel}"/>
                                        </button>
                                        <paw:button text="${rejectConfirmSubmitButtonLabel}" cssClass="btn-danger"/>
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
