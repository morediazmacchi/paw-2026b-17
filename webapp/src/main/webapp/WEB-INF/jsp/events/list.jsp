<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Eventos</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>

<h1>Eventos</h1>

<c:if test="${not empty rsvp}">
    <c:choose>
        <c:when test="${rsvp == 'confirmada'}">
            <p class="alert">Listo, quedaste confirmado.</p>
        </c:when>
        <c:when test="${rsvp == 'espera'}">
            <p class="alert">El evento esta lleno, quedaste en lista de espera.</p>
        </c:when>
    </c:choose>
</c:if>

<form action="<c:url value='/eventos'/>" method="get">
    <label for="categoria">Categoria</label>
    <select id="categoria" name="categoria" onchange="this.form.submit()">
        <option value="">Todas</option>
        <c:forEach var="cat" items="${categories}">
            <option value="${cat.id}" ${cat.id == selectedCategoria ? 'selected' : ''}><c:out value="${cat.nombre}"/></option>
        </c:forEach>
    </select>
</form>

<c:forEach var="event" items="${events}">
    <c:set var="cardPrecio" value="${event.precio == 0 ? null : event.precio}" />
    <paw:card nombre="${event.titulo}" precio="${cardPrecio}" fecha="${event.fechaEvento}" />
    <p>
        <fmt:formatDate value="${event.fechaEvento}" pattern="HH:mm" /> hs
        - <c:out value="${event.direccion}"/><c:if test="${not empty event.barrio}"> (<c:out value="${event.barrio}"/>)</c:if>
        - <c:out value="${event.categoriaNombre}"/>
    </p>
    <c:if test="${not empty event.imagenUrl}">
        <img src="${event.imagenUrl}" alt="" width="200" />
    </c:if>
    <p><c:out value="${event.descripcion}"/></p>

    <form action="<c:url value='/eventos/${event.id}/quiero-ir'/>" method="post">
        <paw:input name="email" label="Tu email" type="email" />
        <paw:input name="nombre" label="Tu nombre (opcional)" type="text" required="${false}" />
        <paw:button text="Quiero ir" />
    </form>
    <hr/>
</c:forEach>

<p><a href="<c:url value='/eventos/publicar'/>">Publicar un evento</a></p>

</body>
</html>
