<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="community.activity.title">
<paw:communityShell active="activity">
    <div class="community-head">
        <div>
            <p class="community-eyebrow community-eyebrow-accent"><spring:message code="community.activity.eyebrow"/></p>
            <h1 class="community-h1"><spring:message code="community.activity.heading"/></h1>
            <p class="community-muted"><spring:message code="community.activity.subheading"/></p>
        </div>
        <button type="button" class="community-link community-link-btn"><spring:message code="community.activity.markAll"/></button>
    </div>

    <div class="community-chips community-chips-line">
        <span class="community-chip community-chip-active"><spring:message code="community.activity.filter.all"/> <strong>${items.size()}</strong></span>
        <span class="community-chip"><spring:message code="community.activity.filter.followers"/></span>
        <span class="community-chip"><spring:message code="community.activity.filter.posts"/></span>
        <span class="community-chip"><spring:message code="community.activity.filter.events"/></span>
    </div>

    <c:set var="lastGroup" value=""/>
    <c:forEach var="a" items="${items}" varStatus="st">
        <c:if test="${a.group != lastGroup}">
            <c:if test="${not st.first}"></ul></c:if>
            <h3 class="community-group-title"><span><c:out value="${a.group}"/></span></h3>
            <ul class="community-card community-activity-list">
            <c:set var="lastGroup" value="${a.group}"/>
        </c:if>
        <li class="community-activity-item${st.index < 3 ? ' community-activity-item-unread' : ''}">
            <span class="community-activity-kind community-activity-kind-${a.kind}">
                <c:choose>
                    <c:when test="${a.kind == 'follow'}"><paw:icon name="user-plus"/></c:when>
                    <c:when test="${a.kind == 'like'}"><paw:icon name="heart-filled"/></c:when>
                    <c:when test="${a.kind == 'comment'}"><paw:icon name="comment"/></c:when>
                    <c:otherwise><paw:icon name="calendar"/></c:otherwise>
                </c:choose>
            </span>
            <a class="community-avatar community-avatar-lg community-avatar-${a.actor.tone}" href="<c:url value='/comunidad/perfil/${a.actor.username}'/>"><c:out value="${a.actor.initials}"/></a>
            <div class="community-activity-text">
                <p><strong><c:out value="${a.actor.name}"/></strong> <c:out value="${a.text}"/></p>
                <p class="community-muted"><c:out value="${a.ago}"/></p>
            </div>
            <c:choose>
                <c:when test="${a.action == 'follow'}"><button type="button" class="community-chip-btn"><spring:message code="community.follow"/></button></c:when>
                <c:when test="${a.action == 'following'}"><button type="button" class="community-chip-btn community-chip-btn-on"><spring:message code="community.following"/></button></c:when>
                <c:when test="${a.action == 'reply'}"><button type="button" class="community-chip-btn"><spring:message code="community.activity.reply"/></button></c:when>
                <c:when test="${not empty a.thumb}"><span class="community-cover community-cover-thumb community-cover-${a.thumb}"></span></c:when>
            </c:choose>
        </li>
    </c:forEach>
    </ul>
</paw:communityShell>
</paw:layout>
