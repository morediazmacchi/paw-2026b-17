<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout title="${person.name}">
<paw:communityShell active="discover">
    <section class="community-card community-profile">
        <div class="community-profile-cover community-banner-${person.tone}"></div>
        <div class="community-profile-top">
            <span class="community-avatar community-avatar-xxl community-avatar-${person.tone}"><c:out value="${person.initials}"/></span>
            <div class="community-profile-actions">
                <button type="button" class="btn btn-medium"><paw:icon name="comment"/> <spring:message code="community.profile.message"/></button>
                <button type="button" class="btn btn-accent btn-medium"><spring:message code="community.follow"/></button>
            </div>
        </div>

        <div class="community-profile-info">
            <div>
                <h1 class="community-h1"><c:out value="${person.name}"/></h1>
                <p class="community-muted">@<c:out value="${person.username}"/> · <c:out value="${person.neighborhood}"/>, Buenos Aires</p>
                <p class="community-bio"><c:out value="${person.bio}"/></p>
            </div>
            <dl class="community-stats">
                <div><dt><c:out value="${person.postsCount}"/></dt><dd><spring:message code="community.profile.posts"/></dd></div>
                <div><dt><c:out value="${person.followers}"/></dt><dd><spring:message code="community.followers"/></dd></div>
                <div><dt><c:out value="${person.followingCount}"/></dt><dd><spring:message code="community.profile.followingCount"/></dd></div>
                <div><dt><c:out value="${person.events}"/></dt><dd><spring:message code="community.profile.organizedCount"/></dd></div>
            </dl>
        </div>

        <%-- Tabs sin JS: los dos paneles estan en la pagina y se alternan con :target... --%>
        <div class="community-tabs">
            <a class="community-tab community-tab-active" href="#publicaciones"><paw:icon name="grid"/> <spring:message code="community.profile.tabPosts"/> <span class="community-count"><c:out value="${person.postsCount}"/></span></a>
            <a class="community-tab" href="#eventos"><paw:icon name="calendar"/> <spring:message code="community.profile.tabEvents"/> <span class="community-count"><c:out value="${person.events}"/></span></a>
        </div>

        <div class="community-tab-panels">
            <section id="publicaciones" class="community-tab-panel community-tab-panel-posts">
                <c:forEach var="post" items="${posts}">
                    <div class="community-post-tile">
                        <div class="community-post-tile-img community-cover-${post.image}"></div>
                        <p class="community-post-text"><c:out value="${post.text}"/></p>
                    </div>
                </c:forEach>
            </section>

            <section id="eventos" class="community-tab-panel community-tab-panel-events">
                <div class="community-list-head">
                    <div>
                        <h2 class="community-h2"><spring:message code="community.profile.organizedBy"><spring:argument value="${person.firstName}"/></spring:message></h2>
                        <p class="community-muted"><spring:message code="community.profile.organizedSub"/></p>
                    </div>
                    <div class="community-subscribe">
                        <%-- Diseño: suscribirse a las notificaciones de eventos nuevos de esta persona. --%>
                        <button type="button" class="btn btn-medium community-subscribe-btn${subscribed ? ' community-subscribe-btn-on' : ''}">
                            <paw:icon name="bell"/>
                            <spring:message code="${subscribed ? 'community.subscribe.on' : 'community.subscribe.off'}"/>
                        </button>
                        <p class="community-muted community-subscribe-hint"><spring:message code="community.subscribe.hint"><spring:argument value="${person.firstName}"/></spring:message></p>
                    </div>
                </div>

                <div class="community-event-grid">
                    <c:forEach var="e" items="${organized}">
                        <a class="community-card community-event-card" href="<c:url value='/eventos'/>">
                            <span class="community-event-cover community-cover-${e.cover}">
                                <span class="community-price"><c:out value="${e.price}"/></span>
                            </span>
                            <span class="community-event-body">
                                <span class="community-eyebrow community-eyebrow-accent"><c:out value="${e.when}"/></span>
                                <span class="community-name"><c:out value="${e.title}"/></span>
                                <span class="community-muted"><c:out value="${e.place}"/></span>
                                <span class="community-link"><spring:message code="community.profile.viewEvent"/> <paw:icon name="chevron-right"/></span>
                            </span>
                        </a>
                    </c:forEach>
                </div>
            </section>
        </div>
    </section>
</paw:communityShell>
</paw:layout>
