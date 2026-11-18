<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="admin.reports.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/modal.js'/>"></script>
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
    <script src="<c:url value='/js/admin-reports.js'/>" defer></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<%-- data-open-report: si se volvio de "Ver seguimiento" (ver AdminController.verReporte()), admin-reports.js
     abre de una el modal de ese reporte. --%>
<main class="page" data-open-report="<c:out value='${abierto}'/>">
    <h1><spring:message code="admin.reports.heading"/></h1>
    <p class="auth-subheading"><spring:message code="admin.reports.subheading"/></p>
    <spring:message code="admin.reports.tabLabel" var="reportsTabLabel"/>
    <spring:message code="admin.allEvents.tabLabel" var="allEventsTabLabel"/>
    <spring:message code="admin.users.tabLabel" var="usersTabLabel"/>
    <div class="auth-tabs">
        <span class="pill pill-active auth-tab"><c:out value="${reportsTabLabel}"/></span>
        <a class="pill auth-tab" href="<c:url value='/admin/eventos'/>"><c:out value="${allEventsTabLabel}"/></a>
        <a class="pill auth-tab" href="<c:url value='/admin/usuarios'/>"><c:out value="${usersTabLabel}"/></a>
    </div>

    <%-- Buscador por evento/usuario/motivo (GET: la busqueda queda en la URL y se puede compartir), conserva
         la pestaña elegida. --%>
    <spring:message code="admin.reports.searchPlaceholder" var="searchPlaceholder"/>
    <spring:message code="admin.reports.searchButton" var="searchButtonLabel"/>
    <form class="admin-toolbar" method="get" action="<c:url value='/admin/reportes'/>">
        <input type="hidden" name="filtro" value="${filtro}"/>
        <input type="search" name="q" maxlength="100" value="<c:out value='${q}'/>"
               placeholder="${searchPlaceholder}" aria-label="${searchPlaceholder}"/>
        <button type="submit" class="btn btn-medium btn-accent"><c:out value="${searchButtonLabel}"/></button>
        <c:if test="${not empty q}">
            <a class="btn btn-medium" href="<c:url value='/admin/reportes'><c:param name='filtro' value='${filtro}'/></c:url>">
                <spring:message code="admin.reports.clearSearch"/>
            </a>
        </c:if>
    </form>

    <%-- Pestañas de estado, con el total de cada una (ReportStatusCounts: no cambia al escribir en el
         buscador, para que sirvan de referencia fija). Cada link resetea la pagina, conserva la busqueda. --%>
    <spring:message code="admin.reports.filter.ALL" var="filterAllLabel"/>
    <spring:message code="admin.reports.filter.NEW" var="filterNewLabel"/>
    <spring:message code="admin.reports.filter.IN_REVIEW" var="filterInReviewLabel"/>
    <spring:message code="admin.reports.filter.CHANGES_REQUESTED" var="filterChangesRequestedLabel"/>
    <spring:message code="admin.reports.filter.RESOLVED" var="filterResolvedLabel"/>
    <nav class="report-filter-tabs">
        <a class="pill${filtro == 'ALL' ? ' pill-active' : ''}"
           href="<c:url value='/admin/reportes'><c:if test='${not empty q}'><c:param name='q' value='${q}'/></c:if></c:url>">
            <c:out value="${filterAllLabel}"/><span class="pill-count">${counts.total}</span>
        </a>
        <a class="pill report-filter-new${filtro == 'NEW' ? ' pill-active' : ''}"
           href="<c:url value='/admin/reportes'><c:param name='filtro' value='NEW'/><c:if test='${not empty q}'><c:param name='q' value='${q}'/></c:if></c:url>">
            <c:out value="${filterNewLabel}"/><span class="pill-count">${counts.nuevos}</span>
        </a>
        <a class="pill${filtro == 'IN_REVIEW' ? ' pill-active' : ''}"
           href="<c:url value='/admin/reportes'><c:param name='filtro' value='IN_REVIEW'/><c:if test='${not empty q}'><c:param name='q' value='${q}'/></c:if></c:url>">
            <c:out value="${filterInReviewLabel}"/><span class="pill-count">${counts.enRevision}</span>
        </a>
        <a class="pill${filtro == 'CHANGES_REQUESTED' ? ' pill-active' : ''}"
           href="<c:url value='/admin/reportes'><c:param name='filtro' value='CHANGES_REQUESTED'/><c:if test='${not empty q}'><c:param name='q' value='${q}'/></c:if></c:url>">
            <c:out value="${filterChangesRequestedLabel}"/><span class="pill-count">${counts.modificacionSolicitada}</span>
        </a>
        <a class="pill${filtro == 'RESOLVED' ? ' pill-active' : ''}"
           href="<c:url value='/admin/reportes'><c:param name='filtro' value='RESOLVED'/><c:if test='${not empty q}'><c:param name='q' value='${q}'/></c:if></c:url>">
            <c:out value="${filterResolvedLabel}"/><span class="pill-count">${counts.resueltos}</span>
        </a>
    </nav>

    <c:if test="${not empty adminReportError}">
        <p class="alert">
            <c:choose>
                <c:when test="${adminReportError == 'NOT_FOUND'}">
                    <spring:message code="admin.reports.alreadyResolved"/>
                </c:when>
                <c:otherwise>
                    <spring:message code="admin.reports.requestChangesInvalid"/>
                </c:otherwise>
            </c:choose>
        </p>
    </c:if>

    <%-- El id del admin que mira: sale del principal de la sesion (PlancitoUserDetails). --%>
    <sec:authentication property="principal.id" var="myId"/>
    <c:choose>
        <c:when test="${empty reports}">
            <p class="empty-state"><spring:message code="admin.reports.empty"/></p>
        </c:when>
        <c:otherwise>
            <p class="reports-caption">
                <spring:message code="admin.reports.showingCaption">
                    <spring:argument value="${reportsPage.firstItem}"/>
                    <spring:argument value="${reportsPage.lastItem}"/>
                    <spring:argument value="${totalElements}"/>
                </spring:message>
            </p>
            <spring:message code="admin.reports.viewEventButton" var="viewEventButtonLabel"/>
            <spring:message code="admin.reports.viewTrackingButton" var="viewTrackingButtonLabel"/>
            <spring:message code="admin.reports.dismissButton" var="dismissButtonLabel"/>
            <spring:message code="admin.reports.removeButton" var="removeButtonLabel"/>
            <spring:message code="admin.reports.removeConfirmBackButton" var="removeConfirmBackButtonLabel"/>
            <spring:message code="admin.reports.removeConfirmSubmitButton" var="removeConfirmSubmitButtonLabel"/>
            <spring:message code="admin.reports.requestChangesButton" var="requestChangesButtonLabel"/>
            <spring:message code="admin.reports.requestChangesTitle" var="requestChangesTitleLabel"/>
            <spring:message code="admin.reports.requestChangesBody" var="requestChangesBodyLabel"/>
            <spring:message code="admin.reports.requestChangesLabel" var="requestChangesLabel"/>
            <spring:message code="admin.reports.requestChangesSubmitButton" var="requestChangesSubmitButtonLabel"/>
            <spring:message code="admin.reports.trackingModalTitle" var="trackingModalTitleLabel"/>
            <spring:message code="event.detail.closeButton" var="closeButtonLabel"/>
            <spring:message code="admin.reports.eventHeader" var="eventHeaderLabel"/>
            <spring:message code="admin.reports.reportedBy" var="reportedByLabel"/>
            <spring:message code="admin.reports.reasonLabel" var="reasonHeaderLabel"/>
            <spring:message code="admin.reports.statusHeader" var="statusHeaderLabel"/>
            <spring:message code="admin.reports.lastUpdateLabel" var="lastUpdateLabel"/>
            <div class="responsive-table-wrap">
                <table class="responsive-table reports-table">
                    <thead>
                    <tr>
                        <th><c:out value="${eventHeaderLabel}"/></th>
                        <th><c:out value="${reportedByLabel}"/></th>
                        <th><c:out value="${reasonHeaderLabel}"/></th>
                        <th><c:out value="${statusHeaderLabel}"/></th>
                        <th><c:out value="${lastUpdateLabel}"/></th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach var="report" items="${reports}">
                        <c:set var="reportedEvent" value="${report.event}"/>
                        <c:set var="reporter" value="${report.reporter}"/>
                        <tr>
                            <td data-label="${eventHeaderLabel}">
                                <c:choose>
                                    <c:when test="${not empty reportedEvent}">
                                        <a href="<c:url value='/eventos/${reportedEvent.id}'/>"><c:out value="${reportedEvent.title}"/></a>
                                        <c:if test="${reportedEvent.isCreatedBy(myId)}"><paw:ownEventBadge/></c:if>
                                    </c:when>
                                    <c:otherwise>&mdash;</c:otherwise>
                                </c:choose>
                            </td>
                            <td data-label="${reportedByLabel}"><c:out value="${not empty reporter ? reporter.email : '—'}"/></td>
                            <td data-label="${reasonHeaderLabel}"><c:out value="${report.reason}"/></td>
                            <td data-label="${statusHeaderLabel}">
                                <c:choose>
                                    <c:when test="${report.status == 'PENDING' and empty report.viewedAt}">
                                        <span class="report-status report-status-new"><spring:message code="admin.reports.status.NEW"/></span>
                                    </c:when>
                                    <c:when test="${report.status == 'PENDING'}">
                                        <span class="report-status report-status-in-review"><spring:message code="admin.reports.status.IN_REVIEW"/></span>
                                    </c:when>
                                    <c:when test="${report.status == 'CHANGES_REQUESTED'}">
                                        <span class="report-status report-status-changes-requested"><spring:message code="admin.reports.status.CHANGES_REQUESTED"/></span>
                                    </c:when>
                                    <c:otherwise>
                                        <span class="report-status report-status-resolved"><spring:message code="admin.reports.status.RESOLVED"/></span>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td data-label="${lastUpdateLabel}"><paw:date value="${report.lastActivityAt}" format="dateTimeTable"/></td>
                            <td class="admin-actions-cell">
                            <div class="admin-report-actions">
                                <c:if test="${not empty reportedEvent}">
                                    <a class="btn btn-medium" href="<c:url value='/eventos/${reportedEvent.id}'/>"><c:out value="${viewEventButtonLabel}"/></a>
                                </c:if>
                                <button type="button" class="btn btn-medium" data-open-modal="seguimientoModal-${report.id}">
                                    <c:out value="${viewTrackingButtonLabel}"/>
                                </button>
                            </div>
                            </td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
            <paw:pager baseUrl="/admin/reportes" currentPage="${currentPage}" totalPages="${totalPages}" extraParams="${pagerParams}"/>

            <c:forEach var="report" items="${reports}">
                <c:set var="reportedEvent" value="${report.event}"/>
                <c:set var="reporter" value="${report.reporter}"/>
                <c:set var="requestedBy" value="${report.changesRequestedBy}"/>
                <%-- Seguimiento: historial de solo lectura, nunca escondido (mirar el historial no es una
                     decision de moderacion, ver AdminController.verReporte()). Abrirlo es un POST (marca
                     "visto"): nunca un GET con efecto de escritura. --%>
                <div class="modal-overlay" id="seguimientoModal-${report.id}" hidden>
                    <div class="modal-box">
                        <div class="modal-header">
                            <h2><c:out value="${trackingModalTitleLabel}"/></h2>
                            <button type="button" class="modal-close" data-close-modal aria-label="${closeButtonLabel}">
                                <paw:icon name="close"/>
                            </button>
                        </div>
                        <div class="modal-body">
                            <p><strong><c:out value="${not empty reportedEvent ? reportedEvent.title : '—'}"/></strong></p>
                            <div class="tracking-timeline">
                                <p>
                                    <spring:message code="admin.reports.trackingReportedLabel"/>
                                    <paw:date value="${report.createdAt}" format="dateTimeTable"/>,
                                    <c:out value="${not empty reporter ? reporter.email : '—'}"/>.
                                </p>
                                <p><spring:message code="admin.reports.trackingReasonLabel"/> <c:out value="${report.reason}"/></p>
                                <c:if test="${report.status == 'PENDING' and not empty report.viewedAt}">
                                    <p>
                                        <spring:message code="admin.reports.trackingInReviewLabel"/>
                                        <paw:date value="${report.viewedAt}" format="dateTimeTable"/>.
                                    </p>
                                </c:if>
                                <c:if test="${not empty report.changesRequestedAt}">
                                    <p>
                                        <spring:message code="admin.reports.trackingChangesRequestedLabel"/>
                                        <paw:date value="${report.changesRequestedAt}" format="dateTimeTable"/>,
                                        <c:out value="${not empty requestedBy ? requestedBy.email : '—'}"/>:
                                    </p>
                                    <p class="admin-changes-history"><c:out value="${report.changesRequestedMessage}"/></p>
                                </c:if>
                                <c:if test="${report.status == 'DISMISSED'}">
                                    <p>
                                        <spring:message code="admin.reports.trackingDismissedLabel"/>
                                        <paw:date value="${report.resolvedAt}" format="dateTimeTable"/>.
                                    </p>
                                </c:if>
                                <c:if test="${report.status == 'ACTIONED'}">
                                    <p>
                                        <spring:message code="admin.reports.trackingActionedLabel"/>
                                        <paw:date value="${report.resolvedAt}" format="dateTimeTable"/>.
                                    </p>
                                </c:if>
                            </div>
                            <c:if test="${not empty reportedEvent}">
                                <form action="<c:url value='/admin/reportes/${report.id}/ver'/>" method="post">
                                    <input type="hidden" name="returnTo" value="${fn:escapeXml(returnTo)}"/>
                                </form>
                            </c:if>
                            <%-- Acciones de moderacion: solo si el reporte sigue abierto y no es el propio
                                 evento del admin que mira (conflicto de intereses, ver AdminSecurity.canResolveReport). --%>
                            <c:if test="${report.status == 'PENDING' or report.status == 'CHANGES_REQUESTED'}">
                                <c:choose>
                                    <c:when test="${not empty reportedEvent and not reportedEvent.canBeModeratedBy(myId)}">
                                        <p><em><spring:message code="admin.reports.ownEvent"/></em></p>
                                    </c:when>
                                    <c:otherwise>
                                        <div class="modal-confirm-actions">
                                            <form action="<c:url value='/admin/reportes/${report.id}/descartar'/>" method="post">
                                                <input type="hidden" name="returnTo" value="${fn:escapeXml(returnTo)}"/>
                                                <paw:button text="${dismissButtonLabel}"/>
                                            </form>
                                            <c:if test="${report.status == 'PENDING'}">
                                                <paw:button text="${requestChangesButtonLabel}" openModal="changesModal-${report.id}"/>
                                            </c:if>
                                            <paw:button text="${removeButtonLabel}" cssClass="btn-danger" openModal="removeModal-${report.id}"/>
                                        </div>
                                    </c:otherwise>
                                </c:choose>
                            </c:if>
                        </div>
                    </div>
                </div>

                <c:if test="${(report.status == 'PENDING' or report.status == 'CHANGES_REQUESTED') and not empty reportedEvent and reportedEvent.canBeModeratedBy(myId)}">
                    <c:if test="${report.status == 'PENDING'}">
                        <div class="modal-overlay" id="changesModal-${report.id}" hidden>
                            <div class="modal-box">
                                <div class="modal-header">
                                    <h2><c:out value="${requestChangesTitleLabel}"/></h2>
                                    <button type="button" class="modal-close" data-close-modal aria-label="${closeButtonLabel}">
                                        <paw:icon name="close"/>
                                    </button>
                                </div>
                                <div class="modal-body">
                                    <p><c:out value="${reportedEvent.title}"/></p>
                                    <p><c:out value="${requestChangesBodyLabel}"/></p>
                                    <form action="<c:url value='/admin/reportes/${report.id}/pedir-cambios'/>" method="post" novalidate="novalidate">
                                        <input type="hidden" name="returnTo" value="${fn:escapeXml(returnTo)}"/>
                                        <div class="form-group">
                                            <label for="changes-message-${report.id}"><c:out value="${requestChangesLabel}"/></label>
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
                    </c:if>
                    <div class="modal-overlay" id="removeModal-${report.id}" hidden>
                        <div class="modal-box">
                            <div class="modal-header">
                                <h2><spring:message code="admin.reports.removeConfirmTitle"/></h2>
                                <button type="button" class="modal-close" data-close-modal aria-label="${closeButtonLabel}">
                                    <paw:icon name="close"/>
                                </button>
                            </div>
                            <div class="modal-body">
                                <p><c:out value="${reportedEvent.title}"/></p>
                                <p><spring:message code="admin.reports.removeConfirmBody"/></p>
                                <form action="<c:url value='/admin/reportes/${report.id}/dar-de-baja'/>" method="post">
                                    <input type="hidden" name="returnTo" value="${fn:escapeXml(returnTo)}"/>
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
                </c:if>
            </c:forEach>
        </c:otherwise>
    </c:choose>
</main>
</jsp:body>
</paw:layout>
