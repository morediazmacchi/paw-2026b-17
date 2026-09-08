<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="event.list.title"/></title>
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
                 </c:url>"><spring:message code="event.list.categoryAll"/></a>
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
    <spring:message code="event.list.addButton" var="addButtonLabel"/>
    <a class="btn btn-medium btn-accent" href="<c:url value='/eventos/publicar'/>"><c:out value="${addButtonLabel}"/></a>
</header>

<main class="page">
    <spring:message code="event.list.priceFromLabel" var="priceFromLabel"/>
    <spring:message code="event.list.priceToLabel" var="priceToLabel"/>
    <spring:message code="event.list.dateFromLabel" var="dateFromLabel"/>
    <spring:message code="event.list.dateToLabel" var="dateToLabel"/>
    <spring:message code="event.list.neighborhoodLabel" var="neighborhoodLabel"/>
    <spring:message code="event.list.neighborhoodAll" var="neighborhoodAllLabel"/>
    <spring:message code="event.list.filterButton" var="filterButtonLabel"/>
    <spring:message code="event.list.clearFiltersButton" var="clearFiltersLabel"/>
    <form method="get" action="<c:url value='/eventos'/>" class="filter-bar">
        <input type="hidden" name="categoria" value="${selectedCategoria}"/>
        <paw:input name="precioMin" label="${priceFromLabel}" type="number" min="0" required="${false}"
                   value="${precioMin}"/>
        <paw:input name="precioMax" label="${priceToLabel}" type="number" min="0" required="${false}"
                   value="${precioMax}" errorMessage="${filterErrors.precioMax}"/>
        <paw:input name="fechaDesde" label="${dateFromLabel}" type="date" required="${false}"
                   value="${fechaDesde}"/>
        <paw:input name="fechaHasta" label="${dateToLabel}" type="date" required="${false}"
                   value="${fechaHasta}" errorMessage="${filterErrors.fechaHasta}"/>
        <div class="form-group">
            <label for="barrio"><c:out value="${neighborhoodLabel}"/></label>
            <select id="barrio" name="barrio">
                <option value=""><c:out value="${neighborhoodAllLabel}"/></option>
                <c:forEach var="b" items="${barrios}">
                    <option value="${b}" ${b.name() == barrio ? 'selected' : ''}>
                        <c:out value="${b.displayName}"/>
                    </option>
                </c:forEach>
            </select>
        </div>
        <paw:button text="${filterButtonLabel}" cssClass="btn-accent"/>
        <a class="btn btn-medium" href="<c:url value='/eventos'/>"><c:out value="${clearFiltersLabel}"/></a>
    </form>

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

    <c:choose>
        <c:when test="${empty events}">
            <p class="empty-state"><spring:message code="event.list.empty"/></p>
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

            <c:if test="${totalPages > 1}">
                <nav class="pager">
                    <c:if test="${currentPage > 1}">
                        <a class="btn btn-medium"
                           href="<c:url value='/eventos'>
                                   <c:param name='categoria' value='${selectedCategoria}'/>
                                   <c:param name='precioMin' value='${precioMin}'/>
                                   <c:param name='precioMax' value='${precioMax}'/>
                                   <c:param name='fechaDesde' value='${fechaDesde}'/>
                                   <c:param name='fechaHasta' value='${fechaHasta}'/>
                                   <c:param name='barrio' value='${barrio}'/>
                                   <c:param name='sort' value='${sort}'/>
                                   <c:param name='page' value='${currentPage - 1}'/>
                                 </c:url>"><spring:message code="event.list.pagePrevious"/></a>
                    </c:if>

                    <span class="pager-indicator">
                        <spring:message code="event.list.pageIndicator" arguments="${currentPage},${totalPages}"/>
                    </span>

                    <c:if test="${currentPage < totalPages}">
                        <a class="btn btn-medium"
                           href="<c:url value='/eventos'>
                                   <c:param name='categoria' value='${selectedCategoria}'/>
                                   <c:param name='precioMin' value='${precioMin}'/>
                                   <c:param name='precioMax' value='${precioMax}'/>
                                   <c:param name='fechaDesde' value='${fechaDesde}'/>
                                   <c:param name='fechaHasta' value='${fechaHasta}'/>
                                   <c:param name='barrio' value='${barrio}'/>
                                   <c:param name='sort' value='${sort}'/>
                                   <c:param name='page' value='${currentPage + 1}'/>
                                 </c:url>"><spring:message code="event.list.pageNext"/></a>
                    </c:if>
                </nav>
            </c:if>
        </c:otherwise>
    </c:choose>
</main>

</body>
</html>
