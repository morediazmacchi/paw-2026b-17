<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="titleCode" required="false" %>
<%@ attribute name="title" required="false" %>
<%@ attribute name="head" required="false" fragment="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>

<%-- Esqueleto de TODA pagina: <html>, <head> con lo comun (charset, titulo, favicon,
     css) y <body>. Cada vista lo usa asi:

       <paw:layout titleCode="auth.login.title"> ...contenido... </paw:layout>

     titleCode: clave del mensaje para el titulo ("Plancito · <titulo>"). Si el titulo
     no sale de un mensaje sino de un dato (ej. el nombre del evento), se pasa el texto
     en "title" (se escapa solo).
     head (opcional): lo que la pagina necesita ADEMAS en el <head> (sus <script> y <link>
     propios). Se pasa con <jsp:attribute name="head">...</jsp:attribute> y entonces el
     contenido va dentro de <jsp:body>...</jsp:body>. --%>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <c:choose><c:when test="${not empty titleCode}"><spring:message code="${titleCode}"/></c:when><c:otherwise><c:out value="${title}"/></c:otherwise></c:choose></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <jsp:invoke fragment="head"/>
</head>
<body>

<jsp:doBody/>

</body>
</html>
