<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; No encontrado</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>

<header class="navbar">
    <a class="logo" href="<c:url value='/eventos'/>">p<span class="logo-dot">.</span> plancito</a>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>">&larr; Volver a eventos</a>
</header>

<main class="page page-narrow">
    <h1>Ese evento no existe</h1>
    <p class="empty-state">
        Puede que lo hayan dado de baja, o que el link este mal escrito.
    </p>
    <a class="btn btn-medium btn-accent" href="<c:url value='/eventos'/>">Ver todos los eventos</a>
</main>

</body>
</html>
