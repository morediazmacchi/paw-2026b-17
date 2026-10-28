<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="active" required="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>

<%-- Marco de toda la seccion Comunidad: barra superior, menu izquierdo, contenido
     (el body) y columna derecha de "Personas para descubrir". Hoy con datos de
     ejemplo (CommunityController): suggested, weekPlan y activityCount vienen del model. --%>
<header class="community-topbar">
    <div class="community-topbar-brand">
        <a class="logo" href="<c:url value='/eventos'/>">p<span class="logo-dot">.</span> plancito</a>
        <span class="community-topbar-sep">/</span>
        <span class="community-topbar-title"><spring:message code="community.nav.title"/></span>
    </div>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>"><paw:icon name="chevron-left"/> <spring:message code="community.nav.back"/></a>
</header>

<div class="community-layout">
    <aside class="community-side">
        <p class="community-eyebrow"><spring:message code="community.side.heading"/></p>
        <nav class="community-menu">
            <a class="community-menu-link${active == 'home' ? ' community-menu-link-active' : ''}" href="<c:url value='/comunidad'/>">
                <paw:icon name="home"/> <spring:message code="community.side.home"/>
            </a>
            <a class="community-menu-link${active == 'discover' ? ' community-menu-link-active' : ''}" href="<c:url value='/comunidad/descubrir'/>">
                <paw:icon name="search"/> <spring:message code="community.side.discover"/>
            </a>
            <a class="community-menu-link${active == 'activity' ? ' community-menu-link-active' : ''}" href="<c:url value='/comunidad/actividad'/>">
                <paw:icon name="heart"/> <spring:message code="community.side.activity"/>
                <span class="community-count"><c:out value="${activityCount}"/></span>
            </a>
        </nav>

        <div class="community-plan">
            <p class="community-eyebrow community-eyebrow-accent"><spring:message code="community.plan.heading"/></p>
            <p class="community-plan-title"><c:out value="${weekPlan.title}"/></p>
            <p class="community-muted"><c:out value="${weekPlan.when}"/></p>
            <a class="community-link" href="<c:url value='/eventos'/>"><spring:message code="community.plan.view"/></a>
        </div>
    </aside>

    <main class="community-main">
        <jsp:doBody/>
    </main>

    <aside class="community-rail">
        <h2 class="community-rail-title"><spring:message code="community.rail.title"/></h2>
        <p class="community-eyebrow"><spring:message code="community.rail.city"/></p>
        <ul class="community-suggestions">
            <c:forEach var="p" items="${suggested}">
                <li class="community-suggestion">
                    <a class="community-avatar community-avatar-${p.tone}" href="<c:url value='/comunidad/perfil/${p.username}'/>"><c:out value="${p.initials}"/></a>
                    <div class="community-suggestion-text">
                        <a class="community-name" href="<c:url value='/comunidad/perfil/${p.username}'/>"><c:out value="${p.name}"/></a>
                        <p class="community-muted"><c:out value="${p.neighborhood}"/> · <c:out value="${p.followers}"/> <spring:message code="community.followers"/></p>
                    </div>
                    <button type="button" class="community-icon-btn${p.following ? ' community-icon-btn-on' : ''}"
                            title="<spring:message code='${p.following ? "community.following" : "community.follow"}'/>">
                        <c:choose><c:when test="${p.following}">&#10003;</c:when><c:otherwise>+</c:otherwise></c:choose>
                    </button>
                </li>
            </c:forEach>
        </ul>
        <div class="community-note">
            <p class="community-name"><spring:message code="community.rail.noteTitle"/></p>
            <p class="community-muted"><spring:message code="community.rail.noteBody"/></p>
        </div>
    </aside>
</div>
