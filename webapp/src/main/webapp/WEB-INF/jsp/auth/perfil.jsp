<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="auth.profile.title"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
    <script src="<c:url value='/js/password-toggle.js'/>"></script>
</head>
<body>

<paw:navbar>
    <spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>"><c:out value="${backToEventsLabel}"/></a>
</paw:navbar>

<div class="account-layout">
    <paw:accountSidebar active="data" registrationsCount="${registrationsCount}" favoritesCount="${favoritesCount}" eventsCount="${eventsCount}"/>

    <main class="account-main">
        <div class="account-identity-card">
            <span class="profile-avatar profile-avatar-large"><c:out value="${fn:toUpperCase(fn:substring(user.name, 0, 1))}"/></span>
            <div>
                <p class="account-identity-name"><c:out value="${user.name}"/></p>
                <p class="account-identity-email"><c:out value="${user.email}"/></p>
            </div>
        </div>

        <c:if test="${profileUpdated}">
            <p class="alert"><spring:message code="auth.profile.updated"/></p>
        </c:if>

        <spring:message code="auth.profile.nameLabel" var="nameLabel"/>
        <spring:message code="auth.login.emailLabel" var="emailLabel"/>
        <spring:message code="auth.profile.saveButton" var="saveButtonLabel"/>
        <form class="account-fields-form" action="<c:url value='/perfil'/>" method="post" novalidate="novalidate">
            <paw:input name="nombre" label="${nameLabel}" type="text"
                       value="${profileForm.nombre}" errorMessage="${profileFieldErrors.nombre}"/>
            <paw:input name="email" label="${emailLabel}" type="email"
                       value="${profileForm.email}" errorMessage="${profileFieldErrors.email}"/>
            <paw:button text="${saveButtonLabel}" cssClass="btn-accent"/>
        </form>

        <hr class="profile-divider"/>

        <c:if test="${passwordChanged}">
            <p class="alert"><spring:message code="auth.profile.passwordChanged"/></p>
        </c:if>

        <h2 class="profile-section-title"><spring:message code="auth.profile.passwordHeading"/></h2>

        <spring:message code="auth.profile.currentPasswordLabel" var="currentPasswordLabel"/>
        <spring:message code="auth.login.newPasswordLabel" var="newPasswordLabel"/>
        <spring:message code="auth.register.confirmPasswordLabel" var="confirmPasswordLabel"/>
        <spring:message code="auth.profile.changePasswordButton" var="changePasswordButtonLabel"/>
        <form class="account-fields-form account-fields-form-single" action="<c:url value='/perfil/contrasena'/>" method="post" novalidate="novalidate">
            <paw:input name="currentPassword" label="${currentPasswordLabel}" type="password"
                       errorMessage="${passwordFieldErrors.currentPassword}"/>
            <paw:input name="newPassword" label="${newPasswordLabel}" type="password"
                       errorMessage="${passwordFieldErrors.newPassword}"/>
            <paw:input name="confirmarPassword" label="${confirmPasswordLabel}" type="password"
                       errorMessage="${passwordFieldErrors.confirmarPassword}"/>
            <paw:button text="${changePasswordButtonLabel}"/>
        </form>
    </main>
</div>

</body>
</html>
