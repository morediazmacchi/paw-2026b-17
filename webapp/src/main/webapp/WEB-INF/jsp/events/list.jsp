<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="event.list.title"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script src="<c:url value='/js/hero-carousel.js'/>"></script>
    <script src="<c:url value='/js/favorite-toggle.js'/>"></script>
</head>
<body>

<%-- Adonde vuelve el corazon de favorito despues de togglear: esta misma
     pagina con los filtros que tenga puestos, no siempre /eventos pelado.
     OJO: pageContext.request.requestURI aca adentro da la ruta del forward
     interno (/WEB-INF/jsp/...), no la URL real: Spring llega a este JSP
     con un forward, y forward pisa requestURI. La URL de verdad esta en
     este atributo que el forward deja seteado (spec de Servlet). --%>
<c:set var="currentUrl" value="${requestScope['javax.servlet.forward.request_uri']}${not empty requestScope['javax.servlet.forward.query_string'] ? '?' : ''}${requestScope['javax.servlet.forward.query_string']}"/>

<paw:navbar>
    <spring:message code="event.list.addButton" var="addButtonLabel"/>
    <a class="btn btn-medium btn-accent" href="<c:url value='/eventos/publicar'/>"><c:out value="${addButtonLabel}"/></a>
</paw:navbar>

<c:if test="${not empty featuredEvents}">
    <section class="hero">
        <c:forEach var="fe" items="${featuredEvents}" varStatus="st">
            <div class="hero-slide${st.first ? ' hero-slide-active' : ''}">
                <c:if test="${not empty fe.imageId}">
                    <img class="hero-img" src="<c:url value='/image/${fe.imageId}'/>" alt=""/>
                </c:if>
                <div class="hero-overlay"></div>
                <div class="hero-content">
                    <div class="hero-badges">
                        <span class="hero-badge hero-badge-accent"><spring:message code="event.list.featuredBadge"/></span>
                        <spring:message code="category.${fe.categoryName}" text="${fe.categoryName}" var="feCategoria"/>
                        <span class="hero-badge"><c:out value="${fn:toUpperCase(feCategoria)}"/></span>
                    </div>
                    <h1 class="hero-title"><c:out value="${fe.title}"/></h1>
                    <c:if test="${not empty fe.description}">
                        <p class="hero-description"><c:out value="${fe.description}"/></p>
                    </c:if>
                    <div class="hero-meta">
                        <span><paw:icon name="calendar"/> <fmt:formatDate value="${fe.eventDate}" pattern="EEE dd MMM · HH:mm"/>hs</span>
                        <span><paw:icon name="pin"/> <c:out value="${empty fe.neighborhood ? fe.address : fe.neighborhood.displayName}"/></span>
                        <span class="hero-price">
                            <c:choose>
                                <c:when test="${fe.price == 0}"><spring:message code="event.price.free"/></c:when>
                                <c:otherwise>$<fmt:formatNumber value="${fe.price}" type="number" maxFractionDigits="0"/></c:otherwise>
                            </c:choose>
                        </span>
                    </div>
                    <a class="btn btn-medium btn-accent" href="<c:url value='/eventos/${fe.id}'/>">
                        <spring:message code="event.list.viewEvent"/>
                    </a>
                </div>
            </div>
        </c:forEach>
        <c:if test="${fn:length(featuredEvents) > 1}">
            <div class="hero-dots">
                <c:forEach var="fe" items="${featuredEvents}" varStatus="st">
                    <span class="hero-dot${st.first ? ' hero-dot-active' : ''}"></span>
                </c:forEach>
            </div>
        </c:if>
    </section>
</c:if>

