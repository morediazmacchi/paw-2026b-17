<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="event.list.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/hero-carousel.js'/>"></script>
    <%-- data-open-modal/data-close-modal de los almanaques "Desde"/"Hasta". --%>
    <script src="<c:url value='/js/modal.js'/>"></script>
    <script src="<c:url value='/js/date-filter-calendar.js'/>"></script>
    <script src="<c:url value='/js/price-range.js'/>"></script>
</jsp:attribute>
<jsp:body>
<%-- Adonde vuelve el corazon de favorito despues de togglear: esta misma
     pagina con los filtros que tenga puestos, no siempre /eventos pelado.
     OJO: pageContext.request.requestURI aca adentro da la ruta del forward
     interno (/WEB-INF/jsp/...), no la URL real: Spring llega a este JSP
     con un forward, y forward pisa requestURI. La URL de verdad esta en
     este atributo que el forward deja seteado (spec de Servlet). --%>
<c:set var="rawCurrentUri" value="${requestScope['javax.servlet.forward.request_uri']}"/>
<%-- request_uri viene CON el context path (spec de Servlet: getRequestURI()
     siempre lo incluye). El controller arma "redirect:" + returnTo, y Spring
     ya antepone el context path solo -- si returnTo lo trae puesto de antes,
     queda duplicado (deploy no-root, ej /paw-2026b-17/paw-2026b-17/...). En
     local nunca se nota: ahi el context path es vacio, duplicar "nada" no
     se ve. Por eso se saca aca, antes de usarlo como returnTo. --%>
