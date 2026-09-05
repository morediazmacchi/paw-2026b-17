<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; Publicar evento</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>

<header class="navbar">
    <a class="logo" href="<c:url value='/eventos'/>">p<span class="logo-dot">.</span> plancito</a>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>">&larr; Volver a eventos</a>
</header>

<main class="page page-narrow">
    <div class="form-card">
        <h1>Publicar un evento</h1>

        <c:if test="${not empty imageError}">
            <p class="alert"><c:out value="${imageError}"/></p>
        </c:if>

        <form action="<c:url value='/eventos/publicar'/>" method="post" enctype="multipart/form-data" novalidate="novalidate">
            <paw:input name="titulo" label="Título" type="text"
                       value="${eventForm.titulo}" errorMessage="${fieldErrors.titulo}" />
            <paw:input name="descripcion" label="Descripción" type="text" required="${false}"
                       value="${eventForm.descripcion}" errorMessage="${fieldErrors.descripcion}" />

            <div class="form-group">
                <label for="categoriaId">Categoría</label>
                <select id="categoriaId" name="categoriaId">
                    <c:forEach var="cat" items="${categories}">
                        <option value="${cat.id}" ${cat.id == eventForm.categoriaId ? 'selected' : ''}>
                            <c:out value="${cat.name}"/>
                        </option>
                    </c:forEach>
                </select>
                <c:if test="${not empty fieldErrors.categoriaId}">
                    <span class="error-message"><c:out value="${fieldErrors.categoriaId}"/></span>
                </c:if>
            </div>

            <paw:input name="fechaEvento" label="Fecha y horario" type="datetime-local"
                       value="${eventForm.fechaEvento}" errorMessage="${fieldErrors.fechaEvento}" />
            <paw:input name="direccion" label="Dirección" type="text"
                       value="${eventForm.direccion}" errorMessage="${fieldErrors.direccion}" />
            <div class="form-group">
                <label for="barrio">Barrio (opcional)</label>
                <select id="barrio" name="barrio">
                    <option value="">Sin especificar</option>
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
            <paw:input name="precio" label="Precio (si es evento gratuito, colocar 0)" type="number" min="0"
                       value="${eventForm.precio}" errorMessage="${fieldErrors.precio}" />
            <paw:input name="cupoMaximo" label="Cupo máximo (si no hay límite, colocar 0)" type="number" min="0"
                       value="${eventForm.cupoMaximo}" errorMessage="${fieldErrors.cupoMaximo}" />
            <paw:input name="creadorEmail" label="Tu email" type="email"
                       value="${eventForm.creadorEmail}" errorMessage="${fieldErrors.creadorEmail}" />

            <div class="form-group">
                <label for="image">Imagen (opcional, hasta 5 MB)</label>
                <input type="file" id="image" name="image" accept="image/*" />
            </div>

            <paw:button text="Publicar" cssClass="btn-accent" />
        </form>
    </div>
</main>

</body>
</html>
