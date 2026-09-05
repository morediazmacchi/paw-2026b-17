<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <c:out value="${event.title}"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</head>
<body>

<header class="navbar">
    <a class="logo" href="<c:url value='/eventos'/>">p<span class="logo-dot">.</span> plancito</a>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>">&larr; Volver a eventos</a>
</header>

<main class="page page-narrow">
    <c:if test="${not empty rsvp}">
        <c:choose>
            <c:when test="${rsvp == 'confirmed'}">
                <p class="alert">Listo, quedaste confirmado.</p>
            </c:when>
            <c:when test="${rsvp == 'ya-anotado'}">
                <p class="alert">Ya estabas anotado.</p>
            </c:when>
            <c:when test="${rsvp == 'lleno'}">
                <p class="alert">El evento ya esta lleno, no te pudimos anotar.</p>
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
                    <c:when test="${event.price == 0}">Gratis</c:when>
                    <c:otherwise>$<fmt:formatNumber value="${event.price}" type="number" minFractionDigits="0" maxFractionDigits="0" /></c:otherwise>
                </c:choose>
            </span>
        </div>

        <c:if test="${not empty event.categoryName}">
            <span class="card-categoria"><c:out value="${event.categoryName}"/></span>
        </c:if>
        <h1 class="detail-titulo"><c:out value="${event.title}"/></h1>
        <p class="detail-meta">
            <fmt:formatDate value="${event.eventDate}" pattern="EEEE dd/MM/yyyy · HH:mm" />hs
            &middot; <c:out value="${event.address}"/><c:if test="${not empty event.neighborhood}"> (<c:out value="${event.neighborhood.displayName}"/>)</c:if>
        </p>
        <c:if test="${not empty event.maxCapacity}">
            <p class="detail-meta">Cupo máximo: <c:out value="${event.maxCapacity}"/> personas</p>
        </c:if>

        <c:if test="${not empty event.description}">
            <p class="detail-descripcion"><c:out value="${event.description}"/></p>
        </c:if>

        <section class="rsvp-box">
            <h2>Quiero ir</h2>
            <c:choose>
                <c:when test="${full}">
                    <p class="alert">Evento a su capacidad.</p>
                </c:when>
                <c:otherwise>
                    <form action="<c:url value='/eventos/${event.id}/quiero-ir'/>" method="post" novalidate="novalidate">
                        <paw:input name="email" label="Tu email" type="email"
                                   value="${registrationForm.email}" errorMessage="${fieldErrors.email}" />
                        <paw:input name="nombre" label="Tu nombre" type="text"
                                   value="${registrationForm.nombre}" errorMessage="${fieldErrors.nombre}" />
                        <paw:button text="Quiero ir" cssClass="btn-accent" />
                    </form>
                </c:otherwise>
            </c:choose>
        </section>
    </article>
</main>

</body>
</html>
