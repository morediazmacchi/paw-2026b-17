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
        <a class="pill ${empty selectedCategoria ? 'pill-active' : ''}"
           href="<c:url value='/eventos'>
                   <c:param name='precioMin' value='${precioMin}'/>
                   <c:param name='precioMax' value='${precioMax}'/>
                   <c:param name='fechaDesde' value='${fechaDesde}'/>
                   <c:param name='fechaHasta' value='${fechaHasta}'/>
                   <c:param name='barrio' value='${barrio}'/>
                 </c:url>">Todos</a>
        <c:forEach var="cat" items="${categories}">
            <a class="pill ${cat.id == selectedCategoria ? 'pill-active' : ''}"
               href="<c:url value='/eventos'>
                       <c:param name='categoria' value='${cat.id}'/>
                       <c:param name='precioMin' value='${precioMin}'/>
                       <c:param name='precioMax' value='${precioMax}'/>
                       <c:param name='fechaDesde' value='${fechaDesde}'/>
                       <c:param name='fechaHasta' value='${fechaHasta}'/>
                       <c:param name='barrio' value='${barrio}'/>
                     </c:url>">
                <c:out value="${cat.name}"/>
            </a>
        </c:forEach>
    </nav>
    <a class="btn btn-medium btn-accent" href="<c:url value='/eventos/publicar'/>">+ Añadir evento</a>
</header>

<main class="page">
    <form method="get" action="<c:url value='/eventos'/>" class="filter-bar">
        <input type="hidden" name="categoria" value="${selectedCategoria}"/>
        <paw:input name="precioMin" label="Precio desde" type="number" min="0" required="${false}"
                   value="${precioMin}"/>
        <paw:input name="precioMax" label="Precio hasta" type="number" min="0" required="${false}"
                   value="${precioMax}" errorMessage="${filterErrors.precioMax}"/>
        <paw:input name="fechaDesde" label="Fecha desde" type="date" required="${false}"
                   value="${fechaDesde}"/>
        <paw:input name="fechaHasta" label="Fecha hasta" type="date" required="${false}"
                   value="${fechaHasta}" errorMessage="${filterErrors.fechaHasta}"/>
        <div class="form-group">
            <label for="barrio">Barrio</label>
            <select id="barrio" name="barrio">
                <option value="">Todos</option>
                <c:forEach var="b" items="${barrios}">
                    <option value="${b}" ${b.name() == barrio ? 'selected' : ''}>
                        <c:out value="${b.displayName}"/>
                    </option>
                </c:forEach>
            </select>
        </div>
        <paw:button text="Filtrar" cssClass="btn-accent"/>
        <a class="btn btn-medium" href="<c:url value='/eventos'/>">Limpiar filtros</a>
    </form>

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

    <c:choose>
        <c:when test="${empty events}">
            <p class="empty-state">No hay eventos que coincidan con lo que buscás.</p>
        </c:when>
        <c:otherwise>
            <div class="card-grid">
                <c:forEach var="event" items="${events}">
                    <c:set var="cardPrecio" value="${event.price == 0 ? null : event.price}" />
                    <c:set var="cardUbicacion" value="${empty event.neighborhood ? event.address : event.neighborhood.displayName}" />
                    <paw:card id="${event.id}" nombre="${event.title}" precio="${cardPrecio}"
                              fecha="${event.eventDate}" categoria="${event.categoryName}"
                              ubicacion="${cardUbicacion}" imagenId="${event.imageId}" />
                </c:forEach>
            </div>
        </c:otherwise>
    </c:choose>
</main>

</body>
</html>
