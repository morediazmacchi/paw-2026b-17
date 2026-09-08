<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="error.serverError.title"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>

<spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
<header class="navbar">
    <a class="logo" href="<c:url value='/eventos'/>">p<span class="logo-dot">.</span> plancito</a>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>">&larr; <c:out value="${backToEventsLabel}"/></a>
</header>

<main class="page page-narrow">
    <h1><spring:message code="error.serverError.heading"/></h1>
    <p class="empty-state">
        <spring:message code="error.serverError.message"/>
    </p>
    <a class="btn btn-medium btn-accent" href="<c:url value='/eventos'/>"><spring:message code="error.serverError.button"/></a>
</main>

</body>
</html>
