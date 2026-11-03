<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="auth.registrations.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
    <script src="<c:url value='/js/modal.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<div class="account-layout">
    <paw:accountSidebar active="registrations" registrationsCount="${accountCounts.registrations}" favoritesCount="${accountCounts.favorites}" eventsCount="${accountCounts.events}" invitationsCount="${accountCounts.invitations}"/>

    <main class="account-main">
        <h1><spring:message code="auth.registrations.heading"/></h1>
        <p class="auth-subheading"><spring:message code="auth.registrations.subheading"/></p>

        <c:if test="${cancelled}">
            <p class="alert alert-success"><spring:message code="auth.registrations.cancelledMessage"/></p>
        </c:if>
        <c:if test="${reviewed}">
            <p class="alert alert-success"><spring:message code="auth.registrations.reviewedMessage"/></p>
        </c:if>
        <c:if test="${reviewError}">
            <p class="alert"><spring:message code="auth.registrations.reviewErrorMessage"/></p>
        </c:if>
        <c:if test="${alreadyReviewed}">
            <p class="alert"><spring:message code="auth.registrations.alreadyReviewedMessage"/></p>
        </c:if>

        <c:choose>
            <c:when test="${empty upcomingRegistrations.content and empty pastRegistrations.content}">
                <p class="empty-state"><spring:message code="auth.registrations.empty"/></p>
            </c:when>
            <c:otherwise>
                <spring:message code="auth.registrations.cancelButton" var="cancelButtonLabel"/>

                <%-- Una seccion sin items no se muestra para nada (ni titulo ni paginado). --%>
                <c:if test="${not empty upcomingRegistrations.content}">
                    <h2 class="profile-section-title"><spring:message code="auth.registrations.upcomingHeading"/></h2>
                    <div class="event-row-list">
                        <c:forEach var="entry" items="${upcomingRegistrations.content}">
                            <c:set var="registration" value="${entry.registration}"/>
                            <c:set var="event" value="${entry.event}"/>
                            <paw:eventRow id="${event.id}" nombre="${event.title}" fecha="${event.eventDate}"
                                          ubicacion="${event.neighborhood.displayName}" precio="${event.price}" gratis="${event.free}"
                                          imagenId="${event.imageId}" cancelado="${event.cancelled}">
                                <%-- Evento cancelado por la organizadora: se muestra con el cartel,
                                     pero no hay inscripcion que cancelar. --%>
                                <paw:registrationStatus status="${registration.status}"/>
                                <c:if test="${entry.canCancel}">
                                    <form action="<c:url value='/perfil/inscripciones/${registration.id}/cancelar'/>" method="post">
                                        <paw:button text="${cancelButtonLabel}" cssClass="btn-danger"/>
                                    </form>
                                </c:if>
                            </paw:eventRow>
                        </c:forEach>
                    </div>
                    <paw:pager baseUrl="/perfil/inscripciones" pageParam="proximasPage"
                               currentPage="${upcomingRegistrations.page}" totalPages="${upcomingRegistrations.totalPages}"
                               otherParam="finalizadasPage" otherPage="${pastRegistrations.page}"/>
                </c:if>

                <c:if test="${not empty pastRegistrations.content}">
                    <c:if test="${not empty upcomingRegistrations.content}">
                        <hr class="profile-divider"/>
                    </c:if>
                    <h2 class="profile-section-title"><spring:message code="auth.registrations.pastHeading"/></h2>
                    <spring:message code="auth.registrations.reviewButton" var="reviewButtonLabel"/>
                    <spring:message code="event.detail.closeButton" var="closeButtonLabel"/>
                    <div class="event-row-list event-row-list-past">
                        <c:forEach var="entry" items="${pastRegistrations.content}">
                            <c:set var="registration" value="${entry.registration}"/>
                            <c:set var="event" value="${entry.event}"/>
                            <c:set var="myReview" value="${entry.myReview}"/>
                            <div>
                                <paw:eventRow id="${event.id}" nombre="${event.title}" fecha="${event.eventDate}"
                                              ubicacion="${event.neighborhood.displayName}" precio="${event.price}" gratis="${event.free}"
                                              imagenId="${event.imageId}" cancelado="${event.cancelled}">
                                    <%-- Integrado en la misma card, a la derecha, como el resto de los
                                         botones de accion (Cancelar en los eventos que vienen).
                                         Solo si tuvo su lugar CONFIRMADO: en lista de espera,
                                         pendiente o rechazada no fue, y ReviewService lo rechazaria
                                         igual (NOT_ELIGIBLE). Mejor no ofrecer el boton. --%>
                                    <c:if test="${entry.canReview}">
                                        <button type="button" class="btn btn-medium btn-accent" data-open-modal="reviewModal-${event.id}">
                                            <c:out value="${reviewButtonLabel}"/>
                                        </button>
                                    </c:if>
                                    <%-- Si no fue (o el evento no ocurrio), en vez del boton se
                                         explica por que: cancelado, lista de espera, pendiente o
                                         rechazada. La clave sale del propio estado (PENDING ->
                                         auth.registrations.pastStatus.PENDING). --%>
                                    <c:if test="${not empty entry.notAttendedReason}">
                                        <span class="pill"><spring:message code="auth.registrations.pastStatus.${entry.notAttendedReason}"/></span>
                                    </c:if>
                                </paw:eventRow>

                                <c:if test="${not empty myReview}">
                                    <div class="review-panel">
                                        <span class="review-display-heading"><spring:message code="auth.registrations.myReviewHeading"/></span>
                                        <paw:stars rating="${myReview.rating}"/>
                                        <c:if test="${not empty myReview.comment}">
                                            <p class="review-comment"><c:out value="${myReview.comment}"/></p>
                                        </c:if>
                                    </div>
                                </c:if>

                                <c:if test="${entry.canReview}">
                                    <div class="modal-overlay" id="reviewModal-${event.id}" hidden>
                                        <div class="modal-box">
                                            <div class="modal-header">
                                                <h2><spring:message code="auth.registrations.reviewHeading"/></h2>
                                                <button type="button" class="modal-close" data-close-modal aria-label="${closeButtonLabel}">
                                                    <paw:icon name="close"/>
                                                </button>
                                            </div>
                                            <form class="review-form" action="<c:url value='/perfil/inscripciones/${event.id}/resena'/>" method="post" novalidate="novalidate">
                                                <paw:starPicker fieldId="review-${event.id}"/>
                                                <div class="form-group">
                                                    <label for="comment-${event.id}"><spring:message code="auth.registrations.commentLabel"/></label>
                                                    <textarea id="comment-${event.id}" name="comment" maxlength="100"></textarea>
                                                </div>
                                                <paw:button text="${reviewButtonLabel}" cssClass="btn-accent"/>
                                            </form>
                                        </div>
                                    </div>
                                </c:if>
                            </div>
                        </c:forEach>
                    </div>
                    <paw:pager baseUrl="/perfil/inscripciones" pageParam="finalizadasPage"
                               currentPage="${pastRegistrations.page}" totalPages="${pastRegistrations.totalPages}"
                               otherParam="proximasPage" otherPage="${upcomingRegistrations.page}"/>
                </c:if>
            </c:otherwise>
        </c:choose>
    </main>
</div>
</jsp:body>
</paw:layout>
