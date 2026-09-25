<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="inbox.title"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>

<paw:navbar>
    <spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>"><c:out value="${backToEventsLabel}"/></a>
</paw:navbar>

<div class="account-layout">
    <paw:accountSidebar active="inbox" registrationsCount="${registrationsCount}" favoritesCount="${favoritesCount}" eventsCount="${eventsCount}"/>

    <main class="account-main">
        <h1><spring:message code="inbox.heading"/></h1>
        <p class="auth-subheading"><spring:message code="inbox.subheading"/></p>

        <c:choose>
            <c:when test="${empty messages}">
                <p class="empty-state"><spring:message code="inbox.empty"/></p>
            </c:when>
            <c:otherwise>
                <ul class="inbox-list">
                    <c:forEach var="message" items="${messages}">
                        <c:set var="event" value="${eventsById[message.eventId]}"/>
                        <%-- A donde lleva cada aviso: lo que le toca al organizador
                             a "Inscriptos" de su evento, y el resto al detalle del evento. --%>
                        <c:choose>
                            <c:when test="${empty event}"><c:set var="targetUrl" value=""/></c:when>
                            <c:when test="${message.type == 'NEW_REGISTRATION' or message.type == 'REGISTRATION_CANCELLED'
                                            or message.type == 'NEW_PENDING' or message.type == 'WAITLIST_ACCEPTED'
                                            or message.type == 'WAITLIST_DECLINED' or message.type == 'EVENT_TOMORROW'}">
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
                                    <fmt:formatDate value="${message.createdAt}" pattern="dd/MM/yyyy · HH:mm"/>hs
                                </p>
                            </div>
                            <c:if test="${not empty targetUrl}">
                                <a class="btn btn-medium" href="${targetUrl}"><spring:message code="inbox.open"/></a>
                            </c:if>
                        </li>
                    </c:forEach>
                </ul>

                <c:if test="${totalPages > 1}">
                    <nav class="pager">
                        <c:if test="${currentPage > 1}">
                            <a class="pager-arrow" href="<c:url value='/perfil/bandeja'><c:param name='page' value='${currentPage - 1}'/></c:url>">
                                <spring:message code="event.list.prevPage"/>
                            </a>
                        </c:if>
                        <span class="pager-number pager-number-active"><c:out value="${currentPage}"/> / <c:out value="${totalPages}"/></span>
                        <c:if test="${currentPage < totalPages}">
                            <a class="pager-arrow" href="<c:url value='/perfil/bandeja'><c:param name='page' value='${currentPage + 1}'/></c:url>">
                                <spring:message code="event.list.nextPage"/>
                            </a>
                        </c:if>
                    </nav>
                </c:if>
            </c:otherwise>
        </c:choose>
    </main>
</div>

</body>
</html>
