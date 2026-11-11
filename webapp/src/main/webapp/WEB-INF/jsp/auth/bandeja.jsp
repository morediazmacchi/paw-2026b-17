<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="inbox.title">
<paw:navbar backToEvents="true"/>

<div class="account-layout">
    <paw:accountSidebar active="inbox" registrationsCount="${accountCounts.registrations}" favoritesCount="${accountCounts.favorites}" eventsCount="${accountCounts.events}" invitationsCount="${accountCounts.invitations}"/>

    <main class="account-main">
        <h1><spring:message code="inbox.heading"/></h1>
        <p class="auth-subheading"><spring:message code="inbox.subheading"/></p>

        <%-- Arriba: un circulo por grupo de chat de eventos a los que te uniste. --%>
        <section class="inbox-groups">
            <h2 class="inbox-section-title"><spring:message code="inbox.groupsHeading"/></h2>
            <c:choose>
                <c:when test="${empty chatGroups}">
                    <p class="empty-state inbox-groups-empty"><spring:message code="inbox.groupsEmpty"/></p>
                </c:when>
                <c:otherwise>
                    <ul class="chat-groups">
                        <c:forEach var="group" items="${chatGroups}">
                            <li>
                                <a class="chat-group" href="<c:url value='/eventos/${group.eventId}/chat'/>">
                                    <span class="chat-group-circle${group.unread ? ' chat-group-circle-unread' : ''}">
                                        <c:choose>
                                            <c:when test="${not empty group.imageId}">
                                                <img src="<c:url value='/image/${group.imageId}'/>" alt=""/>
                                            </c:when>
                                            <c:otherwise>
                                                <span class="chat-group-initial"><c:out value="${fn:toUpperCase(fn:substring(group.eventTitle, 0, 1))}"/></span>
                                            </c:otherwise>
                                        </c:choose>
                                        <c:if test="${group.unread}">
                                            <spring:message code="inbox.groupUnread" var="groupUnreadLabel" htmlEscape="true"/>
                                            <span class="chat-group-badge" title="${groupUnreadLabel}"><c:out value="${group.unreadCount > 99 ? '99+' : group.unreadCount}"/></span>
                                        </c:if>
                                    </span>
                                    <span class="chat-group-name"><c:out value="${group.eventTitle}"/></span>
                                </a>
                            </li>
                        </c:forEach>
                    </ul>
                    <paw:pager baseUrl="/perfil/bandeja" pageParam="chatsPage"
                               currentPage="${chatGroupsCurrentPage}" totalPages="${chatGroupsTotalPages}"
                               otherParam="page" otherPage="${currentPage}"/>
                </c:otherwise>
            </c:choose>
        </section>

        <h2 class="inbox-section-title"><spring:message code="inbox.notificationsHeading"/></h2>
        <c:choose>
            <c:when test="${empty messages}">
                <p class="empty-state"><spring:message code="inbox.empty"/></p>
            </c:when>
            <c:otherwise>
                <%-- "Marcar como leidas" (POST): solo los sin leer de ESTA pagina. Abrir la
                     bandeja no marca nada; lo que esta en otra pagina sigue sin leer. --%>
                <c:if test="${hasUnread}">
                    <spring:message code="inbox.markRead" var="markReadLabel"/>
                    <form action="<c:url value='/perfil/bandeja/marcar-leidas'/>" method="post">
                        <c:forEach var="message" items="${messages}">
                            <c:if test="${message.unread}">
                                <input type="hidden" name="ids" value="${message.id}"/>
                            </c:if>
                        </c:forEach>
                        <input type="hidden" name="page" value="${currentPage}"/>
                        <input type="hidden" name="chatsPage" value="${chatGroupsCurrentPage}"/>
                        <paw:button text="${markReadLabel}"/>
                    </form>
                </c:if>
                <ul class="inbox-list">
                    <c:forEach var="message" items="${messages}">
                        <c:set var="event" value="${eventsById[message.eventId]}"/>
                        <%-- A donde lleva cada aviso: lo que le toca al organizador
                             a "Inscriptos" de su evento, y el resto al detalle del evento. --%>
                        <c:choose>
                            <c:when test="${empty event}"><c:set var="targetUrl" value=""/></c:when>
                            <c:when test="${message.type == 'NEW_REGISTRATION' or message.type == 'REGISTRATION_CANCELLED'
                                            or message.type == 'NEW_PENDING' or message.type == 'EVENT_TOMORROW'}">
                                <c:url var="targetUrl" value="/perfil/eventos/${event.id}/inscriptos"/>
                            </c:when>
                            <c:otherwise><c:url var="targetUrl" value="/eventos/${event.id}"/></c:otherwise>
                        </c:choose>

                        <li class="inbox-item${message.unread ? ' inbox-item-unread' : ''}">
                            <div class="inbox-item-main">
                                <%-- htmlEscape: el nombre de quien se anoto y el titulo del
                                     evento los escribio un usuario. --%>
                                <p class="inbox-item-text">
                                    <c:if test="${message.unread}">
                                        <span class="pill pill-active"><spring:message code="inbox.newBadge"/></span>
                                    </c:if>
                                    <spring:message code="inbox.type.${message.type}" htmlEscape="true">
                                        <spring:argument value="${message.actorName}"/>
                                        <spring:argument value="${event.title}"/>
                                    </spring:message>
                                </p>
                                <c:if test="${not empty message.body}">
                                    <p class="inbox-item-body"><c:out value="${message.body}"/></p>
                                </c:if>
                                <p class="inbox-item-date">
                                    <paw:date value="${message.createdAt}" format="dateTime"/>
                                </p>
                            </div>
                            <c:if test="${not empty targetUrl}">
                                <a class="btn btn-medium" href="${targetUrl}"><spring:message code="inbox.open"/></a>
                            </c:if>
                        </li>
                    </c:forEach>
                </ul>

                <paw:pager baseUrl="/perfil/bandeja" currentPage="${currentPage}" totalPages="${totalPages}"
                           otherParam="chatsPage" otherPage="${chatGroupsCurrentPage}"/>
            </c:otherwise>
        </c:choose>
    </main>
</div>
</paw:layout>
