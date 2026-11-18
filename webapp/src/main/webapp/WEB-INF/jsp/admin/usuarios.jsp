<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
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
    <spring:message code="admin.allEvents.tabLabel" var="allEventsTabLabel"/>
    <spring:message code="admin.users.tabLabel" var="usersTabLabel"/>
    <div class="auth-tabs">
        <a class="pill auth-tab" href="<c:url value='/admin/reportes'/>"><c:out value="${reportsTabLabel}"/></a>
        <a class="pill auth-tab" href="<c:url value='/admin/eventos'/>"><c:out value="${allEventsTabLabel}"/></a>
        <span class="pill pill-active auth-tab"><c:out value="${usersTabLabel}"/></span>
    </div>

    <c:if test="${not empty revokeAdminError}">
        <p class="alert"><spring:message code="admin.users.revokeAdminError.${revokeAdminError}"/></p>
    </c:if>

    <%-- Buscar por mail (contiene, sin importar mayusculas). GET: la busqueda queda en la URL y el paginador la repite. --%>
    <spring:message code="admin.users.searchPlaceholder" var="searchPlaceholder"/>
    <spring:message code="admin.users.searchLabel" var="searchLabel"/>
    <spring:message code="admin.users.searchButton" var="searchButtonLabel"/>
    <spring:message code="admin.users.clearSearch" var="clearSearchLabel"/>
    <c:set var="searchText" value="${fn:trim(q)}"/>
    <div class="search-bar-row">
        <c:if test="${not empty searchText}">
            <a class="results-clear" href="<c:url value='/admin/usuarios'/>"><c:out value="${clearSearchLabel}"/></a>
        </c:if>
        <form method="get" action="<c:url value='/admin/usuarios'/>" class="search-bar" role="search">
            <input type="search" name="q" class="search-input" maxlength="150"
                   value="${fn:escapeXml(q)}"
                   placeholder="${fn:escapeXml(searchPlaceholder)}"
                   aria-label="${fn:escapeXml(searchLabel)}"/>
            <button type="submit" class="search-submit" aria-label="${fn:escapeXml(searchButtonLabel)}"><paw:icon name="search"/></button>
        </form>
    </div>
    <c:if test="${not empty searchText}">
        <div class="results-header">
            <div class="results-info">
                <h2 class="results-count"><spring:message code="admin.users.resultsCount" arguments="${totalElements}"/></h2>
            </div>
        </div>
    </c:if>

    <c:choose>
        <c:when test="${empty users}">
            <p class="empty-state">
                <spring:message code="${empty searchText ? 'admin.users.empty' : 'admin.users.searchEmpty'}"/>
            </p>
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
                    <%-- Quien mira es superadmin: UN solo mecanismo (el rol de la sesion, como el antMatcher de WebAuthConfig). --%>
                    <sec:authorize access="hasRole('SUPERADMIN')" var="callerIsSuperadmin"/>
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
                            <td class="admin-actions-cell">
                            <div class="admin-report-actions">
                                <%-- Nadie actua sobre su propia fila (ver AdminController): ni
                                     suspenderse ni sacarse su propio rol ADMIN. --%>
                                <c:if test="${not targetUser.isSameAccountAs(currentUserId)}">
                                    <c:choose>
                                        <%-- Reactivar sigue la misma regla que suspender (ver AdminSecurity.canReactivate):
                                             a un admin suspendido solo lo reactiva un superadmin. --%>
                                        <c:when test="${targetUser.suspended}">
                                            <c:if test="${targetUser.canBeSuspendedBy(callerIsSuperadmin)}">
                                                <form action="<c:url value='/admin/usuarios/${targetUser.id}/reactivar'/>" method="post">
                                                    <input type="hidden" name="returnTo" value="${fn:escapeXml(returnTo)}"/>
                                                    <paw:button text="${reactivateButtonLabel}"/>
                                                </form>
                                            </c:if>
                                        </c:when>
                                        <%-- A un admin solo lo suspende un superadmin (ver AdminController.suspender). --%>
                                        <c:when test="${targetUser.canBeSuspendedBy(callerIsSuperadmin)}">
                                            <form action="<c:url value='/admin/usuarios/${targetUser.id}/suspender'/>" method="post">
                                                <input type="hidden" name="returnTo" value="${fn:escapeXml(returnTo)}"/>
                                                <paw:button text="${suspendButtonLabel}" cssClass="btn-danger"/>
                                            </form>
                                        </c:when>
                                    </c:choose>
                                    <%-- Otorgar/revocar admin. "Hacer admin" lo ve cualquier admin (esta pantalla ya es
                                         solo de admins) en las cuentas que todavia no lo son. "Quitar admin" solo si
                                         quien mira es superadmin (mismo criterio que el antMatcher de WebAuthConfig: el
                                         rol da el acceso; esto solo evita ofrecer un boton que el POST va a rechazar).
                                         A un superadmin no se le ofrece ninguno: ya es admin y no se le saca el rol
                                         (el service lo rechaza con RevokeAdminResult.TARGET_SUPERADMIN, la misma
                                         propiedad que se mira aca). --%>
                                    <c:if test="${not targetUser.superadmin}">
                                        <c:choose>
                                            <c:when test="${targetUser.admin}">
                                                <c:if test="${callerIsSuperadmin}">
                                                    <form action="<c:url value='/admin/usuarios/${targetUser.id}/revocar'/>" method="post">
                                                        <input type="hidden" name="returnTo" value="${fn:escapeXml(returnTo)}"/>
                                                        <paw:button text="${revokeButtonLabel}"/>
                                                    </form>
                                                </c:if>
                                            </c:when>
                                            <c:otherwise>
                                                <form action="<c:url value='/admin/usuarios/${targetUser.id}/promover'/>" method="post">
                                                    <input type="hidden" name="returnTo" value="${fn:escapeXml(returnTo)}"/>
                                                    <paw:button text="${promoteButtonLabel}"/>
                                                </form>
                                            </c:otherwise>
                                        </c:choose>
                                    </c:if>
                                </c:if>
                            </div>
                            </td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>

            <paw:pager baseUrl="/admin/usuarios" currentPage="${currentPage}" totalPages="${totalPages}"
                       extraParams="${pagerParams}"/>
        </c:otherwise>
    </c:choose>
</main>
</jsp:body>
</paw:layout>
