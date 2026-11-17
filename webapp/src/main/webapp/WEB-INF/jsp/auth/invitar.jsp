<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="auth.invite.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
    <script src="<c:url value='/js/modal.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<%-- Recien publicado un evento privado (ver EventController.publish()): mismo
     popup que detail.jsp, la pantalla a la que llegaria un evento publico. --%>
<c:if test="${justPublished}">
    <spring:message code="event.detail.closeButton" var="justPublishedCloseLabel"/>
    <div class="modal-overlay" id="justPublishedModal">
        <div class="modal-box">
            <div class="modal-header">
                <h2><spring:message code="event.publish.pendingApprovalModalTitle"/></h2>
                <button type="button" class="modal-close" data-close-modal aria-label="${justPublishedCloseLabel}">
                    <paw:icon name="close"/>
                </button>
            </div>
            <div class="modal-body">
                <p><spring:message code="event.publish.pendingApprovalModalBody"/></p>
                <div class="modal-confirm-actions">
                    <button type="button" class="btn btn-medium btn-accent" data-close-modal>
                        <c:out value="${justPublishedCloseLabel}"/>
                    </button>
                </div>
            </div>
        </div>
    </div>
</c:if>

<div class="account-layout">
    <paw:accountSidebar active="events" registrationsCount="${accountCounts.registrations}" favoritesCount="${accountCounts.favorites}" eventsCount="${accountCounts.events}" invitationsCount="${accountCounts.invitations}"/>

    <main class="account-main">
        <h1><spring:message code="auth.invite.heading"/></h1>
        <p class="auth-subheading"><spring:message code="auth.invite.subheading" arguments="${event.title}" htmlEscape="true"/></p>

        <c:if test="${sent}">
            <p class="alert alert-success"><spring:message code="auth.invite.sentMessage"/></p>
        </c:if>
        <c:if test="${alreadyInvited}">
            <p class="alert"><spring:message code="auth.invite.alreadyInvitedMessage"/></p>
        </c:if>

        <c:if test="${param.full != null}">
            <p class="alert"><spring:message code="auth.invite.fullMessage"/></p>
        </c:if>
        <%-- No "event.private": "private" es palabra reservada y EL la rechaza (ver detail.jsp). --%>
        <c:if test="${not event.inviteOnly}">
            <p class="alert"><spring:message code="auth.invite.eventPublicMessage"/></p>
        </c:if>
        <%-- Privado pero sin aprobar: solo lo ven su creador y los admins, asi que una invitacion llevaria a
             un 404 (EventInvitationService.invite() devuelve NOT_APPROVED). --%>
        <c:if test="${event.inviteOnly and not event.approved}">
            <p class="alert"><spring:message code="auth.invite.notApprovedMessage"/></p>
        </c:if>
        <c:if test="${param.selfInvite != null}">
            <p class="alert"><spring:message code="auth.invite.selfInviteMessage"/></p>
        </c:if>

        <%-- El formulario y la lista solo tienen sentido en un evento privado ya aprobado; si no, queda
             solo el cartel de arriba. --%>
        <c:if test="${event.inviteOnly and event.approved}">
            <spring:message code="auth.invite.emailLabel" var="emailLabel"/>
            <spring:message code="auth.invite.submitButton" var="submitButtonLabel"/>
            <form action="<c:url value='/perfil/eventos/${event.id}/invitar'/>" method="post" novalidate="novalidate">
                <paw:input name="email" maxLength="150" label="${emailLabel}" type="email"
                           value="${inviteForm.email}" errorMessage="${fieldErrors.email}" />
                <paw:button text="${submitButtonLabel}" cssClass="btn-accent"/>
            </form>

            <hr class="profile-divider"/>
            <h2 class="profile-section-title"><spring:message code="auth.invite.listHeading"/></h2>

            <c:choose>
                <c:when test="${empty invitations}">
                    <p class="empty-state"><spring:message code="auth.invite.empty"/></p>
                </c:when>
                <c:otherwise>
                    <div class="pending-registration-list">
                        <c:forEach var="invitation" items="${invitations}">
                            <div class="pending-registration-row">
                                <c:set var="invitedAt"><paw:date value="${invitation.createdAt}" format="dateTimeTable"/></c:set>
                                <div class="pending-registration-info">
                                    <p class="pending-registration-name"><c:out value="${invitation.email}"/></p>
                                    <p class="pending-registration-meta">
                                        <spring:message code="auth.invite.invitedAt" arguments="${invitedAt}"/>
                                    </p>
                                </div>
                            </div>
                        </c:forEach>
                    </div>
                </c:otherwise>
            </c:choose>
            <paw:pager baseUrl="/perfil/eventos/${event.id}/invitar" currentPage="${currentPage}" totalPages="${totalPages}"/>
        </c:if>

        <p class="auth-subheading">
            <a class="btn-link" href="<c:url value='/perfil/eventos'/>" data-history-back>
                <spring:message code="auth.invite.backToEvents"/>
            </a>
        </p>
    </main>
</div>
</jsp:body>
</paw:layout>
