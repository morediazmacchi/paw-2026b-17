<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
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
<c:set var="navRawRequestUri" value="${empty requestScope['javax.servlet.forward.request_uri'] ? pageContext.request.requestURI : requestScope['javax.servlet.forward.request_uri']}"/>
<%-- getRequestURI() (y su equivalente de forward) vienen CON el context path.
     Este valor se usa como "redirect" para LocaleController/LoginController,
     que arman "redirect:" + redirect -- Spring ya antepone el context path
     solo, asi que si lo dejamos puesto queda duplicado en deploys no-root
     (ej /paw-2026b-17/paw-2026b-17/...). Se saca aca antes de usarlo. --%>
<c:set var="navRequestUri" value="${fn:substring(navRawRequestUri, fn:length(pageContext.request.contextPath), fn:length(navRawRequestUri))}"/>
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
        <sec:authorize access="isAuthenticated()">
            <%-- unreadInboxCount lo pone UnreadInboxInterceptor en todas las paginas. --%>
            <spring:message code="common.nav.inbox" var="inboxLabel"/>
            <a class="btn btn-medium" href="<c:url value='/perfil/bandeja'/>"><paw:icon name="bell"/> <c:out value="${inboxLabel}"/>
                <c:if test="${unreadInboxCount > 0}">
                    <span class="navbar-badge"><c:out value="${unreadInboxCount}"/></span>
                </c:if>
            </a>
            <%-- Mismo texto que ya usa el tab de "Mis inscripciones" en
                 accountSidebar.tag: es la misma pantalla, este es solo un
                 atajo para no tener que pasar primero por "Mi perfil". --%>
            <spring:message code="profile.tabs.registrations" var="registrationsLabel"/>
            <a class="btn btn-medium" href="<c:url value='/perfil/inscripciones'/>"><paw:icon name="calendar"/> <c:out value="${registrationsLabel}"/></a>
        </sec:authorize>

        <span class="navbar-divider"></span>

        <%-- Idioma: un solo dropdown nativo (<details>), en vez de dos pills
             ES/EN siempre visibles -- visible pero discreto (ver mockup). --%>
        <spring:message code="common.nav.switchToSpanish" var="switchToSpanishLabel"/>
        <spring:message code="common.nav.switchToEnglish" var="switchToEnglishLabel"/>
        <%-- name compartido: HTML nativo cierra los demas <details> del mismo
             grupo al abrir uno (sin JS). --%>
        <details class="navbar-dropdown" name="navbar-dropdown">
            <summary class="navbar-dropdown-trigger">
                <paw:icon name="globe"/> <span class="navbar-dropdown-value">${fn:toUpperCase(currentLanguage)}</span> <paw:icon name="chevron-down"/>
            </summary>
            <div class="navbar-dropdown-panel navbar-dropdown-panel-right">
                <a class="navbar-dropdown-item${currentLanguage == 'es' ? ' is-active' : ''}" href="${navLangEsUrl}">
                    <c:out value="${switchToSpanishLabel}"/>
                </a>
                <a class="navbar-dropdown-item${currentLanguage == 'en' ? ' is-active' : ''}" href="${navLangEnUrl}">
                    <c:out value="${switchToEnglishLabel}"/>
                </a>
            </div>
        </details>

        <sec:authorize access="isAnonymous()">
            <spring:message code="common.nav.login" var="loginLabel"/>
            <a class="btn btn-medium" href="<c:url value='/login'/>"><paw:icon name="user"/> <c:out value="${loginLabel}"/></a>
        </sec:authorize>
        <sec:authorize access="isAuthenticated()">
            <%-- Perfil/Admin/Cerrar sesion, antes 2-3 botones de texto sueltos,
                 ahora agrupados en el avatar: mismo criterio que el idioma de
                 arriba, visible pero discreto. --%>
            <spring:message code="common.nav.profile" var="profileLabel"/>
            <details class="navbar-dropdown" name="navbar-dropdown">
                <summary class="navbar-dropdown-trigger navbar-avatar-trigger" title="${profileLabel}">
                    <span class="navbar-avatar"><paw:icon name="user"/></span> <paw:icon name="chevron-down"/>
                </summary>
                <div class="navbar-dropdown-panel navbar-dropdown-panel-right">
                    <a class="navbar-dropdown-item" href="<c:url value='/perfil'/>"><paw:icon name="user"/> <c:out value="${profileLabel}"/></a>
                    <%-- Solo admins (y superadmins, que tambien tienen ROLE_ADMIN, ver
                         data.sql): mismo gate que WebAuthConfig usa para /admin/**. --%>
                    <sec:authorize access="hasRole('ADMIN')">
                        <spring:message code="admin.nav.link" var="adminLabel"/>
                        <a class="navbar-dropdown-item" href="<c:url value='/admin/reportes'/>"><paw:icon name="shield"/> <c:out value="${adminLabel}"/></a>
                    </sec:authorize>
                    <spring:message code="common.nav.logout" var="logoutLabel"/>
                    <a class="navbar-dropdown-item" href="<c:url value='/logout'/>"><paw:icon name="logout"/> <c:out value="${logoutLabel}"/></a>
                </div>
            </details>
        </sec:authorize>
    </div>
</header>