<main class="page">
    <spring:message code="event.list.priceFromLabel" var="priceFromLabel"/>
    <spring:message code="event.list.priceToLabel" var="priceToLabel"/>
    <spring:message code="event.list.dateFromLabel" var="dateFromLabel"/>
    <spring:message code="event.list.dateToLabel" var="dateToLabel"/>
    <spring:message code="event.list.categoryLabel" var="categoryLabel"/>
    <spring:message code="event.list.neighborhoodLabel" var="neighborhoodLabel"/>
    <spring:message code="event.list.dateFilterLabel" var="dateFilterLabel"/>
    <spring:message code="event.list.priceFilterLabel" var="priceFilterLabel"/>
    <spring:message code="event.list.categoryAll" var="allLabel"/>
    <spring:message code="event.list.filterButton" var="filterButtonLabel"/>
    <spring:message code="event.list.clearFiltersButton" var="clearFiltersLabel"/>
    <spring:message code="event.list.clearAllButton" var="clearAllLabel"/>

    <form method="get" action="<c:url value='/eventos'/>" class="filter-bar-v2">
        <details class="filter-dropdown" name="filter-group">
            <summary>
                <span class="filter-dropdown-label"><c:out value="${categoryLabel}"/></span>
                <span class="filter-dropdown-value">
                    <c:choose>
                        <c:when test="${empty selectedCategorias}"><c:out value="${allLabel}"/></c:when>
                        <c:otherwise><spring:message code="event.list.selectedCount" arguments="${fn:length(selectedCategorias)}"/></c:otherwise>
                    </c:choose>
                </span>
            </summary>
            <div class="filter-dropdown-panel">
                <c:forEach var="cat" items="${categories}">
                    <label class="filter-checkbox">
                        <input type="checkbox" name="categoria" value="${cat.id}"
                               ${not empty selectedCategorias && selectedCategorias.contains(cat.id) ? 'checked="checked"' : ''}/>
                        <spring:message code="category.${cat.name}" text="${cat.name}"/>
                    </label>
                </c:forEach>
            </div>
        </details>

        <details class="filter-dropdown" name="filter-group">
            <summary>
                <span class="filter-dropdown-label"><c:out value="${neighborhoodLabel}"/></span>
                <span class="filter-dropdown-value">
                    <c:choose>
                        <c:when test="${empty selectedBarrios}"><c:out value="${allLabel}"/></c:when>
                        <c:otherwise><spring:message code="event.list.selectedCount" arguments="${fn:length(selectedBarrios)}"/></c:otherwise>
                    </c:choose>
                </span>
            </summary>
            <div class="filter-dropdown-panel filter-dropdown-panel-scroll">
                <c:forEach var="b" items="${barrios}">
                    <label class="filter-checkbox">
                        <input type="checkbox" name="barrio" value="${b}"
                               ${not empty selectedBarrios && selectedBarrios.contains(b) ? 'checked="checked"' : ''}/>
                        <c:out value="${b.displayName}"/>
                    </label>
                </c:forEach>
            </div>
        </details>

        <details class="filter-dropdown" name="filter-group">
            <summary>
                <span class="filter-dropdown-label"><c:out value="${dateFilterLabel}"/></span>
                <span class="filter-dropdown-value">
                    <c:choose>
                        <c:when test="${empty fechaDesde && empty fechaHasta}"><c:out value="${allLabel}"/></c:when>
                        <c:otherwise><c:out value="${fechaDesde}"/> &ndash; <c:out value="${fechaHasta}"/></c:otherwise>
                    </c:choose>
                </span>
            </summary>
            <div class="filter-dropdown-panel">
                <paw:input name="fechaDesde" label="${dateFromLabel}" type="date" required="${false}"
                           value="${fechaDesde}"/>
                <paw:input name="fechaHasta" label="${dateToLabel}" type="date" required="${false}"
                           value="${fechaHasta}" errorMessage="${filterErrors.fechaHasta}"/>
            </div>
        </details>

        <details class="filter-dropdown" name="filter-group">
            <summary>
                <span class="filter-dropdown-label"><c:out value="${priceFilterLabel}"/></span>
                <span class="filter-dropdown-value">
                    <c:choose>
                        <c:when test="${empty precioMin && empty precioMax}"><c:out value="${allLabel}"/></c:when>
                        <c:otherwise><c:out value="${empty precioMin ? '$0' : precioMin}"/> &ndash; <c:out value="${empty precioMax ? '&#8734;' : precioMax}"/></c:otherwise>
                    </c:choose>
                </span>
            </summary>
            <div class="filter-dropdown-panel">
                <paw:input name="precioMin" label="${priceFromLabel}" type="number" min="0" required="${false}"
                           value="${precioMin}"/>
                <paw:input name="precioMax" label="${priceToLabel}" type="number" min="0" required="${false}"
                           value="${precioMax}" errorMessage="${filterErrors.precioMax}"/>
            </div>
        </details>

        <paw:button text="${filterButtonLabel}" cssClass="btn-accent"/>
        <c:if test="${hasActiveFilters}">
            <a class="btn btn-medium filter-clear-all" href="<c:url value='/eventos'/>">&times; <c:out value="${clearAllLabel}"/></a>
        </c:if>
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

    <c:if test="${hasActiveFilters}">
        <div class="results-header">
            <h2 class="results-count"><spring:message code="event.list.resultsCount" arguments="${totalElements}"/></h2>
            <a class="results-clear" href="<c:url value='/eventos'/>"><c:out value="${clearFiltersLabel}"/></a>
        </div>
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
                    <spring:message code="category.${event.categoryName}" text="${event.categoryName}" var="cardCategoria"/>
                    <paw:card id="${event.id}" nombre="${event.title}" precio="${cardPrecio}"
                              fecha="${event.eventDate}" categoria="${cardCategoria}"
                              ubicacion="${cardUbicacion}" imagenId="${event.imageId}"
                              favorito="${not empty favoriteEventIds and favoriteEventIds.contains(event.id)}"
                              returnTo="${currentUrl}" />
                </c:forEach>
            </div>

            <c:if test="${totalPages > 1}">
                <spring:message code="event.list.prevPage" var="prevPageLabel"/>
                <spring:message code="event.list.nextPage" var="nextPageLabel"/>
                <nav class="pager">
                    <c:if test="${currentPage > 1}">
                        <a class="pager-arrow"
                           href="<c:url value='/eventos'>
                                   <c:forEach var="cid" items="${selectedCategorias}"><c:param name='categoria' value='${cid}'/></c:forEach>
                                   <c:forEach var="bar" items="${selectedBarrios}"><c:param name='barrio' value='${bar}'/></c:forEach>
                                   <c:param name='precioMin' value='${precioMin}'/>
                                   <c:param name='precioMax' value='${precioMax}'/>
                                   <c:param name='fechaDesde' value='${fechaDesde}'/>
                                   <c:param name='fechaHasta' value='${fechaHasta}'/>
                                   <c:param name='sort' value='${sort}'/>
                                   <c:param name='page' value='${currentPage - 1}'/>
                                 </c:url>"><c:out value="${prevPageLabel}"/></a>
                    </c:if>

                    <c:forEach var="p" items="${pageNumbers}">
                        <c:choose>
                            <c:when test="${empty p}">
                                <span class="pager-ellipsis">&hellip;</span>
                            </c:when>
                            <c:when test="${p == currentPage}">
                                <span class="pager-number pager-number-active"><c:out value="${p}"/></span>
                            </c:when>
                            <c:otherwise>
                                <a class="pager-number"
                                   href="<c:url value='/eventos'>
                                           <c:forEach var="cid" items="${selectedCategorias}"><c:param name='categoria' value='${cid}'/></c:forEach>
                                           <c:forEach var="bar" items="${selectedBarrios}"><c:param name='barrio' value='${bar}'/></c:forEach>
                                           <c:param name='precioMin' value='${precioMin}'/>
                                           <c:param name='precioMax' value='${precioMax}'/>
                                           <c:param name='fechaDesde' value='${fechaDesde}'/>
                                           <c:param name='fechaHasta' value='${fechaHasta}'/>
                                           <c:param name='sort' value='${sort}'/>
                                           <c:param name='page' value='${p}'/>
                                         </c:url>"><c:out value="${p}"/></a>
                            </c:otherwise>
                        </c:choose>
                    </c:forEach>

                    <c:if test="${currentPage < totalPages}">
                        <a class="pager-arrow"
                           href="<c:url value='/eventos'>
                                   <c:forEach var="cid" items="${selectedCategorias}"><c:param name='categoria' value='${cid}'/></c:forEach>
                                   <c:forEach var="bar" items="${selectedBarrios}"><c:param name='barrio' value='${bar}'/></c:forEach>
                                   <c:param name='precioMin' value='${precioMin}'/>
                                   <c:param name='precioMax' value='${precioMax}'/>
                                   <c:param name='fechaDesde' value='${fechaDesde}'/>
                                   <c:param name='fechaHasta' value='${fechaHasta}'/>
                                   <c:param name='sort' value='${sort}'/>
                                   <c:param name='page' value='${currentPage + 1}'/>
                                 </c:url>"><c:out value="${nextPageLabel}"/></a>
                    </c:if>
                </nav>
            </c:if>
        </c:otherwise>
    </c:choose>
</main>

</body>
</html>
