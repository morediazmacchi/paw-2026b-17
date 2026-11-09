<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="auth.lists.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<div class="account-layout">
    <paw:accountSidebar active="lists" registrationsCount="${accountCounts.registrations}" favoritesCount="${accountCounts.favorites}" eventsCount="${accountCounts.events}" invitationsCount="${accountCounts.invitations}"/>

    <main class="account-main">
        <h1><spring:message code="auth.lists.heading"/></h1>
        <p class="auth-subheading"><spring:message code="auth.lists.subheading"/></p>

        <c:if test="${listDeleted == true}">
            <p class="alert alert-success"><spring:message code="auth.lists.deletedMessage"/></p>
        </c:if>
        <c:if test="${listDeleted == false}">
            <p class="alert"><spring:message code="auth.lists.notFoundMessage"/></p>
        </c:if>

        <c:choose>
            <c:when test="${empty savedSearches}">
                <p class="empty-state"><spring:message code="auth.lists.empty"/></p>
            </c:when>
            <c:otherwise>
                <spring:message code="auth.lists.viewEventsButton" var="viewEventsLabel"/>
                <spring:message code="auth.lists.deleteButton" var="deleteLabel"/>
                <div class="saved-search-list">
                    <c:forEach var="savedSearch" items="${savedSearches}">
                        <div class="saved-search-row">
                            <div class="saved-search-info">
                                <paw:savedSearchFilters search="${savedSearch}"/>
                            </div>
                            <div class="saved-search-actions">
                                <a class="btn btn-medium btn-accent" href="<c:url value='${savedSearch.eventsPath}'/>"><c:out value="${viewEventsLabel}"/></a>
                                <form action="<c:url value='/perfil/listas/${savedSearch.id}/eliminar'/>" method="post">
                                    <input type="hidden" name="page" value="${currentPage}"/>
                                    <paw:button text="${deleteLabel}" cssClass="btn-danger"/>
                                </form>
                            </div>
                        </div>
                    </c:forEach>
                </div>
                <paw:pager baseUrl="/perfil/listas" currentPage="${currentPage}" totalPages="${totalPages}"/>
            </c:otherwise>
        </c:choose>
    </main>
</div>
</jsp:body>
</paw:layout>
