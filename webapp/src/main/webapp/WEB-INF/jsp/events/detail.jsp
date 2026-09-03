<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <c:out value="${event.titulo}"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>

<header class="navbar">
    <a class="logo" href="<c:url value='/eventos'/>">p<span class="logo-dot">.</span> plancito</a>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>">&larr; Volver a eventos</a>
</header>

<main class="page page-narrow">
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

    <article class="detail">
        <div class="detail-placeholder">
            <span class="detail-precio">
                <c:choose>
                    <c:when test="${event.precio == 0}">Gratis</c:when>
                    <c:otherwise>$<fmt:formatNumber value="${event.precio}" type="number" minFractionDigits="0" maxFractionDigits="0" /></c:otherwise>
                </c:choose>
            </span>
        </div>

        <c:if test="${not empty event.categoriaNombre}">
            <span class="card-categoria"><c:out value="${event.categoriaNombre}"/></span>
        </c:if>
        <h1 class="detail-titulo"><c:out value="${event.titulo}"/></h1>
        <p class="detail-meta">
            <fmt:formatDate value="${event.fechaEvento}" pattern="EEEE dd/MM/yyyy · HH:mm" />hs
            &middot; <c:out value="${event.direccion}"/><c:if test="${not empty event.barrio}"> (<c:out value="${event.barrio}"/>)</c:if>
        </p>
        <c:if test="${not empty event.cupoMaximo}">
            <p class="detail-meta">Cupo máximo: <c:out value="${event.cupoMaximo}"/> personas</p>
        </c:if>

        <c:if test="${not empty event.descripcion}">
            <p class="detail-descripcion"><c:out value="${event.descripcion}"/></p>
        </c:if>

        <section class="rsvp-box">
            <h2>Quiero ir</h2>
            <form action="<c:url value='/eventos/${event.id}/quiero-ir'/>" method="post">
                <paw:input name="email" label="Tu email" type="email" />
                <paw:input name="nombre" label="Tu nombre (opcional)" type="text" required="${false}" />
                <paw:button text="Quiero ir" cssClass="btn-accent" />
            </form>
        </section>
    </article>
</main>

</body>
</html>
