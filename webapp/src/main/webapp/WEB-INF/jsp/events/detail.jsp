<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <c:out value="${event.title}"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</head>
<body>

<paw:navbar/>

<spring:message code="event.detail.backButton" var="backLabel"/>
<section class="detail-hero">
    <c:if test="${not empty event.imageId}">
        <img class="detail-hero-img" src="<c:url value='/image/${event.imageId}'/>" alt=""/>
    </c:if>
    <div class="detail-hero-overlay"></div>
    <a class="detail-hero-back" href="<c:url value='/eventos'/>">&larr; <c:out value="${backLabel}"/></a>
    <c:if test="${not empty event.categoryName}">
        <spring:message code="category.${event.categoryName}" text="${event.categoryName}" var="eventCategoria"/>
        <span class="detail-hero-badge"><c:out value="${eventCategoria}"/></span>
    </c:if>
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
                    <c:when test="${full}">
                        <p class="alert"><spring:message code="event.detail.eventFull"/></p>
                    </c:when>
                    <c:otherwise>
                        <spring:message code="event.detail.emailLabel" var="emailLabel"/>
                        <spring:message code="event.detail.nameLabel" var="nameLabel"/>
                        <spring:message code="event.detail.rsvpButton" var="rsvpButtonLabel"/>
                        <form action="<c:url value='/eventos/${event.id}/quiero-ir'/>" method="post" novalidate="novalidate">
                            <paw:input name="nombre" label="${nameLabel}" type="text"
                                       value="${registrationForm.nombre}" errorMessage="${fieldErrors.nombre}" />
                            <paw:input name="email" label="${emailLabel}" type="email"
                                       value="${registrationForm.email}" errorMessage="${fieldErrors.email}" />
                            <paw:button text="${rsvpButtonLabel} →" cssClass="btn-accent detail-rsvp-submit" />
                        </form>
                        <p class="detail-rsvp-note"><spring:message code="event.detail.reserveNote"/></p>
                    </c:otherwise>
                </c:choose>
            </div>
        </aside>
    </div>
</main>

</body>
</html>
