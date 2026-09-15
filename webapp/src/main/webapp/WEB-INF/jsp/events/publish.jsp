<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="event.publish.title"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
    <script src="<c:url value='/js/quick-options.js'/>"></script>
</head>
<body>

<spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
<header class="navbar">
    <a class="logo" href="<c:url value='/eventos'/>">p<span class="logo-dot">.</span> plancito</a>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>">&larr; <c:out value="${backToEventsLabel}"/></a>
</header>

<main class="page page-narrow">
    <div class="form-card">
        <h1><spring:message code="event.publish.heading"/></h1>

        <c:if test="${not empty imageError}">
            <p class="alert"><c:out value="${imageError}"/></p>
        </c:if>

        <spring:message code="event.publish.titleLabel" var="titleLabel"/>
        <spring:message code="event.publish.descriptionLabel" var="descriptionLabel"/>
        <spring:message code="event.publish.categoryLabel" var="categoryLabel"/>
        <spring:message code="event.publish.dateLabel" var="dateLabel"/>
        <spring:message code="event.publish.addressLabel" var="addressLabel"/>
        <spring:message code="event.publish.neighborhoodLabel" var="neighborhoodLabel"/>
        <spring:message code="event.publish.neighborhoodNone" var="neighborhoodNoneLabel"/>
        <spring:message code="event.publish.priceLabel" var="priceLabel"/>
        <spring:message code="event.publish.capacityLabel" var="capacityLabel"/>
        <spring:message code="event.publish.emailLabel" var="emailLabel"/>
        <spring:message code="event.publish.imageLabel" var="imageLabel"/>
        <spring:message code="event.publish.freeOption" var="freeOptionLabel"/>
        <spring:message code="event.publish.noLimitOption" var="noLimitOptionLabel"/>
        <spring:message code="event.publish.submitButton" var="submitButtonLabel"/>
        <form action="<c:url value='/eventos/publicar'/>" method="post" enctype="multipart/form-data" novalidate="novalidate">
            <paw:input name="titulo" label="${titleLabel}" type="text"
                       value="${eventForm.titulo}" errorMessage="${fieldErrors.titulo}" />
            <paw:input name="descripcion" label="${descriptionLabel}" type="text" required="${false}"
                       value="${eventForm.descripcion}" errorMessage="${fieldErrors.descripcion}" />

            <div class="form-group">
                <label for="categoriaId"><c:out value="${categoryLabel}"/></label>
                <select id="categoriaId" name="categoriaId">
                    <c:forEach var="cat" items="${categories}">
                        <option value="${cat.id}" ${cat.id == eventForm.categoriaId ? 'selected' : ''}>
                            <spring:message code="category.${cat.name}" text="${cat.name}" htmlEscape="true"/>
                        </option>
                    </c:forEach>
                </select>
                <c:if test="${not empty fieldErrors.categoriaId}">
                    <span class="error-message"><c:out value="${fieldErrors.categoriaId}"/></span>
                </c:if>
            </div>

            <paw:input name="fechaEvento" label="${dateLabel}" type="datetime-local"
                       value="${eventForm.fechaEvento}" errorMessage="${fieldErrors.fechaEvento}" />
            <paw:input name="direccion" label="${addressLabel}" type="text"
                       value="${eventForm.direccion}" errorMessage="${fieldErrors.direccion}" />
            <div class="form-group">
                <label for="barrio"><c:out value="${neighborhoodLabel}"/></label>
                <select id="barrio" name="barrio">
                    <option value=""><c:out value="${neighborhoodNoneLabel}"/></option>
                    <c:forEach var="b" items="${barrios}">
                        <option value="${b}" ${b.name() == eventForm.barrio ? 'selected' : ''}>
                            <c:out value="${b.displayName}"/>
                        </option>
                    </c:forEach>
                </select>
                <c:if test="${not empty fieldErrors.barrio}">
                    <span class="error-message"><c:out value="${fieldErrors.barrio}"/></span>
                </c:if>
            </div>
            <paw:input name="precio" label="${priceLabel}" type="number" min="0"
                       value="${eventForm.precio}" errorMessage="${fieldErrors.precio}"
                       toggleText="${freeOptionLabel}" />
            <paw:input name="cupoMaximo" label="${capacityLabel}" type="number" min="0"
                       value="${eventForm.cupoMaximo}" errorMessage="${fieldErrors.cupoMaximo}"
                       toggleText="${noLimitOptionLabel}" />

            <paw:input name="creadorEmail" label="${emailLabel}" type="email"
                       value="${eventForm.creadorEmail}" errorMessage="${fieldErrors.creadorEmail}" />

            <div class="form-group">
                <label for="image"><c:out value="${imageLabel}"/></label>
                <input type="file" id="image" name="image" accept="image/*" />
            </div>

            <paw:button text="${submitButtonLabel}" cssClass="btn-accent" />
        </form>
    </div>
</main>

</body>
</html>
