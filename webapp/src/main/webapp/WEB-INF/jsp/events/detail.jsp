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

<spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
<header class="navbar">
    <a class="logo" href="<c:url value='/eventos'/>">p<span class="logo-dot">.</span> plancito</a>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>">&larr; <c:out value="${backToEventsLabel}"/></a>
</header>

<main class="page page-narrow">
    <c:if test="${not empty rsvp}">
        <c:choose>
            <c:when test="${rsvp == 'confirmed'}">
                <p class="alert"><spring:message code="event.rsvp.confirmed"/></p>
            </c:when>
            <c:when test="${rsvp == 'ya-anotado'}">
                <p class="alert"><spring:message code="event.rsvp.alreadyRegistered"/></p>
            </c:when>
            <c:when test="${rsvp == 'lleno'}">
                <p class="alert"><spring:message code="event.rsvp.full"/></p>
            </c:when>
        </c:choose>
    </c:if>

    <article class="detail">
        <div class="detail-placeholder">
            <c:if test="${not empty event.imageId}">
                <img class="detail-img" src="<c:url value='/image/${event.imageId}'/>" alt="<c:out value='${event.title}'/>" />
            </c:if>
            <span class="detail-precio">
                <c:choose>
                    <c:when test="${event.price == 0}"><spring:message code="event.price.free"/></c:when>
                    <c:otherwise>$<fmt:formatNumber value="${event.price}" type="number" minFractionDigits="0" maxFractionDigits="0" /></c:otherwise>
                </c:choose>
            </span>
        </div>

        <c:if test="${not empty event.categoryName}">
            <span class="card-categoria"><spring:message code="category.${event.categoryName}" text="${event.categoryName}" htmlEscape="true"/></span>
        </c:if>
        <h1 class="detail-titulo"><c:out value="${event.title}"/></h1>
        <p class="detail-meta">
            <fmt:formatDate value="${event.eventDate}" pattern="EEEE dd/MM/yyyy · HH:mm" />hs
            &middot; <c:out value="${event.address}"/><c:if test="${not empty event.neighborhood}"> (<c:out value="${event.neighborhood.displayName}"/>)</c:if>
        </p>
        <c:if test="${not empty event.maxCapacity}">
            <p class="detail-meta"><spring:message code="event.detail.maxCapacity" arguments="${event.maxCapacity}"/></p>
        </c:if>

        <c:if test="${not empty event.description}">
            <p class="detail-descripcion"><c:out value="${event.description}"/></p>
        </c:if>

        <section class="rsvp-box">
            <h2><spring:message code="event.detail.rsvpHeading"/></h2>
            <c:choose>
                <c:when test="${full}">
                    <p class="alert"><spring:message code="event.detail.eventFull"/></p>
                </c:when>
                <c:otherwise>
                    <spring:message code="event.detail.emailLabel" var="emailLabel"/>
                    <spring:message code="event.detail.nameLabel" var="nameLabel"/>
                    <spring:message code="event.detail.rsvpButton" var="rsvpButtonLabel"/>
                    <form action="<c:url value='/eventos/${event.id}/quiero-ir'/>" method="post" novalidate="novalidate">
                        <paw:input name="email" label="${emailLabel}" type="email"
                                   value="${registrationForm.email}" errorMessage="${fieldErrors.email}" />
                        <paw:input name="nombre" label="${nameLabel}" type="text"
                                   value="${registrationForm.nombre}" errorMessage="${fieldErrors.nombre}" />
                        <paw:button text="${rsvpButtonLabel}" cssClass="btn-accent" />
                    </form>
                </c:otherwise>
            </c:choose>
        </section>
    </article>
</main>

</body>
</html>
