<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="auth.events.title"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script src="<c:url value='/js/modal.js'/>"></script>
</head>
<body>

<paw:navbar>
    <spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>"><c:out value="${backToEventsLabel}"/></a>
</paw:navbar>

<div class="account-layout">
    <paw:accountSidebar active="events" registrationsCount="${registrationsCount}" favoritesCount="${favoritesCount}" eventsCount="${eventsCount}"/>

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
                <div class="event-row-list">
                    <c:forEach var="event" items="${createdEvents}">
                        <paw:eventRow id="${event.id}" nombre="${event.title}" fecha="${event.eventDate}"
                                      ubicacion="${event.neighborhood.displayName}" precio="${event.price}"
                                      imagenId="${event.imageId}">
                            <a class="btn btn-medium" href="<c:url value='/eventos/${event.id}/inscriptos'/>">
                                <c:out value="${attendeesButtonLabel}"/>
                            </a>
                            <a class="btn btn-medium btn-accent" href="<c:url value='/eventos/${event.id}/editar'/>">
                                <c:out value="${editButtonLabel}"/>
                            </a>
                        </paw:eventRow>
                    </c:forEach>
                </div>
            </c:otherwise>
        </c:choose>
    </main>
</div>

<c:if test="${creatorReviewCount > 0}">
    <div class="modal-overlay" id="myReviewsModal" hidden>
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
