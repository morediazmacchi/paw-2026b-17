<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>

<%-- Barra superior, igual en toda la app. El body (opcional) son los botones
     propios de cada vista (volver, + Añadir evento, etc.), que se dibujan antes
     del pill de login/perfil: ese es siempre el mismo, no cada vista lo repite. --%>
<%-- Idioma (PAW20-75): a donde volver despues de tocar ES/EN, la misma pagina
     donde esta parado. OJO: pageContext.request.requestURI da la ruta del
     forward interno (/WEB-INF/jsp/...) cuando el controller llego aca con un
     forward en vez de una vista propia; la URL de verdad, si la hubo, esta en
     este atributo que el forward deja seteado (spec de Servlet, mismo criterio
     que list.jsp usa para el returnTo del corazon de favoritos). --%>
<c:set var="navRequestUri" value="${empty requestScope['javax.servlet.forward.request_uri'] ? pageContext.request.requestURI : requestScope['javax.servlet.forward.request_uri']}"/>
<c:set var="navQueryString" value="${empty requestScope['javax.servlet.forward.query_string'] ? pageContext.request.queryString : requestScope['javax.servlet.forward.query_string']}"/>
<c:set var="navCurrentUrl" value="${navRequestUri}${empty navQueryString ? '' : '?'}${navQueryString}"/>
<c:url value="/idioma" var="navLangEsUrl">
    <c:param name="lang" value="es"/>
    <c:param name="redirect" value="${navCurrentUrl}"/>
</c:url>
<c:url value="/idioma" var="navLangEnUrl">
    <c:param name="lang" value="en"/>
    <c:param name="redirect" value="${navCurrentUrl}"/>
</c:url>

<header class="navbar">
    <a class="logo" href="<c:url value='/eventos'/>">p<span class="logo-dot">.</span> plancito</a>
    <div class="navbar-actions">
        <jsp:doBody/>
        <spring:message code="common.nav.switchToSpanish" var="switchToSpanishLabel"/>
        <spring:message code="common.nav.switchToEnglish" var="switchToEnglishLabel"/>
        <div class="navbar-lang">
            <a class="pill navbar-lang-option${currentLanguage == 'es' ? ' pill-active' : ''}"
               href="${navLangEsUrl}" title="${switchToSpanishLabel}">ES</a>
            <a class="pill navbar-lang-option${currentLanguage == 'en' ? ' pill-active' : ''}"
               href="${navLangEnUrl}" title="${switchToEnglishLabel}">EN</a>
        </div>
        <sec:authorize access="isAnonymous()">
            <spring:message code="common.nav.login" var="loginLabel"/>
            <a class="btn btn-medium" href="<c:url value='/login'/>"><paw:icon name="user"/> <c:out value="${loginLabel}"/></a>
        </sec:authorize>
        <sec:authorize access="isAuthenticated()">
            <%-- unreadInboxCount lo pone UnreadInboxInterceptor en todas las paginas. --%>
            <spring:message code="common.nav.inbox" var="inboxLabel"/>
            <a class="btn btn-medium" href="<c:url value='/perfil/bandeja'/>"><paw:icon name="bell"/> <c:out value="${inboxLabel}"/>
                <c:if test="${unreadInboxCount > 0}">
                    <span class="navbar-badge"><c:out value="${unreadInboxCount}"/></span>
                </c:if>
            </a>
            <%-- Mismo texto/icono que ya usa el tab de "Mis inscripciones" en
                 accountSidebar.tag: es la misma pantalla, este es solo un
                 atajo para no tener que pasar primero por "Mi perfil". --%>
            <spring:message code="profile.tabs.registrations" var="registrationsLabel"/>
            <a class="btn btn-medium" href="<c:url value='/perfil/inscripciones'/>"><paw:icon name="check-square"/> <c:out value="${registrationsLabel}"/></a>
            <spring:message code="common.nav.profile" var="profileLabel"/>
            <a class="btn btn-medium" href="<c:url value='/perfil'/>"><paw:icon name="user"/> <c:out value="${profileLabel}"/></a>
        </sec:authorize>
    </div>
</header>
