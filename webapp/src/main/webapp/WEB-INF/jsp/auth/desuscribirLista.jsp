<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="auth.lists.unsubscribe.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<div class="account-layout">
    <paw:accountSidebar active="lists" registrationsCount="${accountCounts.registrations}" favoritesCount="${accountCounts.favorites}" eventsCount="${accountCounts.events}" invitationsCount="${accountCounts.invitations}"/>

    <main class="account-main">
        <h1><spring:message code="auth.lists.unsubscribe.heading"/></h1>

        <div class="saved-search-confirm">
            <p class="saved-search-confirm-question"><spring:message code="auth.lists.unsubscribe.question"/></p>
            <div class="saved-search-row">
                <div class="saved-search-info">
                    <paw:savedSearchFilters search="${savedSearch}"/>
                </div>
            </div>
            <p class="auth-subheading"><spring:message code="auth.lists.unsubscribe.hint"/></p>

            <spring:message code="auth.lists.unsubscribe.confirmButton" var="confirmLabel"/>
            <div class="saved-search-actions">
                <form action="<c:url value='/perfil/listas/${savedSearch.id}/desuscribirse'/>" method="post">
                    <paw:button text="${confirmLabel}" cssClass="btn-danger"/>
                </form>
                <a class="btn btn-medium" href="<c:url value='/perfil/listas'/>"><spring:message code="auth.lists.unsubscribe.cancelButton"/></a>
            </div>
        </div>
    </main>
</div>
</jsp:body>
</paw:layout>
