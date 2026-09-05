<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; Algo salio mal</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>

<header class="navbar">
    <a class="logo" href="<c:url value='/eventos'/>">p<span class="logo-dot">.</span> plancito</a>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>">&larr; Volver a eventos</a>
</header>

<main class="page page-narrow">
    <h1>Algo salio mal</h1>
    <p class="empty-state">
        Tuvimos un problema procesando tu pedido. Proba de nuevo en un rato.
    </p>
    <a class="btn btn-medium btn-accent" href="<c:url value='/eventos'/>">Volver a eventos</a>
</main>

</body>
</html>
