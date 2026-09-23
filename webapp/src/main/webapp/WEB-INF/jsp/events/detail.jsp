<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <c:out value="${event.title}"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <c:if test="${event.locationPinned}">
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
              integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin=""/>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
                integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
    </c:if>
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
    <script src="<c:url value='/js/favorite-toggle.js'/>"></script>
    <script src="<c:url value='/js/modal.js'/>"></script>
</head>
<body>

<paw:navbar/>

<spring:message code="event.detail.backButton" var="backLabel"/>
<section class="detail-hero">
    <c:if test="${not empty event.imageId}">
        <img class="detail-hero-img" src="<c:url value='/image/${event.imageId}'/>" alt=""/>
    </c:if>
    <div class="detail-hero-overlay"></div>
    <a class="detail-hero-back" href="<c:url value='/eventos'/>"><c:out value="${backLabel}"/></a>
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
            <span class="detail-hero-badge detail-hero-badge-cancelled"><spring:message code="auth.events.cancelledTag"/></span>
        </c:if>
        <sec:authorize access="isAuthenticated()">
            <c:url value="/eventos/${event.id}" var="detailUrl"/>
            <c:url value="/eventos/${event.id}/favorito" var="favAddUrl"/>
            <c:url value="/eventos/${event.id}/favorito/quitar" var="favRemoveUrl"/>
            <spring:message code="event.favorite.add" var="favAddLabel"/>
            <spring:message code="event.favorite.remove" var="favRemoveLabel"/>
            <form class="js-favorite-toggle" action="${favorite ? favRemoveUrl : favAddUrl}" method="post"
                  data-add-url="${favAddUrl}" data-remove-url="${favRemoveUrl}"
                  data-add-label="${favAddLabel}" data-remove-label="${favRemoveLabel}">
                <input type="hidden" name="returnTo" value="${detailUrl}"/>
                <button type="submit" class="card-favorite-btn detail-hero-favorite-btn${favorite ? ' card-favorite-btn-active' : ''}"
                        aria-label="${favorite ? favRemoveLabel : favAddLabel}">
                    <span class="favorite-icon-outline"${favorite ? ' hidden' : ''}><paw:icon name="heart"/></span>
                    <span class="favorite-icon-filled"${favorite ? '' : ' hidden'}><paw:icon name="heart-filled"/></span>
                </button>
            </form>
        </sec:authorize>
    </div>
    <h1 class="detail-hero-title"><c:out value="${event.title}"/></h1>
</section>

<%-- Galeria: solo si hay mas de una foto (la primera ya se ve en el hero de
     arriba). Cada miniatura abre la foto entera en una pestaña nueva -- sin
     carrusel ni reordenamiento, alcanza para "verlas todas". --%>
<c:if test="${fn:length(event.imageIds) > 1}">
    <section class="detail-gallery">
        <c:forEach var="imgId" items="${event.imageIds}">
            <a href="<c:url value='/image/${imgId}'/>" target="_blank" rel="noopener">
                <img class="detail-gallery-thumb" src="<c:url value='/image/${imgId}'/>" alt=""/>
            </a>
        </c:forEach>
    </section>
</c:if>

<main class="page detail-page">
    <c:if test="${not empty rsvp}">
        <c:choose>
            <c:when test="${rsvp == 'confirmed'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.confirmed"/></p>
            </c:when>
            <c:when test="${rsvp == 'ya-anotado'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.alreadyRegistered"/></p>
            </c:when>
            <c:when test="${rsvp == 'waitlist'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.waitlisted"/></p>
            </c:when>
            <c:when test="${rsvp == 'ya-en-waitlist'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.alreadyWaitlisted"/></p>
            </c:when>
            <c:when test="${rsvp == 'lleno'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.full"/></p>
            </c:when>
            <c:when test="${rsvp == 'baja'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.unregistered"/></p>
            </c:when>
            <c:when test="${rsvp == 'baja-waitlist'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.leftWaitlist"/></p>
            </c:when>
            <c:when test="${rsvp == 'pendiente'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.pending"/></p>
            </c:when>
            <c:when test="${rsvp == 'ya-pendiente'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.alreadyPending"/></p>
            </c:when>
            <c:when test="${rsvp == 'rechazado'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.rejected"/></p>
            </c:when>
            <c:when test="${rsvp == 'baja-pendiente'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.leftPending"/></p>
            </c:when>
            <c:when test="${rsvp == 'finalizado'}">
                <p class="alert detail-alert"><spring:message code="event.detail.eventFinished"/></p>
            </c:when>
            <c:when test="${rsvp == 'propio-evento'}">
                <p class="alert detail-alert"><spring:message code="event.detail.ownEventNote"/></p>
            </c:when>
        </c:choose>
    </c:if>

    <div class="detail-meta-strip">
        <div class="detail-meta-item">
            <span class="detail-meta-icon"><paw:icon name="calendar"/></span>
            <div>
                <span class="detail-meta-label"><spring:message code="event.detail.dateLabelSmall"/></span>
                <span class="detail-meta-value"><fmt:formatDate value="${event.eventDate}" pattern="EEE dd MMM"/></span>
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
                        <c:when test="${event.price == 0}"><spring:message code="event.price.free"/></c:when>
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
        </div>

        <aside class="detail-side">
            <div class="detail-price-card">
                <div class="detail-price-card-top">
                    <div>
                        <span class="detail-price-big">
                            <c:choose>
                                <c:when test="${event.price == 0}"><spring:message code="event.price.free"/></c:when>
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
                    <span><paw:icon name="calendar"/> <fmt:formatDate value="${event.eventDate}" pattern="EEE dd MMM"/></span>
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
                        <p class="alert"><spring:message code="event.detail.ownEventNote"/></p>
                    </c:when>
                    <%-- Cancelado gana sobre todo lo demas: ni siquiera importa si
                         estaba finalizado o si alguien seguia anotado, no hay ninguna
                         accion valida que ofrecer (mismo lugar donde se esconde el
                         form de "Quiero ir" cuando el evento esta lleno). --%>
                    <c:when test="${event.cancelled}">
                        <p class="alert"><spring:message code="event.detail.eventCancelled"/></p>
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
                                        <%-- Si se llego con un link de invitacion, el token tiene que
                                             sobrevivir al POST: sin esto, alguien invitado a un evento
                                             privado podria ver el detalle pero no anotarse. --%>
                                        <c:if test="${not empty invitacion}">
                                            <input type="hidden" name="invitacion" value="${invitacion}"/>
                                        </c:if>
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
                                 contextPath + redirect). Un string EL crudo, como ya hacia esto
                                 antes de la invitacion, evita duplicarlo. El token de invitacion
                                 (base64 URL-safe) nunca necesita escape. --%>
                            <c:choose>
                                <c:when test="${not empty invitacion}">
                                    <c:set var="eventDetailPath" value="/eventos/${event.id}?invitacion=${invitacion}"/>
                                </c:when>
                                <c:otherwise>
                                    <c:set var="eventDetailPath" value="/eventos/${event.id}"/>
                                </c:otherwise>
                            </c:choose>
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
            </div>
        </aside>
    </div>
</main>

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

</body>
</html>
