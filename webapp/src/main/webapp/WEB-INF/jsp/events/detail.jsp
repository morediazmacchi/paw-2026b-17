<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <c:out value="${event.title}"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
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
        <c:if test="${not empty event.categoryName}">
            <spring:message code="category.${event.categoryName}" text="${event.categoryName}" var="eventCategoria"/>
            <span class="detail-hero-badge"><c:out value="${eventCategoria}"/></span>
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

<main class="page detail-page">
    <c:if test="${not empty rsvp}">
        <c:choose>
            <c:when test="${rsvp == 'confirmed'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.confirmed"/></p>
            </c:when>
            <c:when test="${rsvp == 'ya-anotado'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.alreadyRegistered"/></p>
            </c:when>
            <c:when test="${rsvp == 'lleno'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.full"/></p>
            </c:when>
            <c:when test="${rsvp == 'baja'}">
                <p class="alert detail-alert"><spring:message code="event.rsvp.unregistered"/></p>
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
            <%-- Mapa: pendiente, todavia no tenemos geocoding/coordenadas del evento. --%>

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
                    <%-- Lleno solo importa si todavia no tenes lugar: alguien ya
                         anotado se tiene que poder dar de baja igual. --%>
                    <c:when test="${full and !registered}">
                        <p class="alert"><spring:message code="event.detail.eventFull"/></p>
                    </c:when>
                    <c:otherwise>
                        <spring:message code="event.detail.rsvpButton" var="rsvpButtonLabel"/>
                        <spring:message code="event.detail.unregisterButton" var="unregisterButtonLabel"/>
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
                            <c:url value="/login" var="loginWithRedirectUrl">
                                <c:param name="redirect" value="/eventos/${event.id}"/>
                            </c:url>
                            <a class="btn btn-medium btn-accent detail-rsvp-submit" href="${loginWithRedirectUrl}">
                                <c:out value="${rsvpButtonLabel}"/>
                            </a>
                        </sec:authorize>
                        <c:if test="${not registered}">
                            <p class="detail-rsvp-note"><spring:message code="event.detail.reserveNote"/></p>
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
                                <a href="<c:url value='/eventos/${reviewedEvent.id}'/>"><c:out value="${reviewedEvent.title}"/></a>
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
