<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Publicar evento</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>

<h1>Publicar un evento</h1>

<form action="<c:url value='/eventos/publicar'/>" method="post">
    <paw:input name="titulo" label="Titulo" type="text" />
    <paw:input name="descripcion" label="Descripcion" type="text" required="${false}" />

    <div class="form-group">
        <label for="categoriaId">Categoria</label>
        <select id="categoriaId" name="categoriaId">
            <c:forEach var="cat" items="${categories}">
                <option value="${cat.id}"><c:out value="${cat.nombre}"/></option>
            </c:forEach>
        </select>
    </div>

    <paw:input name="fechaEvento" label="Fecha y horario" type="datetime-local" />
    <paw:input name="direccion" label="Direccion" type="text" />
    <paw:input name="barrio" label="Barrio (opcional)" type="text" required="${false}" />
    <paw:input name="imagenUrl" label="URL de imagen (opcional)" type="text" required="${false}" />
    <paw:input name="precio" label="Precio (vacio = gratis)" type="number" required="${false}" />
    <paw:input name="cupoMaximo" label="Cupo maximo (vacio = sin limite)" type="number" required="${false}" />
    <paw:input name="creadorEmail" label="Tu email" type="email" />

    <paw:button text="Publicar" />
</form>

</body>
</html>
