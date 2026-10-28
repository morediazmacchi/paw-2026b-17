<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="community.discover.title">
<paw:communityShell active="discover">
    <p class="community-eyebrow community-eyebrow-accent"><spring:message code="community.discover.eyebrow"/></p>
    <h1 class="community-h1"><spring:message code="community.discover.heading"/></h1>
    <p class="community-muted"><spring:message code="community.discover.subheading"/></p>

    <div class="community-search">
        <paw:icon name="search"/>
        <input type="text" placeholder="<spring:message code='community.discover.searchPlaceholder'/>" disabled/>
    </div>

    <div class="community-chips">
        <span class="community-chip community-chip-active"><spring:message code="community.discover.filter.forYou"/></span>
        <span class="community-chip"><spring:message code="community.discover.filter.organizers"/></span>
        <span class="community-chip"><spring:message code="community.discover.filter.music"/></span>
        <span class="community-chip"><spring:message code="community.discover.filter.food"/></span>
        <span class="community-chip"><spring:message code="community.discover.filter.art"/></span>
    </div>

    <div class="community-list-head">
        <h2 class="community-h2"><c:out value="${profiles.size()}"/> <spring:message code="community.discover.count"/></h2>
        <span class="community-muted"><spring:message code="community.discover.basedOn"/></span>
    </div>

    <div class="community-profile-grid">
        <c:forEach var="p" items="${profiles}">
            <article class="community-card community-profile-card">
                <div class="community-profile-banner community-banner-${p.tone}"></div>
                <div class="community-profile-card-body">
                    <div class="community-profile-card-top">
                        <a class="community-avatar community-avatar-xl community-avatar-${p.tone}" href="<c:url value='/comunidad/perfil/${p.username}'/>"><c:out value="${p.initials}"/></a>
                        <button type="button" class="community-chip-btn${p.following ? ' community-chip-btn-on' : ''}">
                            <spring:message code="${p.following ? 'community.following' : 'community.follow'}"/>
                        </button>
                    </div>
                    <a class="community-name community-name-lg" href="<c:url value='/comunidad/perfil/${p.username}'/>"><c:out value="${p.name}"/></a>
                    <p class="community-muted">@<c:out value="${p.username}"/> · <c:out value="${p.neighborhood}"/></p>
                    <p class="community-bio"><c:out value="${p.bio}"/></p>
                    <p class="community-profile-stats">
                        <span><strong><c:out value="${p.followers}"/></strong> <spring:message code="community.followers"/></span>
                        <span><strong><c:out value="${p.events}"/></strong> <spring:message code="community.events"/></span>
                    </p>
                </div>
            </article>
        </c:forEach>
    </div>
</paw:communityShell>
</paw:layout>