<c:set var="currentUrl" value="${fn:substring(rawCurrentUri, fn:length(pageContext.request.contextPath), fn:length(rawCurrentUri))}${not empty requestScope['javax.servlet.forward.query_string'] ? '?' : ''}${requestScope['javax.servlet.forward.query_string']}"/>

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
                        <c:forEach var="fec" items="${fe.categories}">
                            <spring:message code="category.${fec.categoryName}" text="${fec.categoryName}" var="feCategoria"/>
                            <span class="hero-badge"><c:out value="${fn:toUpperCase(feCategoria)}"/></span>
                        </c:forEach>
                    </div>
                    <h1 class="hero-title"><c:out value="${fe.title}"/></h1>
                    <c:if test="${not empty fe.description}">
                        <p class="hero-description"><c:out value="${fe.description}"/></p>
                    </c:if>
                    <div class="hero-meta">
                        <span><paw:icon name="calendar"/> <paw:date value="${fe.eventDate}" format="longDateTime"/></span>
                        <span><paw:icon name="pin"/> <c:out value="${empty fe.neighborhood ? fe.address : fe.neighborhood.displayName}"/></span>
                        <span class="hero-price">
                            <c:choose>
                                <c:when test="${fe.free}"><spring:message code="event.price.free"/></c:when>
                                <c:otherwise><paw:price value="${fe.price}"/></c:otherwise>
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
    <%-- Lo muestra date-filter-calendar.js cuando no hay fecha elegida. --%>
    <spring:message code="event.list.anyDate" var="anyDateLabel"/>
    <spring:message code="event.list.categoryLabel" var="categoryLabel"/>
    <spring:message code="event.list.neighborhoodLabel" var="neighborhoodLabel"/>
    <spring:message code="event.list.dateFilterLabel" var="dateFilterLabel"/>
    <spring:message code="event.list.priceFilterLabel" var="priceFilterLabel"/>
    <spring:message code="event.list.categoryAll" var="allLabel"/>
    <spring:message code="event.list.organizerRatingLabel" var="organizerRatingLabel"/>
    <spring:message code="event.list.filterButton" var="filterButtonLabel"/>
    <spring:message code="event.list.clearFiltersButton" var="clearFiltersLabel"/>
    <spring:message code="event.list.clearAllButton" var="clearAllLabel"/>

    <form method="get" action="<c:url value='/eventos'/>">
    <div class="filter-bar-v2">
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
                <%-- Mismo almanaque que publicar/editar evento (ver event-wizard.js),
                     pero sin hora y sin techo de +2 anios: aca son los dos extremos de
                     un rango, y la unica regla es que uno no cruce al otro. Cada
                     almanaque deshabilita los dias invalidos segun lo que ya este
                     elegido en el otro (ver date-filter-calendar.js), en vez de
                     esperar a que el server rechace el rango al filtrar. --%>
                <div class="form-group">
                    <label for="fechaDesdeTrigger"><c:out value="${dateFromLabel}"/></label>
                    <button type="button" id="fechaDesdeTrigger" class="calendar-trigger"
                            data-open-modal="fechaDesdeCalendarModal">
                        <paw:icon name="calendar"/>
                        <span class="calendar-trigger-text" data-calendar-trigger-text
                              data-calendar-empty-label="<c:out value='${anyDateLabel}'/>"></span>
                    </button>
                    <input type="hidden" id="fechaDesde" name="fechaDesde" value="<c:out value='${fechaDesde}'/>"/>
                </div>
                <div class="form-group">
                    <label for="fechaHastaTrigger"><c:out value="${dateToLabel}"/></label>
                    <button type="button" id="fechaHastaTrigger" class="calendar-trigger"
                            data-open-modal="fechaHastaCalendarModal">
                        <paw:icon name="calendar"/>
                        <span class="calendar-trigger-text" data-calendar-trigger-text
                              data-calendar-empty-label="<c:out value='${anyDateLabel}'/>"></span>
                    </button>
                    <input type="hidden" id="fechaHasta" name="fechaHasta" value="<c:out value='${fechaHasta}'/>"/>
                    <c:if test="${not empty filterErrors.fechaHasta}">
                        <span class="error-message"><c:out value="${filterErrors.fechaHasta}"/></span>
                    </c:if>
                </div>
            </div>
        </details>

        <spring:message code="event.detail.closeButton" var="calendarCloseLabel"/>
        <spring:message code="event.publish.prevMonth" var="calendarPrevLabel"/>
        <spring:message code="event.publish.nextMonth" var="calendarNextLabel"/>
        <div class="modal-overlay calendar-modal" id="fechaDesdeCalendarModal" hidden>
            <div class="modal-box calendar-box">
                <div class="modal-header">
                    <h2><c:out value="${dateFromLabel}"/></h2>
                    <button type="button" class="modal-close" data-close-modal aria-label="${calendarCloseLabel}">
                        <paw:icon name="close"/>
                    </button>
                </div>
                <div class="calendar-header">
                    <button type="button" class="calendar-nav" data-calendar-prev aria-label="${calendarPrevLabel}">
                        <paw:icon name="chevron-left"/>
                    </button>
                    <span class="calendar-month-label" data-calendar-month-label></span>
                    <button type="button" class="calendar-nav" data-calendar-next aria-label="${calendarNextLabel}">
                        <paw:icon name="chevron-right"/>
                    </button>
                </div>
                <paw:calendarWeekdays/>
                <div class="calendar-days" data-calendar-days></div>
            </div>
        </div>
        <div class="modal-overlay calendar-modal" id="fechaHastaCalendarModal" hidden>
            <div class="modal-box calendar-box">
                <div class="modal-header">
                    <h2><c:out value="${dateToLabel}"/></h2>
                    <button type="button" class="modal-close" data-close-modal aria-label="${calendarCloseLabel}">
                        <paw:icon name="close"/>
                    </button>
                </div>
                <div class="calendar-header">
                    <button type="button" class="calendar-nav" data-calendar-prev aria-label="${calendarPrevLabel}">
                        <paw:icon name="chevron-left"/>
                    </button>
                    <span class="calendar-month-label" data-calendar-month-label></span>
                    <button type="button" class="calendar-nav" data-calendar-next aria-label="${calendarNextLabel}">
                        <paw:icon name="chevron-right"/>
                    </button>
                </div>
                <paw:calendarWeekdays/>
                <div class="calendar-days" data-calendar-days></div>
            </div>
        </div>

        <details class="filter-dropdown" name="filter-group">
            <summary>
                <span class="filter-dropdown-label"><c:out value="${priceFilterLabel}"/></span>
                <span class="filter-dropdown-value">
                    <c:choose>
                        <c:when test="${empty precioMin && empty precioMax}"><c:out value="${allLabel}"/></c:when>
                        <c:otherwise><c:out value="${empty precioMin ? '$0' : precioMin}"/> &ndash;
                            <c:choose>
                                <c:when test="${empty precioMax}"><spring:message code="event.list.priceRangeNoMax"/></c:when>
                                <c:otherwise><c:out value="${precioMax}"/></c:otherwise>
                            </c:choose>
                        </c:otherwise>
                    </c:choose>
                </span>
            </summary>
            <div class="filter-dropdown-panel">
                <%-- Slider de dos puntas tipo Airbnb (ver price-range.js): sincronizado
                     con los dos inputs numericos de abajo, que siguen siendo los que
                     realmente viajan al servidor (y los que permiten un valor exacto
                     o mas alto que el tope visual del slider). --%>
                <div class="price-range" data-price-range>
                    <div class="price-range-track">
                        <div class="price-range-fill" data-price-range-fill></div>
                    </div>
                    <input type="range" class="price-range-input" min="0" max="50000" step="500"
                           value="${empty precioMin ? 0 : precioMin}" data-price-range-min/>
                    <input type="range" class="price-range-input" min="0" max="50000" step="500"
                           value="${empty precioMax ? 50000 : precioMax}" data-price-range-max/>
                </div>
                <div class="price-range-labels">
                    <span><spring:message code="event.price.free"/></span>
                    <span><spring:message code="event.list.priceRangeMaxLabel"/></span>
                </div>
                <paw:input name="precioMin" label="${priceFromLabel}" type="number" min="0" required="${false}"
                           value="${precioMin}"/>
                <paw:input name="precioMax" label="${priceToLabel}" type="number" min="0" required="${false}"
                           value="${precioMax}" errorMessage="${filterErrors.precioMax}"/>
            </div>
        </details>

        <details class="filter-dropdown" name="filter-group">
            <summary>
                <span class="filter-dropdown-label"><c:out value="${organizerRatingLabel}"/></span>
                <span class="filter-dropdown-value">
                    <c:choose>
                        <c:when test="${empty puntajeMin}"><c:out value="${allLabel}"/></c:when>
                        <c:when test="${puntajeMin == '5'}"><spring:message code="event.list.organizerRating5"/></c:when>
                        <c:otherwise><spring:message code="event.list.organizerRatingPlus" arguments="${puntajeMin}"/></c:otherwise>
                    </c:choose>
                </span>
            </summary>
            <div class="filter-dropdown-panel">
                <label class="filter-checkbox">
                    <input type="radio" name="puntajeMin" value="" ${empty puntajeMin ? 'checked="checked"' : ''}/>
                    <c:out value="${allLabel}"/>
                </label>
                <c:forEach var="n" begin="3" end="5">
                    <label class="filter-checkbox">
                        <input type="radio" name="puntajeMin" value="${n}" ${puntajeMin == n ? 'checked="checked"' : ''}/>
                        <c:choose>
                            <c:when test="${n == 5}"><spring:message code="event.list.organizerRating5"/></c:when>
                            <c:otherwise><spring:message code="event.list.organizerRatingPlus" arguments="${n}"/></c:otherwise>
                        </c:choose>
                    </label>
                </c:forEach>
            </div>
        </details>

        <spring:message code="event.list.availabilityFilter" var="availabilityLabel"/>
        <details class="filter-dropdown" name="filter-group">
            <summary>
                <span class="filter-dropdown-label"><c:out value="${availabilityLabel}"/></span>
                <span class="filter-dropdown-value">
                    <c:choose>
                        <c:when test="${not empty pocosCupos and not empty agotados}"><spring:message code="event.list.selectedCount" arguments="2"/></c:when>
                        <c:when test="${not empty pocosCupos}"><spring:message code="event.list.fewSpotsShort"/></c:when>
                        <c:when test="${not empty agotados}"><spring:message code="event.list.soldOut"/></c:when>
                        <c:otherwise><c:out value="${allLabel}"/></c:otherwise>
                    </c:choose>
                </span>
            </summary>
            <div class="filter-dropdown-panel">
                <%-- Checkboxes: el que no esta tildado el navegador ni lo manda, y
                     ese filtro queda apagado. Se pueden tildar los dos. --%>
                <label class="filter-checkbox">
                    <input type="checkbox" name="pocosCupos" value="true"
                           ${not empty pocosCupos ? 'checked="checked"' : ''}/>
                    <spring:message code="event.list.fewSpots"/>
                </label>
                <label class="filter-checkbox">
                    <input type="checkbox" name="agotados" value="true"
                           ${not empty agotados ? 'checked="checked"' : ''}/>
                    <spring:message code="event.list.soldOut"/>
                </label>
            </div>
        </details>

        <paw:button text="${filterButtonLabel}" cssClass="btn-accent"/>
        <c:if test="${hasActiveFilters}">
            <a class="btn btn-medium filter-clear-all" href="<c:url value='/eventos'/>">&times; <c:out value="${clearAllLabel}"/></a>
        </c:if>
    </div>

    <%-- Ordenar, aparte de los filtros: un <select> comun que dispara el mismo
         GET apenas se elige una opcion (sin esperar a "Filtrar"). Sigue adentro
         de este form para viajar junto con los filtros ya puestos; solo cambia
         donde se ve (esta fila, arriba del listado, no el acordeon de arriba). --%>
    <spring:message code="event.list.sortLabel" var="sortLabel"/>
    <div class="results-header">
        <c:if test="${hasActiveFilters}">
            <div class="results-info">
                <h2 class="results-count"><spring:message code="event.list.resultsCount" arguments="${totalElements}"/></h2>
                <a class="results-clear" href="<c:url value='/eventos'/>"><c:out value="${clearFiltersLabel}"/></a>
            </div>
        </c:if>
        <label class="sort-select">
            <span><c:out value="${sortLabel}"/></span>
            <select name="sort" onchange="this.form.submit()">
                <c:forEach var="option" items="${sortOptions}">
                    <option value="${option}" ${sort == option.name() ? 'selected="selected"' : ''}>
                        <spring:message code="event.list.sort.${option}"/>
                    </option>
                </c:forEach>
            </select>
        </label>
    </div>
    </form>

    <c:choose>
        <c:when test="${empty events}">
            <p class="empty-state"><spring:message code="event.list.empty"/></p>
        </c:when>
        <c:otherwise>
            <div class="card-grid">
                <c:forEach var="event" items="${events}">
                    <c:set var="cardPrecio" value="${event.free ? null : event.price}" />
                    <c:set var="cardUbicacion" value="${empty event.neighborhood ? event.address : event.neighborhood.displayName}" />
                    <c:set var="cardCategoria" value=""/>
                    <c:forEach var="ecat" items="${event.categories}" varStatus="ecatStatus">
                        <spring:message code="category.${ecat.categoryName}" text="${ecat.categoryName}" var="ecatName"/>
                        <c:set var="cardCategoria" value="${cardCategoria}${ecatStatus.first ? '' : ', '}${ecatName}"/>
                    </c:forEach>
                    <paw:card id="${event.id}" nombre="${event.title}" precio="${cardPrecio}"
                              fecha="${event.eventDate}" categoria="${cardCategoria}"
                              ubicacion="${cardUbicacion}" imagenId="${event.imageId}"
                              favorito="${not empty favoriteEventIds and favoriteEventIds.contains(event.id)}"
                              estado="${registrationStatuses[event.id]}"
                              returnTo="${currentUrl}" />
                </c:forEach>
            </div>

            <c:if test="${totalPages > 1}">
                <spring:message code="event.list.prevPage" var="prevPageLabel"/>
                <spring:message code="event.list.nextPage" var="nextPageLabel"/>
                <nav class="pager">
                    <c:if test="${currentPage > 1}">
                        <a class="pager-arrow"
                           href="<c:url value='/eventos'/>?${fn:escapeXml(filterQuery)}page=${currentPage - 1}"><c:out value="${prevPageLabel}"/></a>
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
                                   href="<c:url value='/eventos'/>?${fn:escapeXml(filterQuery)}page=${p}"><c:out value="${p}"/></a>
                            </c:otherwise>
                        </c:choose>
                    </c:forEach>

                    <c:if test="${currentPage < totalPages}">
                        <a class="pager-arrow"
                           href="<c:url value='/eventos'/>?${fn:escapeXml(filterQuery)}page=${currentPage + 1}"><c:out value="${nextPageLabel}"/></a>
                    </c:if>
                </nav>
            </c:if>
        </c:otherwise>
    </c:choose>
</main>
</jsp:body>
</paw:layout>
