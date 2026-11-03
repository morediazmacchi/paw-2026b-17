<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="auth.events.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/modal.js'/>"></script>
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<div class="account-layout">
    <paw:accountSidebar active="events" registrationsCount="${accountCounts.registrations}" favoritesCount="${accountCounts.favorites}" eventsCount="${accountCounts.events}" invitationsCount="${accountCounts.invitations}"/>

    <main class="account-main">
        <h1><spring:message code="auth.events.heading"/></h1>
        <p class="auth-subheading"><spring:message code="auth.events.subheading"/></p>

        <div class="creator-rating">
            <span class="review-display-heading"><spring:message code="auth.events.scoreLabel"/></span>
            <c:choose>
                <c:when test="${creatorReviewCount > 0}">
                    <paw:stars rating="${creatorAverageRating}"/>
                    <span class="creator-rating-count">
                        <fmt:formatNumber value="${creatorAverageRating}" maxFractionDigits="1"/>
                        (<spring:message code="review.countLabel" arguments="${creatorReviewCount}"/>)
                    </span>
                    <button type="button" class="btn-link" data-open-modal="myReviewsModal">
                        <spring:message code="auth.events.viewMyReviews"/>
                    </button>
                </c:when>
                <c:otherwise>
                    <span class="creator-rating-count"><spring:message code="auth.events.noReviewsYet"/></span>
                </c:otherwise>
            </c:choose>
        </div>

        <div class="creator-add-event">
            <spring:message code="event.list.addButton" var="addButtonLabel"/>
            <a class="btn btn-medium btn-accent" href="<c:url value='/eventos/publicar'/>"><c:out value="${addButtonLabel}"/></a>
        </div>

        <c:choose>
            <c:when test="${empty createdEvents}">
                <p class="empty-state"><spring:message code="auth.events.empty"/></p>
            </c:when>
            <c:otherwise>
                <spring:message code="auth.events.editButton" var="editButtonLabel"/>
                <spring:message code="auth.events.attendeesButton" var="attendeesButtonLabel"/>
                <spring:message code="auth.events.pendingButton" var="pendingButtonLabel"/>
                <spring:message code="auth.events.inviteButton" var="inviteButtonLabel"/>
                <spring:message code="auth.events.cancelButton" var="cancelButtonLabel"/>
                <spring:message code="auth.events.cancelConfirmBackButton" var="cancelConfirmBackButtonLabel"/>
                <spring:message code="auth.events.cancelConfirmSubmitButton" var="cancelConfirmSubmitButtonLabel"/>
                <spring:message code="event.detail.closeButton" var="closeButtonLabel"/>
                <div class="event-row-list">
                    <c:forEach var="stats" items="${createdEvents}">
                        <c:set var="event" value="${stats.event}"/>
                        <paw:eventRow id="${event.id}" nombre="${event.title}" fecha="${event.eventDate}"
                                      ubicacion="${event.neighborhood.displayName}" precio="${event.price}" gratis="${event.free}"
                                      imagenId="${event.imageId}" privado="${event.inviteOnly}"
                                      cancelado="${event.cancelled}" pendiente="${event.pendingApproval}"
                                      rechazado="${event.rejected}">
                            <a class="btn btn-medium" href="<c:url value='/eventos/${event.id}/inscriptos'/>">
                                <c:out value="${attendeesButtonLabel}"/>
                            </a>
                            <%-- Solo tiene sentido en un evento privado: uno publico ya
                                 tiene un link que se puede compartir sin necesitar esto. --%>
                            <c:if test="${event.inviteOnly}">
                                <a class="btn btn-medium btn-accent" href="<c:url value='/perfil/eventos/${event.id}/invitar'/>">
                                    <c:out value="${inviteButtonLabel}"/>
                                </a>
                            </c:if>
                            <%-- Solo tiene sentido para eventos con confirmacion manual:
                                 el resto no tiene nada pendiente que revisar. --%>
                            <c:if test="${event.requiresApproval}">
                                <a class="btn btn-medium btn-accent" href="<c:url value='/perfil/eventos/${event.id}/inscriptos'/>">
                                    <c:out value="${pendingButtonLabel}"/>
                                    <c:if test="${stats.pendingCount > 0}">
                                        (<c:out value="${stats.pendingCount}"/>)
                                    </c:if>
                                </a>
                            </c:if>
                            <%-- Rechazado es definitivo, mismo criterio que detail.jsp: no se edita. --%>
                            <c:if test="${event.editable}">
                                <a class="btn btn-medium btn-accent" href="<c:url value='/eventos/${event.id}/editar'/>">
                                    <c:out value="${editButtonLabel}"/>
                                </a>
                            </c:if>
                            <%-- Un evento ya cancelado no muestra el boton de nuevo: no hay
                                 nada mas que cancelar. --%>
                            <c:if test="${!event.cancelled}">
                                <button type="button" class="btn btn-medium btn-danger" data-open-modal="cancelModal-${event.id}">
                                    <c:out value="${cancelButtonLabel}"/>
                                </button>
                            </c:if>
                        </paw:eventRow>

                        <c:if test="${!event.cancelled}">
                            <div class="modal-overlay" id="cancelModal-${event.id}" hidden>
                                <div class="modal-box">
                                    <div class="modal-header">
                                        <h2><spring:message code="auth.events.cancelConfirmTitle"/></h2>
                                        <button type="button" class="modal-close" data-close-modal aria-label="${closeButtonLabel}">
                                            <paw:icon name="close"/>
                                        </button>
                                    </div>
                                    <div class="modal-body">
                                        <p><strong><c:out value="${event.title}"/></strong></p>
                                        <p>
                                            <spring:message code="auth.events.cancelConfirmBody"
                                                             arguments="${stats.confirmedCount}"/>
                                        </p>
                                        <div class="modal-confirm-actions">
                                            <button type="button" class="btn btn-medium" data-close-modal>
                                                <c:out value="${cancelConfirmBackButtonLabel}"/>
                                            </button>
                                            <form action="<c:url value='/eventos/${event.id}/cancelar'/>" method="post">
                                                <paw:button text="${cancelConfirmSubmitButtonLabel}" cssClass="btn-danger"/>
                                            </form>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </c:if>
                    </c:forEach>
                </div>
                <paw:pager baseUrl="/perfil/eventos" currentPage="${currentPage}" totalPages="${totalPages}"/>
            </c:otherwise>
        </c:choose>
    </main>
