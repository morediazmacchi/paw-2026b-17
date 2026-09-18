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
                </c:when>
                <c:otherwise>
                    <span class="creator-rating-count"><spring:message code="auth.events.noReviewsYet"/></span>
                </c:otherwise>
            </c:choose>
        </div>

        <c:choose>
            <c:when test="${empty createdEvents}">
                <p class="empty-state"><spring:message code="auth.events.empty"/></p>
            </c:when>
            <c:otherwise>
                <spring:message code="auth.events.editButton" var="editButtonLabel"/>
                <div class="event-row-list">
                    <c:forEach var="event" items="${createdEvents}">
                        <paw:eventRow id="${event.id}" nombre="${event.title}" fecha="${event.eventDate}"
                                      ubicacion="${event.neighborhood.displayName}" precio="${event.price}"
                                      imagenId="${event.imageId}">
                            <%-- Sin funcionalidad todavia: lo conecta una companera despues. --%>
                            <paw:button text="${editButtonLabel}" type="button" cssClass="btn-accent"/>
                        </paw:eventRow>
                    </c:forEach>
                </div>
            </c:otherwise>
        </c:choose>
    </main>
</div>

</body>
</html>
