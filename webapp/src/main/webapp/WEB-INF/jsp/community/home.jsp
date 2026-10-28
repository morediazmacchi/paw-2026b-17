<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="community.title">
<paw:communityShell active="home">
    <div class="community-head">
        <div>
            <h1 class="community-h1"><spring:message code="community.home.heading"/></h1>
            <p class="community-muted"><spring:message code="community.home.subheading"/></p>
        </div>
        <div class="community-seg">
            <span class="community-seg-item community-seg-item-active"><spring:message code="community.home.forYou"/></span>
            <span class="community-seg-item"><spring:message code="community.home.following"/></span>
        </div>
    </div>

    <%-- Compositor de publicacion (diseño: sin accion todavia). --%>
    <div class="community-card community-composer">
        <span class="community-avatar community-avatar-neutral"><paw:icon name="user"/></span>
        <div class="community-composer-body">
            <sec:authorize access="isAnonymous()">
                <p class="community-composer-placeholder"><spring:message code="community.composer.loginPrompt"/></p>
            </sec:authorize>
            <sec:authorize access="isAuthenticated()">
                <p class="community-composer-placeholder"><spring:message code="community.composer.placeholder"/></p>
            </sec:authorize>
            <div class="community-composer-bar">
                <span class="community-composer-event"><paw:icon name="calendar"/> <spring:message code="community.composer.linkEvent"/> <paw:icon name="chevron-down"/></span>
                <sec:authorize access="isAnonymous()">
                    <a class="btn btn-accent btn-medium" href="<c:url value='/login'/>"><spring:message code="common.nav.login"/></a>
                </sec:authorize>
                <sec:authorize access="isAuthenticated()">
                    <button type="button" class="btn btn-accent btn-medium"><spring:message code="community.composer.publish"/></button>
                </sec:authorize>
            </div>
        </div>
    </div>

    <c:forEach var="post" items="${posts}">
        <article class="community-card community-post">
            <header class="community-post-head">
                <a class="community-avatar community-avatar-lg community-avatar-${post.author.tone}" href="<c:url value='/comunidad/perfil/${post.author.username}'/>"><c:out value="${post.author.initials}"/></a>
                <div class="community-post-who">
                    <a class="community-name" href="<c:url value='/comunidad/perfil/${post.author.username}'/>"><c:out value="${post.author.name}"/></a>
                    <p class="community-muted">@<c:out value="${post.author.username}"/> · <c:out value="${post.ago}"/></p>
                </div>
                <button type="button" class="community-chip-btn"><spring:message code="community.follow"/></button>
            </header>
            <p class="community-post-text"><c:out value="${post.text}"/></p>

            <a class="community-event-link" href="<c:url value='/eventos'/>">
                <span class="community-cover community-cover-sm community-cover-${post.event.cover}"></span>
                <span class="community-event-link-text">
                    <span class="community-eyebrow community-eyebrow-accent"><spring:message code="community.post.relatedEvent"/></span>
                    <span class="community-name"><c:out value="${post.event.title}"/></span>
                    <span class="community-muted"><c:out value="${post.event.when}"/></span>
                </span>
                <paw:icon name="chevron-right"/>
            </a>

            <div class="community-post-image community-cover-${post.image}"></div>

            <footer class="community-post-actions">
                <span class="community-action"><paw:icon name="heart"/> <c:out value="${post.likes}"/></span>
                <span class="community-action"><paw:icon name="comment"/> <c:out value="${post.comments}"/></span>
                <span class="community-action community-action-right"><paw:icon name="share"/> <spring:message code="community.post.share"/></span>
            </footer>
        </article>
    </c:forEach>
</paw:communityShell>
</paw:layout>