</div>

<c:if test="${creatorReviewCount > 0}">
    <%-- Abierto si se pidio una pagina de reseñas (los links de abajo): asi pasar de pagina no
         lo cierra. Sin ese parametro arranca cerrado y lo abre el boton (modal.js). --%>
    <div class="modal-overlay" id="myReviewsModal" <c:if test="${empty param.resenasPage}">hidden</c:if>>
        <div class="modal-box">
            <div class="modal-header">
                <h2><spring:message code="auth.events.myReviewsHeading"/></h2>
                <spring:message code="event.detail.closeButton" var="closeButtonLabel"/>
                <button type="button" class="modal-close" data-close-modal aria-label="${closeButtonLabel}">
                    <paw:icon name="close"/>
                </button>
            </div>
            <div class="modal-body">
                <c:forEach var="review" items="${myCreatorReviews}">
                    <c:set var="reviewedEvent" value="${reviewEventsById[review.eventId]}"/>
                    <div class="creator-review-item">
                        <c:if test="${not empty reviewedEvent}">
                            <div class="creator-review-event">
                                <span class="creator-review-event-title"><c:out value="${reviewedEvent.title}"/></span>
                                <span class="creator-review-date"><paw:date value="${reviewedEvent.eventDate}" format="date"/></span>
                            </div>
                        </c:if>
                        <paw:stars rating="${review.rating}"/>
                        <c:if test="${not empty review.comment}">
                            <p class="review-comment"><c:out value="${review.comment}"/></p>
                        </c:if>
                    </div>
                </c:forEach>
                <c:if test="${creatorReviewsTotalPages > 1}">
                    <nav class="pager">
                        <c:if test="${creatorReviewsPage > 1}">
                            <a class="pager-arrow" href="<c:url value='/perfil/eventos'><c:param name='page' value='${currentPage}'/><c:param name='resenasPage' value='${creatorReviewsPage - 1}'/></c:url>">
                                <spring:message code="event.list.prevPage"/>
                            </a>
                        </c:if>
                        <span class="pager-number pager-number-active"><c:out value="${creatorReviewsPage}"/> / <c:out value="${creatorReviewsTotalPages}"/></span>
                        <c:if test="${creatorReviewsPage < creatorReviewsTotalPages}">
                            <a class="pager-arrow" href="<c:url value='/perfil/eventos'><c:param name='page' value='${currentPage}'/><c:param name='resenasPage' value='${creatorReviewsPage + 1}'/></c:url>">
                                <spring:message code="event.list.nextPage"/>
                            </a>
                        </c:if>
                    </nav>
                </c:if>
            </div>
        </div>
    </div>
</c:if>
</jsp:body>
</paw:layout>
