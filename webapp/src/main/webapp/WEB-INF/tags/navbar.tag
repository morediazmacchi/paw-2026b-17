<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>

<%-- Barra superior, igual en toda la app. El body (opcional) son los botones
     propios de cada vista (volver, + Añadir evento, etc.), que se dibujan antes
     del pill de login/perfil: ese es siempre el mismo, no cada vista lo repite. --%>
<header class="navbar">
    <a class="logo" href="<c:url value='/eventos'/>">p<span class="logo-dot">.</span> plancito</a>
    <div class="navbar-actions">
        <jsp:doBody/>
        <sec:authorize access="isAnonymous()">
            <spring:message code="common.nav.login" var="loginLabel"/>
            <a class="btn btn-medium" href="<c:url value='/login'/>"><paw:icon name="user"/> <c:out value="${loginLabel}"/></a>
        </sec:authorize>
        <sec:authorize access="isAuthenticated()">
            <spring:message code="common.nav.profile" var="profileLabel"/>
            <a class="btn btn-medium" href="<c:url value='/perfil'/>"><paw:icon name="user"/> <c:out value="${profileLabel}"/></a>
        </sec:authorize>
    </div>
</header>
