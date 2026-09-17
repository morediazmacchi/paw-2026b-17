<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="auth.login.title"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</head>
<body>

<paw:navbar>
    <spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>">&larr; <c:out value="${backToEventsLabel}"/></a>
</paw:navbar>

<main class="page page-narrow">
    <div class="auth-card">
        <spring:message code="auth.login.tabLogin" var="tabLoginLabel"/>
        <spring:message code="auth.login.tabRegister" var="tabRegisterLabel"/>
        <div class="auth-tabs">
            <span class="pill pill-active auth-tab"><c:out value="${tabLoginLabel}"/></span>
            <a class="pill auth-tab" href="<c:url value='/registro'/>"><c:out value="${tabRegisterLabel}"/></a>
        </div>

        <c:choose>
            <c:when test="${needsPassword}">
                <h1><spring:message code="auth.login.needsPasswordHeading"/></h1>
                <p class="auth-subheading"><spring:message code="auth.login.needsPasswordMessage"/></p>

                <spring:message code="auth.login.newPasswordLabel" var="newPasswordLabel"/>
                <spring:message code="auth.register.confirmPasswordLabel" var="confirmPasswordLabel"/>
                <spring:message code="auth.login.setPasswordButton" var="setPasswordButtonLabel"/>
                <form action="<c:url value='/login/nueva-contrasena'/>" method="post" novalidate="novalidate">
                    <input type="hidden" name="email" value="${fn:escapeXml(setPasswordForm.email)}"/>
                    <paw:input name="password" label="${newPasswordLabel}" type="password"
                               errorMessage="${fieldErrors.password}"/>
                    <paw:input name="confirmarPassword" label="${confirmPasswordLabel}" type="password"
                               errorMessage="${fieldErrors.confirmarPassword}"/>
                    <paw:button text="${setPasswordButtonLabel}" cssClass="btn-accent"/>
                </form>
            </c:when>
            <c:otherwise>
                <h1><spring:message code="auth.login.heading"/></h1>
                <p class="auth-subheading"><spring:message code="auth.login.subheading"/></p>

                <c:if test="${loginError}">
                    <p class="alert"><spring:message code="auth.login.error"/></p>
                </c:if>
                <c:if test="${loggedOut}">
                    <p class="alert"><spring:message code="auth.login.loggedOut"/></p>
                </c:if>
                <c:if test="${justRegistered}">
                    <p class="alert"><spring:message code="auth.register.success"/></p>
                </c:if>
                <c:if test="${passwordSet}">
                    <p class="alert"><spring:message code="auth.login.passwordSetSuccess"/></p>
                </c:if>

                <spring:message code="auth.login.emailLabel" var="emailLabel"/>
                <spring:message code="auth.login.passwordLabel" var="passwordLabel"/>
                <spring:message code="auth.login.submitButton" var="submitButtonLabel"/>
                <form action="<c:url value='/login'/>" method="post" novalidate="novalidate">
                    <paw:input name="email" label="${emailLabel}" type="email"/>
                    <paw:input name="password" label="${passwordLabel}" type="password"/>
                    <paw:button text="${submitButtonLabel}" cssClass="btn-accent"/>
                </form>

                <p class="auth-footer">
                    <spring:message code="auth.login.noAccount"/>
                    <spring:message code="auth.login.registerLink" var="registerLinkLabel"/>
                    <a href="<c:url value='/registro'/>"><c:out value="${registerLinkLabel}"/></a>
                </p>
            </c:otherwise>
        </c:choose>
    </div>
</main>

</body>
</html>
