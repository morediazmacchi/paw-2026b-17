<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout title="${event.title}">
<jsp:attribute name="head">
    <c:if test="${event.locationPinned}">
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
              integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin=""/>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
                integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
    </c:if>
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
    <script src="<c:url value='/js/event-gallery.js'/>"></script>
    <script src="<c:url value='/js/favorite-toggle.js'/>"></script>
    <script src="<c:url value='/js/modal.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar/>

<spring:message code="event.detail.backButton" var="backLabel"/>
<section class="detail-hero">
    <c:if test="${not empty event.imageId}">
        <c:choose>
            <c:when test="${fn:length(event.imageIds) > 1}">
                <a class="detail-hero-photo" href="<c:url value='/image/${event.imageId}'/>"
                   data-open-modal="eventGalleryModal" data-gallery-index="0">
                    <img class="detail-hero-img" src="<c:url value='/image/${event.imageId}'/>" alt=""/>
                </a>
            </c:when>
            <c:otherwise>
                <img class="detail-hero-img" src="<c:url value='/image/${event.imageId}'/>" alt=""/>
            </c:otherwise>
        </c:choose>
    </c:if>
    <div class="detail-hero-overlay"></div>
    <a class="detail-hero-back" href="<c:url value='/eventos'/>" data-history-back><c:out value="${backLabel}"/></a>
    <div class="detail-hero-top-actions">
        <c:forEach var="ec" items="${event.categories}">
            <spring:message code="category.${ec.categoryName}" text="${ec.categoryName}" var="eventCategoria"/>
            <span class="detail-hero-badge"><c:out value="${eventCategoria}"/></span>
        </c:forEach>
        <%-- No "event.private": "private" es palabra reservada de Java y el
             parser de EL la rechaza como nombre de propiedad, aunque el
             getter se llame isPrivate(). Se compara el enum directo. --%>
        <c:if test="${event.visibility == 'PRIVATE'}">
            <span class="detail-hero-badge"><spring:message code="event.publish.privateLabel"/></span>
        </c:if>
        <c:if test="${event.cancelled}">
            <span class="detail-hero-badge detail-hero-badge-cancelled">
                <c:choose>
                    <%-- Mismo mecanismo que una cancelacion comun (ver Event.removedByAdmin),
                         pero el texto tiene que decir que fue un admin, no el organizador. --%>
                    <c:when test="${event.removedByAdmin}"><spring:message code="auth.events.removedByAdminTag"/></c:when>
                    <c:otherwise><spring:message code="auth.events.cancelledTag"/></c:otherwise>
                </c:choose>
            </span>
        </c:if>
        <sec:authorize access="isAuthenticated()">
            <%-- Sin <c:url>: returnTo es para el controller, no un href -- ver
                 mismo comentario en auth/favoritos.jsp. --%>
            <c:set var="detailUrl" value="/eventos/${event.id}"/>
            <c:url value="/eventos/${event.id}/favorito" var="favAddUrl"/>
            <c:url value="/eventos/${event.id}/favorito/quitar" var="favRemoveUrl"/>
            <spring:message code="event.favorite.add" var="favAddLabel"/>
            <spring:message code="event.favorite.remove" var="favRemoveLabel"/>
            <spring:message code="event.favorite.error" var="favErrorLabel"/>
            <form class="js-favorite-toggle" action="${favorite ? favRemoveUrl : favAddUrl}" method="post"
                  data-add-url="${favAddUrl}" data-remove-url="${favRemoveUrl}"
                  data-add-label="${favAddLabel}" data-remove-label="${favRemoveLabel}"
                  data-error-label="${favErrorLabel}">
                <input type="hidden" name="returnTo" value="${detailUrl}"/>
                <button type="submit" class="card-favorite-btn detail-hero-favorite-btn${favorite ? ' card-favorite-btn-active' : ''}"
                        aria-label="${favorite ? favRemoveLabel : favAddLabel}">
                    <span class="favorite-icon-outline"${favorite ? ' hidden' : ''}><paw:icon name="heart"/></span>
                    <span class="favorite-icon-filled"${favorite ? '' : ' hidden'}><paw:icon name="heart-filled"/></span>
                </button>
            </form>
        </sec:authorize>
        <%-- Banner de /eventos (ver EventController.eventos()): un admin elige
             que eventos van ahi. La accion vive bajo /admin/** (ver
             AdminController), que ya exige ROLE_ADMIN. --%>
        <sec:authorize access="hasRole('ADMIN')">
            <c:choose>
                <c:when test="${event.featured}">
                    <spring:message code="admin.events.unfeatureButton" var="unfeatureLabel"/>
                    <form action="<c:url value='/admin/eventos/${event.id}/destacar/quitar'/>" method="post">
                        <button type="submit" class="detail-hero-badge detail-hero-badge-accent detail-hero-feature-btn">
                            <c:out value="${unfeatureLabel}"/>
                        </button>
                    </form>
                </c:when>
                <c:otherwise>
                    <spring:message code="admin.events.featureButton" var="featureLabel"/>
                    <form action="<c:url value='/admin/eventos/${event.id}/destacar'/>" method="post">
                        <button type="submit" class="detail-hero-badge detail-hero-feature-btn">
                            <c:out value="${featureLabel}"/>
                        </button>
                    </form>
                </c:otherwise>
            </c:choose>
        </sec:authorize>
    </div>
    <h1 class="detail-hero-title"><c:out value="${event.title}"/></h1>
</section>

<%-- Galeria: solo si hay mas de una foto (la primera ya se ve en el hero de
     arriba). Tocar la principal o cualquier miniatura abre el carrusel de mas
     abajo con todas las fotos (event-gallery.js); sin JS cada una sigue
     siendo un link a la foto entera. --%>
<c:if test="${fn:length(event.imageIds) > 1}">
    <section class="detail-gallery">
        <c:forEach var="imgId" items="${event.imageIds}" varStatus="loop">
            <a class="detail-gallery-link" href="<c:url value='/image/${imgId}'/>"
               data-open-modal="eventGalleryModal" data-gallery-index="${loop.index}">
                <img class="detail-gallery-thumb" src="<c:url value='/image/${imgId}'/>" alt=""/>
            </a>
        </c:forEach>
    </section>

    <spring:message code="event.detail.closeButton" var="galleryCloseLabel"/>
    <spring:message code="event.detail.prevPhoto" var="galleryPrevLabel"/>
    <spring:message code="event.detail.nextPhoto" var="galleryNextLabel"/>
    <div class="modal-overlay gallery-modal" id="eventGalleryModal" hidden>
        <div class="modal-box gallery-box">
            <div class="modal-header">
                <span class="gallery-counter" data-gallery-counter></span>
                <button type="button" class="modal-close" data-close-modal aria-label="${galleryCloseLabel}">
                    <paw:icon name="close"/>
                </button>
            </div>
            <div class="gallery-viewer">
                <button type="button" class="calendar-nav gallery-nav" data-gallery-prev aria-label="${galleryPrevLabel}">
                    <paw:icon name="chevron-left"/>
                </button>
                <img class="gallery-photo" data-gallery-photo alt=""/>
                <button type="button" class="calendar-nav gallery-nav" data-gallery-next aria-label="${galleryNextLabel}">
                    <paw:icon name="chevron-right"/>
                </button>
            </div>
        </div>
    </div>
</c:if>

<main class="page detail-page">
    <c:if test="${not empty rsvp}">
        <c:choose>
            <c:when test="${rsvp == 'confirmed'}">
                <p class="alert alert-success detail-alert"><spring:message code="event.rsvp.confirmed"/></p>
            </c:when>
            <c:when test="${rsvp == 'ya-anotado'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.alreadyRegistered"/></p>
            </c:when>
            <c:when test="${rsvp == 'waitlist'}">
                <p class="alert alert-success detail-alert"><spring:message code="event.rsvp.waitlisted"/></p>
            </c:when>
            <c:when test="${rsvp == 'ya-en-waitlist'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.alreadyWaitlisted"/></p>
            </c:when>
            <c:when test="${rsvp == 'baja'}">
                <p class="alert alert-success detail-alert"><spring:message code="event.rsvp.unregistered"/></p>
            </c:when>
            <c:when test="${rsvp == 'baja-waitlist'}">
                <p class="alert alert-success detail-alert"><spring:message code="event.rsvp.leftWaitlist"/></p>
            </c:when>
            <c:when test="${rsvp == 'pendiente'}">
                <p class="alert alert-success detail-alert"><spring:message code="event.rsvp.pending"/></p>
            </c:when>
            <c:when test="${rsvp == 'ya-pendiente'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.alreadyPending"/></p>
            </c:when>
            <c:when test="${rsvp == 'rechazado'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.rejected"/></p>
            </c:when>
            <c:when test="${rsvp == 'baja-pendiente'}">
                <p class="alert alert-success detail-alert"><spring:message code="event.rsvp.leftPending"/></p>
            </c:when>
            <c:when test="${rsvp == 'finalizado'}">
                <p class="alert detail-alert"><spring:message code="event.detail.eventFinished"/></p>
            </c:when>
            <c:when test="${rsvp == 'propio-evento'}">
                <p class="alert detail-alert"><spring:message code="event.detail.ownEventNote"/></p>
            </c:when>
            <c:when test="${rsvp == 'cancelado'}">
                <p class="alert detail-alert"><spring:message code="event.detail.eventCancelled"/></p>
            </c:when>
        </c:choose>
    </c:if>

    <%-- Resultado de reportar el evento (ver EventController.reportar()): mismo
         mecanismo POST-Redirect-GET que "rsvp"/"questionMessage" de aca arriba. --%>
    <c:if test="${not empty reportMessage}">
        <div class="alert<c:if test="${reportMessage == 'CREATED'}"> alert-success</c:if> detail-alert">
            <spring:message code="event.detail.reportFlash.${reportMessage}"/>
            <%-- Si quien reporta tambien esta anotado (confirmado, en lista de espera o pendiente),
                 se le ofrece darse de baja ahi mismo, en la misma linea: es el mismo POST que el
                 boton de la barra lateral. --%>
            <c:if test="${(reportMessage == 'CREATED' or reportMessage == 'ALREADY_REPORTED') and registered and not event.cancelled and not event.finished}">
                <c:choose>
                    <c:when test="${waitlisted}"><spring:message code="event.detail.leaveWaitlistButton" var="reportUnregisterLabel"/></c:when>
                    <c:when test="${pendingApproval}"><spring:message code="event.detail.pendingApprovalButton" var="reportUnregisterLabel"/></c:when>
                    <c:otherwise><spring:message code="event.detail.unregisterButton" var="reportUnregisterLabel"/></c:otherwise>
                </c:choose>
                <form class="report-unregister" action="<c:url value='/eventos/${event.id}/darme-de-baja'/>" method="post" novalidate="novalidate">
                    <button type="submit" class="btn-link report-unregister-link"><c:out value="${reportUnregisterLabel}"/></button>.
                </form>
            </c:if>
        </div>
    </c:if>

    <div class="detail-meta-strip">
        <div class="detail-meta-item">
            <span class="detail-meta-icon"><paw:icon name="calendar"/></span>
            <div>
                <span class="detail-meta-label"><spring:message code="event.detail.dateLabelSmall"/></span>
                <span class="detail-meta-value"><fmt:formatDate value="${event.eventDate}" pattern="EEE dd MMM yyyy"/></span>
            </div>
        </div>
        <div class="detail-meta-item">
            <span class="detail-meta-icon"><paw:icon name="clock"/></span>
            <div>
                <span class="detail-meta-label"><spring:message code="event.detail.timeLabelSmall"/></span>
                <span class="detail-meta-value"><fmt:formatDate value="${event.eventDate}" pattern="HH:mm"/>hs</span>
            </div>
        </div>
        <div class="detail-meta-item">
            <span class="detail-meta-icon"><paw:icon name="tag"/></span>
            <div>
                <span class="detail-meta-label"><spring:message code="event.detail.priceLabelSmall"/></span>
                <span class="detail-meta-value">
                    <c:choose>
                        <c:when test="${event.free}"><spring:message code="event.price.free"/></c:when>
                        <c:otherwise>$<fmt:formatNumber value="${event.price}" type="number" maxFractionDigits="0"/></c:otherwise>
                    </c:choose>
                </span>
            </div>
        </div>
        <div class="detail-meta-item">
            <span class="detail-meta-icon"><paw:icon name="people"/></span>
            <div>
                <span class="detail-meta-label"><spring:message code="event.detail.capacityLabelSmall"/></span>
                <span class="detail-meta-value">
                    <c:choose>
                        <c:when test="${not empty event.maxCapacity}"><spring:message code="event.detail.capacityValue" arguments="${event.maxCapacity}"/></c:when>
                        <c:otherwise><spring:message code="event.detail.capacityUnlimited"/></c:otherwise>
                    </c:choose>
                </span>
            </div>
        </div>
        <div class="detail-meta-item">
            <span class="detail-meta-icon"><paw:icon name="car"/></span>
            <div>
                <span class="detail-meta-label"><spring:message code="event.detail.parkingLabelSmall"/></span>
                <span class="detail-meta-value">
                    <c:choose>
                        <c:when test="${event.parking}"><spring:message code="common.yes"/></c:when>
                        <c:otherwise><spring:message code="common.no"/></c:otherwise>
                    </c:choose>
                </span>
            </div>
        </div>
    </div>

    <div class="detail-grid">
        <div class="detail-main">
            <c:if test="${not empty event.description}">
                <h2 class="detail-section-heading"><spring:message code="event.detail.aboutHeading"/></h2>
                <p class="detail-descripcion"><c:out value="${event.description}"/></p>
            </c:if>

            <c:forEach var="ec" items="${event.categories}">
                <c:if test="${not empty ec.extraInfo}">
                    <spring:message code="category.${ec.categoryName}" text="${ec.categoryName}" var="ecCategoria"/>
                    <h2 class="detail-section-heading"><c:out value="${ecCategoria}"/></h2>
                    <p class="detail-descripcion"><c:out value="${ec.extraInfo}"/></p>
                </c:if>
            </c:forEach>

            <h2 class="detail-section-heading"><spring:message code="event.detail.addressHeading"/></h2>
            <div class="detail-address">
                <span class="detail-address-icon"><paw:icon name="pin"/></span>
                <div>
                    <p class="detail-address-line"><c:out value="${event.address}"/></p>
                    <%-- Piso/depto/referencias (ver EventForm.addressDetails): texto libre,
                         nunca geocodificado, solo se muestra aca junto a la direccion. --%>
                    <c:if test="${not empty event.addressDetails}">
                        <p class="detail-address-sub"><c:out value="${event.addressDetails}"/></p>
                    </c:if>
                    <c:if test="${not empty event.neighborhood}">
                        <p class="detail-address-sub"><c:out value="${event.neighborhood.displayName}"/>, Buenos Aires</p>
                    </c:if>
                </div>
            </div>
            <%-- Mapa de solo lectura: solo si el creador marco un pin al publicar/
                 editar (event.locationPinned). Si no, la direccion en texto de
                 arriba (siempre presente) es lo unico que se muestra, igual que
                 antes de esta feature. --%>
            <c:if test="${event.locationPinned}">
                <div id="detail-map" class="detail-map"
                     data-lat="${event.latitude}" data-lon="${event.longitude}"></div>
                <script src="<c:url value='/js/event-detail-map.js'/>"></script>
                <%-- No tiene que destacar, pero tiene que estar: avisa si el pin lo
                     geocodifico Nominatim o lo puso el organizador a mano (ver
                     Event.locationManual/event-location-map.js), porque un pin a
                     mano puede no ser exacto. --%>
                <p class="detail-map-note">
                    <c:choose>
                        <c:when test="${event.locationManual}">
                            <spring:message code="event.detail.mapNoteManual"/>
                        </c:when>
                        <c:otherwise>
                            <spring:message code="event.detail.mapNoteGeo"/>
                        </c:otherwise>
                    </c:choose>
                </p>
            </c:if>

            <h2 class="detail-section-heading"><spring:message code="event.detail.organizerHeading"/></h2>
            <c:choose>
                <c:when test="${creatorReviewCount > 0}">
                    <div class="creator-rating">
                        <paw:stars rating="${creatorAverageRating}"/>
                        <span class="creator-rating-count">
                            <fmt:formatNumber value="${creatorAverageRating}" maxFractionDigits="1"/>
                            (<spring:message code="review.countLabel" arguments="${creatorReviewCount}"/>)
                        </span>
                    </div>
                    <button type="button" class="btn-link" data-open-modal="creatorReviewsModal">
                        <spring:message code="event.detail.viewCreatorReviews"/>
                    </button>
                </c:when>
                <c:otherwise>
                    <p class="creator-rating-empty"><spring:message code="event.detail.noCreatorReviews"/></p>
                </c:otherwise>
            </c:choose>

            <h2 class="detail-section-heading" id="preguntas"><spring:message code="event.questions.heading"/></h2>
            <c:if test="${not empty questionMessage}">
                <p class="alert<c:if test="${questionMessage == 'CREATED' or questionMessage == 'ANSWERED'}"> alert-success</c:if>"><spring:message code="event.questions.flash.${questionMessage}"/></p>
            </c:if>

            <%-- Preguntar: logueado, no organizador, evento abierto. Anonimo: link al login
                 que vuelve aca. Cerrado: solo el aviso. --%>
            <c:choose>
                <c:when test="${not questionsOpen}">
                    <p class="creator-rating-empty"><spring:message code="event.questions.closed"/></p>
                </c:when>
                <c:when test="${isOwner}"></c:when>
                <c:otherwise>
                    <sec:authorize access="isAuthenticated()">
                        <form action="<c:url value='/eventos/${event.id}/preguntas'/>" method="post" novalidate="novalidate">
                            <div class="form-group">
                                <label for="question-text"><spring:message code="event.questions.askLabel"/></label>
                                <textarea id="question-text" name="text" maxlength="300"></textarea>
                            </div>
                            <spring:message code="event.questions.askButton" var="askButtonLabel"/>
                            <paw:button text="${askButtonLabel}" cssClass="btn-accent"/>
                        </form>
                    </sec:authorize>
                    <sec:authorize access="isAnonymous()">
                        <c:set var="questionsReturnPath" value="/eventos/${event.id}"/>
                        <c:url value="/login" var="loginToAskUrl">
                            <c:param name="redirect" value="${questionsReturnPath}"/>
                        </c:url>
                        <p><a class="btn-link" href="${loginToAskUrl}"><spring:message code="event.questions.loginToAsk"/></a></p>
                    </sec:authorize>
                </c:otherwise>
            </c:choose>

            <c:choose>
                <c:when test="${empty questions}">
                    <p class="creator-rating-empty"><spring:message code="event.questions.empty"/></p>
                </c:when>
                <c:otherwise>
                    <c:forEach var="question" items="${questions}">
                        <div class="creator-review-item">
                            <div class="creator-review-event">
                                <span class="creator-review-event-title">
                                    <c:choose>
                                        <c:when test="${not empty question.askerName}"><c:out value="${question.askerName}"/></c:when>
                                        <c:otherwise><spring:message code="event.questions.unnamedAsker"/></c:otherwise>
                                    </c:choose>
                                </span>
                                <span class="creator-review-date"><fmt:formatDate value="${question.createdAt}" pattern="dd/MM/yyyy"/></span>
                            </div>
                            <p class="review-comment"><c:out value="${question.text}"/></p>
                            <c:choose>
                                <c:when test="${question.answered}">
                                    <p class="review-comment question-answer">
                                        <strong><spring:message code="event.questions.answerPrefix"/></strong>
                                        <c:out value="${question.answer}"/>
                                    </p>
                                </c:when>
                                <c:when test="${isOwner}">
                                    <form class="question-answer-form"
                                          action="<c:url value='/eventos/${event.id}/preguntas/${question.id}/responder'/>"
                                          method="post" novalidate="novalidate">
                                        <div class="form-group">
                                            <label for="answer-${question.id}"><spring:message code="event.questions.answerLabel"/></label>
                                            <textarea id="answer-${question.id}" name="text" maxlength="300"></textarea>
                                        </div>
                                        <spring:message code="event.questions.answerButton" var="answerButtonLabel"/>
                                        <paw:button text="${answerButtonLabel}" cssClass="btn-accent"/>
                                    </form>
                                </c:when>
                                <c:otherwise>
                                    <p class="creator-rating-empty"><spring:message code="event.questions.unanswered"/></p>
                                </c:otherwise>
                            </c:choose>
                            <%-- Moderacion: borra la pregunta (y su respuesta, si tenia).
                                 La accion vive bajo /admin/** (ver AdminController), que
                                 ya exige ROLE_ADMIN. --%>
                            <sec:authorize access="hasRole('ADMIN')">
                                <spring:message code="admin.questions.deleteButton" var="deleteQuestionLabel"/>
                                <button type="button" class="btn-link" data-open-modal="deleteQuestionModal-${question.id}">
                                    <c:out value="${deleteQuestionLabel}"/>
                                </button>
                            </sec:authorize>
                        </div>
                    </c:forEach>

                    <sec:authorize access="hasRole('ADMIN')">
                        <spring:message code="event.detail.closeButton" var="deleteQuestionCloseLabel"/>
                        <spring:message code="admin.questions.deleteCancelButton" var="deleteQuestionCancelLabel"/>
                        <spring:message code="admin.questions.deleteConfirmButton" var="deleteQuestionConfirmLabel"/>
                        <c:forEach var="question" items="${questions}">
                            <div class="modal-overlay" id="deleteQuestionModal-${question.id}" hidden>
                                <div class="modal-box">
                                    <div class="modal-header">
                                        <h2><spring:message code="admin.questions.deleteModalTitle"/></h2>
                                        <button type="button" class="modal-close" data-close-modal aria-label="${deleteQuestionCloseLabel}">
                                            <paw:icon name="close"/>
                                        </button>
                                    </div>
                                    <div class="modal-body">
                                        <p><c:out value="${question.text}"/></p>
                                        <p><spring:message code="admin.questions.deleteModalBody"/></p>
                                        <form action="<c:url value='/admin/preguntas/${question.id}/eliminar'/>" method="post">
                                            <input type="hidden" name="eventId" value="${event.id}"/>
                                            <div class="modal-confirm-actions">
                                                <button type="button" class="btn btn-medium" data-close-modal>
                                                    <c:out value="${deleteQuestionCancelLabel}"/>
                                                </button>
                                                <paw:button text="${deleteQuestionConfirmLabel}" cssClass="btn-danger"/>
                                            </div>
                                        </form>
                                    </div>
                                </div>
                            </div>
                        </c:forEach>
                    </sec:authorize>

                    <c:if test="${questionsTotalPages > 1}">
                        <nav class="pager">
                            <c:if test="${questionsPage > 1}">
                                <a class="pager-arrow" href="<c:url value='/eventos/${event.id}'><c:param name='preguntasPage' value='${questionsPage - 1}'/></c:url>#preguntas">
                                    <spring:message code="event.list.prevPage"/>
                                </a>
                            </c:if>
                            <span class="pager-number pager-number-active"><c:out value="${questionsPage}"/> / <c:out value="${questionsTotalPages}"/></span>
                            <c:if test="${questionsPage < questionsTotalPages}">
                                <a class="pager-arrow" href="<c:url value='/eventos/${event.id}'><c:param name='preguntasPage' value='${questionsPage + 1}'/></c:url>#preguntas">
                                    <spring:message code="event.list.nextPage"/>
                                </a>
                            </c:if>
                        </nav>
                    </c:if>
                </c:otherwise>
            </c:choose>
        </div>

        <aside class="detail-side">
            <div class="detail-price-card">
                <div class="detail-price-card-top">
                    <div>
                        <span class="detail-price-big">
                            <c:choose>
                                <c:when test="${event.free}"><spring:message code="event.price.free"/></c:when>
                                <c:otherwise>$<fmt:formatNumber value="${event.price}" type="number" maxFractionDigits="0"/></c:otherwise>
                            </c:choose>
                        </span>
                        <span class="detail-price-sub"><spring:message code="event.detail.perPerson"/></span>
                    </div>
                    <c:if test="${not empty availableSpots}">
                        <span class="detail-spots-badge">
                            <c:out value="${availableSpots}"/>
                            <span class="detail-spots-badge-label"><spring:message code="event.detail.spotsBadge"/></span>
                        </span>
                    </c:if>
                </div>
                <div class="detail-price-card-bottom">
                    <span><paw:icon name="calendar"/> <fmt:formatDate value="${event.eventDate}" pattern="EEE dd MMM yyyy"/></span>
                    <span><paw:icon name="clock"/> <fmt:formatDate value="${event.eventDate}" pattern="HH:mm"/>hs</span>
                </div>
            </div>

            <div class="detail-rsvp-card">
                <h2 class="detail-rsvp-heading"><spring:message code="event.detail.registerCardHeading"/></h2>
                <c:choose>
                    <%-- Sos el organizador: gana sobre todo lo demas (cancelado,
                         finalizado, lleno, etc.), ninguna de esas ramas te aplica
                         a vos -- nunca te vas a poder anotar a tu propio evento
                         (ver RegistrationServiceImpl.register()), asi que no tiene
                         sentido ofrecerte ningun boton relacionado. --%>
                    <c:when test="${isOwner}">
                        <%-- Revision previa a publicarse (ver EventApprovalStatus): no es
                             lo mismo que event.cancelled/removedByAdmin, por eso va
                             primero aca y no en esas ramas de abajo (esas son para
                             terceros, a quienes nunca les llega a esta rama porque
                             isOwner gana sobre todo). --%>
                        <c:choose>
                            <c:when test="${event.pendingApproval}">
                                <p class="alert"><spring:message code="event.detail.eventPendingApproval"/></p>
                            </c:when>
                            <c:when test="${event.rejected}">
                                <p class="alert">
                                    <spring:message code="event.detail.eventRejectedByAdmin"/>
                                    <c:if test="${not empty event.rejectedReason}">
                                        <br/>
                                        <spring:message code="event.detail.eventRejectedByAdminReason" arguments="${event.rejectedReason}" htmlEscape="true"/>
                                    </c:if>
                                </p>
                            </c:when>
                            <c:otherwise>
                                <p class="alert"><spring:message code="event.detail.ownEventNote"/></p>
                            </c:otherwise>
                        </c:choose>
                        <%-- Cancelado o rechazado no se edita (mismo criterio que "Mis
                             eventos": rechazado es definitivo, no hay vuelta atras). --%>
                        <c:if test="${!event.cancelled && !event.rejected}">
                            <spring:message code="auth.events.editButton" var="editButtonLabel"/>
                            <a class="btn btn-medium btn-accent detail-rsvp-submit" href="<c:url value='/eventos/${event.id}/editar'/>">
                                <c:out value="${editButtonLabel}"/>
                            </a>
                        </c:if>
                    </c:when>
                    <%-- Admin mirando un evento privado al que no fue invitado: puede verlo
                         (moderacion) pero no anotarse, asi que no se le ofrece el boton. --%>
                    <c:when test="${viewOnly}">
                        <p class="alert"><spring:message code="event.detail.adminViewOnlyNote"/></p>
                    </c:when>
                    <%-- Cancelado gana sobre todo lo demas: ni siquiera importa si
                         estaba finalizado o si alguien seguia anotado, no hay ninguna
                         accion valida que ofrecer (mismo lugar donde se esconde el
                         form de "Quiero ir" cuando el evento esta lleno). --%>
                    <c:when test="${event.cancelled}">
                        <p class="alert">
                            <c:choose>
                                <c:when test="${event.removedByAdmin}"><spring:message code="event.detail.eventRemovedByAdmin"/></c:when>
                                <c:otherwise><spring:message code="event.detail.eventCancelled"/></c:otherwise>
                            </c:choose>
                        </p>
                    </c:when>
                    <%-- Finalizado gana incluso si estabas anotado: ya paso, no tiene
                         sentido "darse de baja" de algo que ya sucedio. Que este lleno,
                         en cambio, ya no es un callejon sin salida: se avisa con el
                         cartel y el mismo boton pasa a ser el de la lista de espera. --%>
                    <c:when test="${finished}">
                        <p class="alert"><spring:message code="event.detail.eventFinished"/></p>
                    </c:when>
                    <%-- Rechazado es terminal: no hay boton que ofrecer, ni "darse de
                         baja" (no hay nada activo que cancelar) ni "inscribirme" de
                         nuevo (el service lo vuelve a rechazar solo con REJECTED). --%>
                    <c:when test="${rejected}">
                        <p class="alert"><spring:message code="event.detail.rejectedNote"/></p>
                    </c:when>
                    <c:otherwise>
                        <%-- El cartel solo si todavia no tenes nada: quien ya esta
                             confirmado o ya esta en la lista no necesita que le avisen. --%>
                        <c:if test="${full and !registered}">
                            <p class="alert"><spring:message code="event.detail.eventFull"/></p>
                        </c:if>

                        <%-- Las etiquetas se resuelven una sola vez aca arriba: abajo hay
                             una rama para logueado y otra para anonimo, y no tiene sentido
                             repetir el mismo c:choose en las dos. --%>
                        <c:choose>
                            <c:when test="${full}">
                                <spring:message code="event.detail.waitlistButton" var="rsvpButtonLabel"/>
                            </c:when>
                            <c:otherwise>
                                <spring:message code="event.detail.rsvpButton" var="rsvpButtonLabel"/>
                            </c:otherwise>
                        </c:choose>
                        <c:choose>
                            <c:when test="${waitlisted}">
                                <spring:message code="event.detail.leaveWaitlistButton" var="unregisterButtonLabel"/>
                            </c:when>
                            <c:when test="${pendingApproval}">
                                <spring:message code="event.detail.pendingApprovalButton" var="unregisterButtonLabel"/>
                            </c:when>
                            <c:otherwise>
                                <spring:message code="event.detail.unregisterButton" var="unregisterButtonLabel"/>
                            </c:otherwise>
                        </c:choose>
                        <sec:authorize access="isAuthenticated()">
                            <c:choose>
                                <c:when test="${registered}">
                                    <form action="<c:url value='/eventos/${event.id}/darme-de-baja'/>" method="post" novalidate="novalidate">
                                        <paw:button text="${unregisterButtonLabel}" cssClass="btn-danger detail-rsvp-submit" />
                                    </form>
                                </c:when>
                                <c:otherwise>
                                    <form action="<c:url value='/eventos/${event.id}/quiero-ir'/>" method="post" novalidate="novalidate">
                                        <paw:button text="${rsvpButtonLabel}" cssClass="btn-accent detail-rsvp-submit" />
                                    </form>
                                </c:otherwise>
                            </c:choose>
                        </sec:authorize>
                        <sec:authorize access="isAnonymous()">
                            <%-- No se manda directo a /eventos/{id}/quiero-ir: eso es un POST
                                 protegido, y volver de un POST despues del login no es trivial.
                                 Mas simple y mas explicito: un link a /login que vuelve al
                                 detalle (WebAuthConfig.loginSuccessHandler), y aca aprieta
                                 "Inscribirme" de nuevo, ya logueado. --%>
                            <%-- OJO: a proposito NO es <c:url value="/eventos/${event.id}"/>: eso
                                 agregaria el context path, y el <c:param> de abajo lo agregaria
                                 DE NUEVO al armar /login (loginSuccessHandler hace
                                 contextPath + redirect). Un string EL crudo evita duplicarlo. --%>
                            <c:set var="eventDetailPath" value="/eventos/${event.id}"/>
                            <c:url value="/login" var="loginWithRedirectUrl">
                                <c:param name="redirect" value="${eventDetailPath}"/>
                            </c:url>
                            <a class="btn btn-medium btn-accent detail-rsvp-submit" href="${loginWithRedirectUrl}">
                                <c:out value="${rsvpButtonLabel}"/>
                            </a>
                        </sec:authorize>
                        <c:if test="${not registered}">
                            <p class="detail-rsvp-note">
                                <c:choose>
                                    <c:when test="${full}"><spring:message code="event.detail.waitlistNote"/></c:when>
                                    <c:when test="${event.requiresApproval}"><spring:message code="event.detail.pendingApprovalNote"/></c:when>
                                    <c:otherwise><spring:message code="event.detail.reserveNote"/></c:otherwise>
                                </c:choose>
                            </p>
                        </c:if>
                        <c:if test="${registered and pendingApproval}">
                            <p class="detail-rsvp-note"><spring:message code="event.detail.pendingApprovalNote"/></p>
                        </c:if>
                    </c:otherwise>
                </c:choose>

                <%-- Grupo de chat: solo para quien tiene el lugar CONFIRMADO (el organizador no
                     se anota a su evento, asi que nunca lo ve). Quien ya es miembro ve el acceso
                     aunque el evento haya terminado o se haya cancelado: el chat queda de solo
                     lectura. Unirse, en cambio, solo mientras el evento sigue vigente. --%>
                <c:if test="${chatMember or (chatConfirmed and not chatClosed)}">
                    <div class="detail-chat">
                        <c:choose>
                            <c:when test="${chatMember}">
                                <a class="btn btn-medium detail-rsvp-submit" href="<c:url value='/eventos/${event.id}/chat'/>">
                                    <spring:message code="chat.open"/>
                                </a>
                            </c:when>
                            <c:otherwise>
                                <form action="<c:url value='/eventos/${event.id}/chat/unirse'/>" method="post" novalidate="novalidate">
                                    <spring:message code="chat.join" var="chatJoinLabel"/>
                                    <paw:button text="${chatJoinLabel}" cssClass="detail-rsvp-submit" />
                                </form>
                                <p class="detail-rsvp-note"><spring:message code="chat.joinNote"/></p>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </c:if>
                <c:if test="${not empty chatMessage}">
                    <p class="alert detail-alert"><spring:message code="chat.message.${chatMessage}"/></p>
                </c:if>
            </div>

            <%-- Reportar: cualquiera logueado que no sea el organizador (reportar tu
                 propio evento no tiene sentido, ademas el service lo rechaza igual).
                 No hace falta esconderlo si ya esta cancelado/removido: reportar
                 igual puede servir para avisar de algo del organizador. --%>
            <c:if test="${not isOwner}">
                <sec:authorize access="isAuthenticated()">
                    <button type="button" class="btn-link detail-report-link" data-open-modal="reportModal">
                        <spring:message code="event.detail.reportButton"/>
                    </button>
                </sec:authorize>
            </c:if>
        </aside>
    </div>
</main>

<c:if test="${not isOwner}">
    <sec:authorize access="isAuthenticated()">
        <spring:message code="event.detail.closeButton" var="reportCloseLabel"/>
        <div class="modal-overlay" id="reportModal" hidden>
            <div class="modal-box">
                <div class="modal-header">
                    <h2><spring:message code="event.detail.reportModalTitle"/></h2>
                    <button type="button" class="modal-close" data-close-modal aria-label="${reportCloseLabel}">
                        <paw:icon name="close"/>
                    </button>
                </div>
                <div class="modal-body">
                    <form action="<c:url value='/eventos/${event.id}/reportar'/>" method="post" novalidate="novalidate">
                        <div class="form-group">
                            <label for="report-reason"><spring:message code="event.detail.reportReasonLabel"/></label>
                            <textarea id="report-reason" name="motivo" maxlength="500" required="required"></textarea>
                        </div>
                        <spring:message code="event.detail.reportSubmitButton" var="reportSubmitLabel"/>
                        <paw:button text="${reportSubmitLabel}" cssClass="btn-danger"/>
                    </form>
                </div>
            </div>
        </div>
    </sec:authorize>
</c:if>

<c:if test="${creatorReviewCount > 0}">
    <div class="modal-overlay" id="creatorReviewsModal" hidden>
        <div class="modal-box">
            <div class="modal-header">
                <h2><spring:message code="event.detail.creatorReviewsHeading"/></h2>
                <spring:message code="event.detail.closeButton" var="closeButtonLabel"/>
                <button type="button" class="modal-close" data-close-modal aria-label="${closeButtonLabel}">
                    <paw:icon name="close"/>
                </button>
            </div>
            <div class="modal-body">
                <c:forEach var="review" items="${creatorReviews}">
                    <c:set var="reviewedEvent" value="${reviewEventsById[review.eventId]}"/>
                    <div class="creator-review-item">
                        <c:if test="${not empty reviewedEvent}">
                            <div class="creator-review-event">
                                <span class="creator-review-event-title"><c:out value="${reviewedEvent.title}"/></span>
                                <span class="creator-review-date"><fmt:formatDate value="${reviewedEvent.eventDate}" pattern="dd/MM/yyyy"/></span>
                            </div>
                        </c:if>
                        <paw:stars rating="${review.rating}"/>
                        <c:if test="${not empty review.comment}">
                            <p class="review-comment"><c:out value="${review.comment}"/></p>
                        </c:if>
                    </div>
                </c:forEach>
            </div>
        </div>
    </div>
</c:if>
</jsp:body>
</paw:layout>
