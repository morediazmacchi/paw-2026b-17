<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="admin.users.title"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</head>
<body>

<paw:navbar>
    <spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>"><c:out value="${backToEventsLabel}"/></a>
</paw:navbar>

<main class="page">
    <h1><spring:message code="admin.users.heading"/></h1>
    <spring:message code="admin.reports.tabLabel" var="reportsTabLabel"/>
    <spring:message code="admin.reports.trackingTabLabel" var="trackingTabLabel"/>
    <spring:message code="admin.users.tabLabel" var="usersTabLabel"/>
    <div class="auth-tabs">
        <a class="pill auth-tab" href="<c:url value='/admin/reportes'/>"><c:out value="${reportsTabLabel}"/></a>
        <a class="pill auth-tab" href="<c:url value='/admin/reportes/seguimiento'/>"><c:out value="${trackingTabLabel}"/></a>
        <span class="pill pill-active auth-tab"><c:out value="${usersTabLabel}"/></span>
    </div>

    <c:if test="${not empty adminError}">
        <p class="alert"><spring:message code="admin.users.cannotActOnSelf"/></p>
    </c:if>

    <spring:message code="admin.users.suspendButton" var="suspendButtonLabel"/>
    <spring:message code="admin.users.reactivateButton" var="reactivateButtonLabel"/>
    <spring:message code="admin.users.promoteButton" var="promoteButtonLabel"/>
    <spring:message code="admin.users.revokeButton" var="revokeButtonLabel"/>
    <spring:message code="admin.users.emailColumn" var="emailColumnLabel"/>
    <spring:message code="admin.users.nameColumn" var="nameColumnLabel"/>
    <spring:message code="admin.users.rolesColumn" var="rolesColumnLabel"/>
    <spring:message code="admin.users.statusColumn" var="statusColumnLabel"/>
    <div class="responsive-table-wrap">
        <table class="responsive-table users-table">
            <thead>
            <tr>
                <th><c:out value="${emailColumnLabel}"/></th>
                <th><c:out value="${nameColumnLabel}"/></th>
                <th><c:out value="${rolesColumnLabel}"/></th>
                <th><c:out value="${statusColumnLabel}"/></th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <c:forEach var="targetUser" items="${users}">
                <tr>
                    <td data-label="${emailColumnLabel}"><c:out value="${targetUser.email}"/></td>
                    <td data-label="${nameColumnLabel}"><c:out value="${targetUser.name}"/></td>
                    <td data-label="${rolesColumnLabel}">
                        <div class="pills">
                            <c:forEach var="role" items="${targetUser.roles}">
                                <span class="pill"><c:out value="${role}"/></span>
                            </c:forEach>
                        </div>
                    </td>
                    <td data-label="${statusColumnLabel}">
                        <c:choose>
                            <c:when test="${targetUser.suspended}">
                                <span class="pill"><spring:message code="admin.users.statusSuspended"/></span>
                            </c:when>
                            <c:otherwise>
                                <span class="pill pill-active"><spring:message code="admin.users.statusActive"/></span>
                            </c:otherwise>
                        </c:choose>
                    </td>
                    <td class="admin-report-actions">
                        <%-- Nadie actua sobre su propia fila (ver AdminController): ni
                             suspenderse ni sacarse su propio ROLE_ADMIN. --%>
                        <c:if test="${targetUser.id != currentUserId}">
                            <c:choose>
                                <c:when test="${targetUser.suspended}">
                                    <form action="<c:url value='/admin/usuarios/${targetUser.id}/reactivar'/>" method="post">
                                        <paw:button text="${reactivateButtonLabel}"/>
                                    </form>
                                </c:when>
                                <c:otherwise>
                                    <form action="<c:url value='/admin/usuarios/${targetUser.id}/suspender'/>" method="post">
                                        <paw:button text="${suspendButtonLabel}" cssClass="btn-danger"/>
                                    </form>
                                </c:otherwise>
                            </c:choose>
                            <%-- Otorgar/revocar admin: solo si quien mira esta pantalla es
                                 superadmin (WebAuthConfig ya lo exige en el servidor; esto
                                 solo evita ofrecer un boton que el POST va a rechazar). --%>
                            <c:if test="${isSuperadmin}">
                                <c:choose>
                                    <c:when test="${fn:contains(targetUser.roles, 'ROLE_ADMIN')}">
                                        <form action="<c:url value='/admin/usuarios/${targetUser.id}/revocar'/>" method="post">
                                            <paw:button text="${revokeButtonLabel}"/>
                                        </form>
                                    </c:when>
                                    <c:otherwise>
                                        <form action="<c:url value='/admin/usuarios/${targetUser.id}/promover'/>" method="post">
                                            <paw:button text="${promoteButtonLabel}"/>
                                        </form>
                                    </c:otherwise>
                                </c:choose>
                            </c:if>
                        </c:if>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
    </div>
</main>

</body>
</html>
