<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; Eventos</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>

<header class="navbar">
    <a class="logo" href="<c:url value='/eventos'/>">p<span class="logo-dot">.</span> plancito</a>
    <nav class="pills">
        <a class="pill ${empty selectedCategoria ? 'pill-active' : ''}" href="<c:url value='/eventos'/>">Todos</a>
        <c:forEach var="cat" items="${categories}">
            <a class="pill ${cat.id == selectedCategoria ? 'pill-active' : ''}"
               href="<c:url value='/eventos'><c:param name='categoria' value='${cat.id}'/></c:url>">
                <c:out value="${cat.name}"/>
            </a>
        </c:forEach>
    </nav>
    <a class="btn btn-medium btn-accent" href="<c:url value='/eventos/publicar'/>">+ Añadir evento</a>
</header>

<main class="page">
    <c:if test="${not empty rsvp}">
        <c:choose>
            <c:when test="${rsvp == 'confirmed'}">
                <p class="alert">Listo, quedaste confirmado.</p>
            </c:when>
            <c:when test="${rsvp == 'waitlisted'}">
                <p class="alert">El evento esta lleno, quedaste en lista de espera.</p>
            </c:when>
        </c:choose>
    </c:if>

    <c:choose>
        <c:when test="${empty events}">
            <p class="empty-state">Todavía no hay eventos<c:if test="${not empty selectedCategoria}"> en esta categoría</c:if>.</p>
        </c:when>
        <c:otherwise>
            <div class="card-grid">
                <c:forEach var="event" items="${events}">
                    <c:set var="cardPrecio" value="${event.price == 0 ? null : event.price}" />
                    <c:set var="cardUbicacion" value="${empty event.neighborhood ? event.address : event.neighborhood}" />
                    <paw:card id="${event.id}" nombre="${event.title}" precio="${cardPrecio}"
                              fecha="${event.eventDate}" categoria="${event.categoryName}"
                              ubicacion="${cardUbicacion}" />
                </c:forEach>
            </div>
        </c:otherwise>
    </c:choose>
</main>

</body>
</html>
