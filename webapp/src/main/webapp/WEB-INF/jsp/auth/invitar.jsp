<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="auth.invite.title"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</head>
<body>

<paw:navbar>
    <spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>"><c:out value="${backToEventsLabel}"/></a>
</paw:navbar>

<div class="account-layout">
    <paw:accountSidebar active="events" registrationsCount="${registrationsCount}" favoritesCount="${favoritesCount}" eventsCount="${eventsCount}"/>

    <main class="account-main">
        <h1><spring:message code="auth.invite.heading"/></h1>
        <p class="auth-subheading"><spring:message code="auth.invite.subheading" arguments="${event.title}"/></p>

        <c:if test="${sent}">
            <p class="alert"><spring:message code="auth.invite.sentMessage"/></p>
        </c:if>
        <c:if test="${alreadyInvited}">
            <p class="alert"><spring:message code="auth.invite.alreadyInvitedMessage"/></p>
        </c:if>

        <c:if test="${param.full != null}">
            <p class="alert"><spring:message code="auth.invite.fullMessage"/></p>
        </c:if>

        <spring:message code="auth.invite.emailLabel" var="emailLabel"/>
        <spring:message code="auth.invite.submitButton" var="submitButtonLabel"/>
        <form action="<c:url value='/perfil/eventos/${event.id}/invitar'/>" method="post" novalidate="novalidate">
            <paw:input name="email" label="${emailLabel}" type="email"
                       value="${inviteForm.email}" errorMessage="${fieldErrors.email}" />
            <paw:button text="${submitButtonLabel}" cssClass="btn-accent"/>
        </form>

        <hr class="profile-divider"/>
        <h2 class="profile-section-title"><spring:message code="auth.invite.listHeading"/></h2>

        <c:choose>
            <c:when test="${empty invitations}">
                <p class="empty-state"><spring:message code="auth.invite.empty"/></p>
            </c:when>
            <c:otherwise>
                <div class="pending-registration-list">
                    <c:forEach var="invitation" items="${invitations}">
                        <div class="pending-registration-row">
                            <fmt:formatDate value="${invitation.createdAt}" pattern="dd/MM/yyyy HH:mm" var="invitedAt"/>
                            <div class="pending-registration-info">
                                <p class="pending-registration-name"><c:out value="${invitation.email}"/></p>
                                <p class="pending-registration-meta">
                                    <spring:message code="auth.invite.invitedAt" arguments="${invitedAt}"/>
                                </p>
                            </div>
                        </div>
                    </c:forEach>
                </div>
            </c:otherwise>
        </c:choose>

        <p class="auth-subheading">
            <a class="btn-link" href="<c:url value='/perfil/eventos'/>">
                <spring:message code="auth.invite.backToEvents"/>
            </a>
        </p>
    </main>
</div>

</body>
</html>
