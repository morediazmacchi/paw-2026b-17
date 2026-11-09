<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="admin.allEvents.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/modal.js'/>"></script>
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<main class="page">
    <h1><spring:message code="admin.allEvents.heading"/></h1>
    <spring:message code="admin.reports.tabLabel" var="reportsTabLabel"/>
    <spring:message code="admin.reports.trackingTabLabel" var="trackingTabLabel"/>
    <spring:message code="admin.allEvents.tabLabel" var="allEventsTabLabel"/>
    <spring:message code="admin.users.tabLabel" var="usersTabLabel"/>
    <div class="auth-tabs">
        <a class="pill auth-tab" href="<c:url value='/admin/reportes'/>"><c:out value="${reportsTabLabel}"/></a>
        <a class="pill auth-tab" href="<c:url value='/admin/reportes/seguimiento'/>"><c:out value="${trackingTabLabel}"/></a>
        <span class="pill pill-active auth-tab"><c:out value="${allEventsTabLabel}"/></span>
        <a class="pill auth-tab" href="<c:url value='/admin/usuarios'/>"><c:out value="${usersTabLabel}"/></a>
    </div>

    <%-- Buscador por titulo + orden (GET: la busqueda queda en la URL y se puede compartir). --%>
    <spring:message code="admin.allEvents.searchPlaceholder" var="searchPlaceholder"/>
    <spring:message code="admin.allEvents.searchButton" var="searchButtonLabel"/>
    <form class="admin-toolbar" method="get" action="<c:url value='/admin/eventos'/>">
        <input type="search" name="q" maxlength="100" value="<c:out value='${q}'/>"
               placeholder="${searchPlaceholder}" aria-label="${searchPlaceholder}"/>
        <button type="submit" class="btn btn-medium btn-accent"><c:out value="${searchButtonLabel}"/></button>
        <%-- Con una busqueda activa: volver a ver todo de un clic, sin borrar el texto a mano (se conserva el orden). --%>
        <c:if test="${not empty q}">
            <a class="btn btn-medium" href="<c:url value='/admin/eventos'><c:param name='sort' value='${sort}'/></c:url>">
                <spring:message code="admin.allEvents.clearSearch"/>
            </a>
        </c:if>
        <label class="sort-select">
            <span><spring:message code="admin.allEvents.sortLabel"/></span>
            <select name="sort" onchange="this.form.submit()">
                <c:forEach var="option" items="${sortOptions}">
                    <option value="${option}" ${sort == option.name() ? 'selected="selected"' : ''}>
                        <spring:message code="admin.allEvents.sort.${option}"/>
                    </option>
                </c:forEach>
            </select>
        </label>
    </form>

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

    <%-- El id del admin que mira: sale del principal de la sesion (PlancitoUserDetails). --%>
    <sec:authentication property="principal.id" var="myId"/>
    <c:choose>
        <c:when test="${empty events}">
            <p class="empty-state">
                <c:choose>
                    <c:when test="${not empty q}">
                        <spring:message code="admin.allEvents.emptySearch"/>
                        <a href="<c:url value='/admin/eventos'><c:param name='sort' value='${sort}'/></c:url>"><spring:message code="admin.allEvents.viewAll"/></a>
                    </c:when>
                    <c:otherwise><spring:message code="admin.allEvents.empty"/></c:otherwise>
                </c:choose>
            </p>
        </c:when>
        <c:otherwise>
            <spring:message code="admin.allEvents.viewEventButton" var="viewEventButtonLabel"/>
            <spring:message code="admin.allEvents.viewRegistrationsButton" var="viewRegistrationsButtonLabel"/>
            <spring:message code="admin.allEvents.removedTag" var="removedTagLabel"/>
            <spring:message code="admin.pendingEvents.approveButton" var="approveButtonLabel"/>
            <spring:message code="admin.pendingEvents.requestChangesButton" var="requestChangesButtonLabel"/>
            <spring:message code="admin.pendingEvents.rejectButton" var="rejectButtonLabel"/>
            <spring:message code="admin.pendingEvents.requestChangesTitle" var="requestChangesTitleLabel"/>
            <spring:message code="admin.pendingEvents.requestChangesBody" var="requestChangesBodyLabel"/>
            <spring:message code="admin.pendingEvents.requestChangesLabel" var="requestChangesLabel"/>
            <spring:message code="admin.pendingEvents.requestChangesSubmitButton" var="requestChangesSubmitButtonLabel"/>
            <spring:message code="admin.pendingEvents.rejectConfirmBackButton" var="rejectConfirmBackButtonLabel"/>
            <spring:message code="admin.pendingEvents.rejectConfirmSubmitButton" var="rejectConfirmSubmitButtonLabel"/>
            <spring:message code="event.detail.closeButton" var="closeButtonLabel"/>
            <div class="event-row-list">
                <c:forEach var="stats" items="${events}">
                    <c:set var="event" value="${stats.event}"/>
                    <paw:eventRow id="${event.id}" nombre="${event.title}" fecha="${event.eventDate}"
                                  ubicacion="${event.neighborhood.displayName}" precio="${event.price}" gratis="${event.free}"
                                  imagenId="${event.imageId}" privado="${event.inviteOnly}"
                                  cancelado="${event.cancelled and not event.removedByAdmin}"
                                  pendiente="${event.pendingApproval}" rechazado="${event.rejected}"
                                  cambiosPedidos="${event.changesRequested}">
                        <%-- Dado de baja por un admin es distinto de un self-cancel: el eventRow no
                             distingue los dos (ambos son "cancelado" para el publico), asi que esto
                             agrega la etiqueta puntual que le interesa a quien modera. --%>
                        <c:if test="${event.removedByAdmin}">
                            <span class="event-row-cancelled-tag">(<c:out value="${removedTagLabel}"/>)</span>
                        </c:if>
                        <%-- Ya se le habian pedido cambios y el creador lo reenvio: contexto util para quien lo revisa. --%>
                        <c:if test="${event.pendingApproval and not empty event.changesRequestedMessage}">
                            <p class="admin-changes-history">
                                <spring:message code="admin.pendingEvents.previousChangesRequested" arguments="${event.changesRequestedMessage}" htmlEscape="true"/>
                            </p>
                        </c:if>
                        <a class="btn btn-medium" href="<c:url value='/eventos/${event.id}'/>">
                            <c:out value="${viewEventButtonLabel}"/>
                        </a>
                        <c:choose>
                            <%-- Pendiente de aprobacion: las mismas opciones que tenia la cola de pendientes (aprobar,
                                 rechazar). Todavia no tiene inscriptos que ver. --%>
                            <c:when test="${event.pendingApproval}">
                                <c:choose>
                                    <%-- Un admin no revisa su propio evento: no se le ofrecen los botones. --%>
                                    <c:when test="${not event.canBeModeratedBy(myId)}">
                                        <em><spring:message code="admin.pendingEvents.ownEvent"/></em>
                                    </c:when>
                                    <c:otherwise>
                                        <form action="<c:url value='/admin/eventos/${event.id}/aprobar'/>" method="post">
                                            <paw:button text="${approveButtonLabel}"/>
                                        </form>
                                        <button type="button" class="btn btn-medium" data-open-modal="changesModal-${event.id}">
                                            <c:out value="${requestChangesButtonLabel}"/>
                                        </button>
                                        <button type="button" class="btn btn-medium btn-danger" data-open-modal="rejectModal-${event.id}">
                                            <c:out value="${rejectButtonLabel}"/>
                                        </button>
                                    </c:otherwise>
                                </c:choose>
                            </c:when>
                            <c:otherwise>
                                <a class="btn btn-medium btn-accent" href="<c:url value='/admin/eventos/${event.id}/inscriptos'/>">
                                    <c:out value="${viewRegistrationsButtonLabel}"/>
                                    (<c:out value="${stats.confirmedCount}"/><c:if test="${not empty stats.pendingCount and stats.pendingCount > 0}">
                                        +<c:out value="${stats.pendingCount}"/></c:if>)
                                </a>
                            </c:otherwise>
                        </c:choose>
                    </paw:eventRow>
                </c:forEach>
            </div>
            <paw:pager baseUrl="/admin/eventos" currentPage="${currentPage}" totalPages="${totalPages}" extraParams="${pagerParams}"/>

            <c:forEach var="stats" items="${events}">
                <c:set var="event" value="${stats.event}"/>
                <c:if test="${event.pendingApproval and event.canBeModeratedBy(myId)}">
                    <div class="modal-overlay" id="changesModal-${event.id}" hidden>
                        <div class="modal-box">
                            <div class="modal-header">
                                <h2><c:out value="${requestChangesTitleLabel}"/></h2>
                                <button type="button" class="modal-close" data-close-modal aria-label="${closeButtonLabel}">
                                    <paw:icon name="close"/>
                                </button>
                            </div>
                            <div class="modal-body">
                                <p><c:out value="${event.title}"/></p>
                                <p><c:out value="${requestChangesBodyLabel}"/></p>
                                <form action="<c:url value='/admin/eventos/${event.id}/pedir-cambios'/>" method="post" novalidate="novalidate">
                                    <div class="form-group">
                                        <label for="changes-mensaje-${event.id}"><c:out value="${requestChangesLabel}"/></label>
                                        <textarea id="changes-mensaje-${event.id}" name="mensaje" maxlength="1000" required="required"></textarea>
                                    </div>
                                    <div class="modal-confirm-actions">
                                        <button type="button" class="btn btn-medium" data-close-modal>
                                            <c:out value="${rejectConfirmBackButtonLabel}"/>
                                        </button>
                                        <paw:button text="${requestChangesSubmitButtonLabel}"/>
                                    </div>
                                </form>
                            </div>
                        </div>
                    </div>
                    <div class="modal-overlay" id="rejectModal-${event.id}" hidden>
                        <div class="modal-box">
                            <div class="modal-header">
                                <h2><spring:message code="admin.pendingEvents.rejectConfirmTitle"/></h2>
                                <button type="button" class="modal-close" data-close-modal aria-label="${closeButtonLabel}">
                                    <paw:icon name="close"/>
                                </button>
                            </div>
                            <div class="modal-body">
                                <p><c:out value="${event.title}"/></p>
                                <p><spring:message code="admin.pendingEvents.rejectConfirmBody"/></p>
                                <form action="<c:url value='/admin/eventos/${event.id}/rechazar'/>" method="post" novalidate="novalidate">
                                    <div class="form-group">
                                        <label for="reject-motivo-${event.id}"><spring:message code="admin.pendingEvents.rejectReasonLabel"/></label>
                                        <textarea id="reject-motivo-${event.id}" name="motivo" maxlength="500" required="required"></textarea>
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
                </c:if>
            </c:forEach>
        </c:otherwise>
    </c:choose>
</main>
</jsp:body>
</paw:layout>
