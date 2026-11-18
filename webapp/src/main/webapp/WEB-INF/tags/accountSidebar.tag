<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="active" required="true" %>
<%@ attribute name="registrationsCount" required="false" type="java.lang.Long" %>
<%@ attribute name="favoritesCount" required="false" type="java.lang.Long" %>
<%@ attribute name="eventsCount" required="false" type="java.lang.Long" %>
<%@ attribute name="invitationsCount" required="false" type="java.lang.Long" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>

<%-- Barra lateral de "Mi perfil": identidad + navegacion entre subpaginas.
     "user" no es un atributo del tag: lo toma del request (ya lo pone
     ProfileController en el model de las tres paginas que la usan). Cuando
     se agreguen Mis eventos/Favoritos, sus links van aca y aparecen en todas
     las pantallas de la cuenta a la vez. --%>
<%-- Al volver con "Atras" a una pantalla de la cuenta, que no se vea una copia vieja (ver fresh-on-back.js). --%>
<script src="<c:url value='/js/fresh-on-back.js'/>" defer></script>
<aside class="account-sidebar">
    <div class="account-sidebar-identity">
        <span class="profile-avatar"><c:out value="${fn:toUpperCase(fn:substring(user.name, 0, 1))}"/></span>
        <div>
            <p class="account-sidebar-name"><c:out value="${user.name}"/></p>
            <p class="account-sidebar-email"><c:out value="${user.email}"/></p>
        </div>
    </div>

    <nav class="account-sidebar-nav">
        <spring:message code="profile.tabs.data" var="dataLabel"/>
        <a class="account-sidebar-link ${active == 'data' ? 'account-sidebar-link-active' : ''}"
           href="<c:url value='/perfil'/>">
            <paw:icon name="user"/> <c:out value="${dataLabel}"/>
        </a>
        <spring:message code="profile.public.viewMine" var="publicProfileLabel"/>
        <a class="account-sidebar-link" href="<c:url value='/usuarios/${user.id}'/>">
            <paw:icon name="people"/> <c:out value="${publicProfileLabel}"/>
        </a>
        <spring:message code="profile.tabs.registrations" var="registrationsLabel"/>
        <a class="account-sidebar-link ${active == 'registrations' ? 'account-sidebar-link-active' : ''}"
           href="<c:url value='/perfil/inscripciones'/>">
            <paw:icon name="check-square"/> <c:out value="${registrationsLabel}"/>
            <c:if test="${not empty registrationsCount}">
                <span class="account-sidebar-badge"><c:out value="${registrationsCount}"/></span>
            </c:if>
        </a>
        <spring:message code="profile.tabs.favorites" var="favoritesLabel"/>
        <a class="account-sidebar-link ${active == 'favorites' ? 'account-sidebar-link-active' : ''}"
           href="<c:url value='/perfil/favoritos'/>">
            <paw:icon name="heart"/> <c:out value="${favoritesLabel}"/>
            <c:if test="${not empty favoritesCount}">
                <span class="account-sidebar-badge"><c:out value="${favoritesCount}"/></span>
            </c:if>
        </a>
        <spring:message code="profile.tabs.invitations" var="invitationsLabel"/>
        <a class="account-sidebar-link ${active == 'invitations' ? 'account-sidebar-link-active' : ''}"
           href="<c:url value='/perfil/invitaciones'/>">
            <paw:icon name="mail"/> <c:out value="${invitationsLabel}"/>
            <c:if test="${not empty invitationsCount}">
                <span class="account-sidebar-badge"><c:out value="${invitationsCount}"/></span>
            </c:if>
        </a>
        <spring:message code="profile.tabs.lists" var="listsLabel"/>
        <a class="account-sidebar-link ${active == 'lists' ? 'account-sidebar-link-active' : ''}"
           href="<c:url value='/perfil/listas'/>">
            <paw:icon name="search"/> <c:out value="${listsLabel}"/>
        </a>
        <spring:message code="profile.tabs.inbox" var="inboxLabel"/>
        <a class="account-sidebar-link ${active == 'inbox' ? 'account-sidebar-link-active' : ''}"
           href="<c:url value='/perfil/bandeja'/>">
            <paw:icon name="bell"/> <c:out value="${inboxLabel}"/>
            <c:if test="${unreadInboxCount > 0}">
                <span class="account-sidebar-badge"><c:out value="${unreadInboxCount}"/></span>
            </c:if>
        </a>
        <spring:message code="profile.tabs.events" var="eventsLabel"/>
        <a class="account-sidebar-link ${active == 'events' ? 'account-sidebar-link-active' : ''}"
           href="<c:url value='/perfil/eventos'/>">
            <paw:icon name="calendar"/> <c:out value="${eventsLabel}"/>
            <c:if test="${not empty eventsCount}">
                <span class="account-sidebar-badge"><c:out value="${eventsCount}"/></span>
            </c:if>
        </a>
    </nav>

    <spring:message code="common.nav.logout" var="logoutLabel"/>
    <form class="account-sidebar-logout-form" action="<c:url value='/logout'/>" method="post">
        <button type="submit" class="account-sidebar-logout">
            <paw:icon name="logout"/> <c:out value="${logoutLabel}"/>
        </button>
    </form>
</aside>
