<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="admin.users.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<main class="page">
    <h1><spring:message code="admin.users.heading"/></h1>
    <spring:message code="admin.reports.tabLabel" var="reportsTabLabel"/>
    <spring:message code="admin.reports.trackingTabLabel" var="trackingTabLabel"/>
    <spring:message code="admin.pendingEvents.tabLabel" var="pendingEventsTabLabel"/>
    <spring:message code="admin.users.tabLabel" var="usersTabLabel"/>
    <div class="auth-tabs">
        <a class="pill auth-tab" href="<c:url value='/admin/reportes'/>"><c:out value="${reportsTabLabel}"/></a>
        <a class="pill auth-tab" href="<c:url value='/admin/reportes/seguimiento'/>"><c:out value="${trackingTabLabel}"/></a>
        <a class="pill auth-tab" href="<c:url value='/admin/eventos-pendientes'/>"><c:out value="${pendingEventsTabLabel}"/></a>
        <span class="pill pill-active auth-tab"><c:out value="${usersTabLabel}"/></span>
    </div>

    <c:if test="${not empty revokeAdminError}">
        <p class="alert"><spring:message code="admin.users.revokeAdminError.${revokeAdminError}"/></p>
    </c:if>
    <c:if test="${not empty suspendError}">
        <p class="alert"><spring:message code="admin.users.suspendError.${suspendError}"/></p>
    </c:if>

    <c:choose>
        <c:when test="${empty users}">
            <p class="empty-state"><spring:message code="admin.users.empty"/></p>
        </c:when>
        <c:otherwise>
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
                                        <span class="pill"><spring:message code="admin.users.role.${role}"/></span>
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
                                     suspenderse ni sacarse su propio rol ADMIN. --%>
                                <c:if test="${targetUser.id != currentUserId}">
                                    <c:choose>
                                        <c:when test="${targetUser.suspended}">
                                            <form action="<c:url value='/admin/usuarios/${targetUser.id}/reactivar'/>" method="post">
                                                <paw:button text="${reactivateButtonLabel}"/>
                                            </form>
                                        </c:when>
                                        <%-- A un admin solo lo suspende un superadmin (ver AdminController.suspender). --%>
                                        <c:when test="${targetUser.canBeSuspendedBy(isSuperadmin)}">
                                            <form action="<c:url value='/admin/usuarios/${targetUser.id}/suspender'/>" method="post">
                                                <paw:button text="${suspendButtonLabel}" cssClass="btn-danger"/>
                                            </form>
                                        </c:when>
                                    </c:choose>
                                    <%-- Otorgar/revocar admin: solo si quien mira esta pantalla es
                                         superadmin (WebAuthConfig ya lo exige en el servidor; esto
                                         solo evita ofrecer un boton que el POST va a rechazar). A un
                                         superadmin no se le ofrece ninguno: ya es admin y no se le saca
                                         el rol (RevokeAdminResult.TARGET_SUPERADMIN). --%>
                                    <c:if test="${targetUser.canHaveAdminRoleChangedBy(isSuperadmin)}">
                                        <c:choose>
                                            <c:when test="${targetUser.admin}">
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

            <paw:pager baseUrl="/admin/usuarios" currentPage="${currentPage}" totalPages="${totalPages}"/>
        </c:otherwise>
    </c:choose>
</main>
</jsp:body>
</paw:layout>
