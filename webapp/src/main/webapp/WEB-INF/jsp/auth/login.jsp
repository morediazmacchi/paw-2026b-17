<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="auth.login.title"/></title>
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

<main class="page page-narrow">
    <div class="auth-card">
        <spring:message code="auth.login.tabLogin" var="tabLoginLabel"/>
        <spring:message code="auth.login.tabRegister" var="tabRegisterLabel"/>
        <%-- Si vino con ?redirect= (por ejemplo desde un evento privado), crear
             la cuenta no lo pierde: RegisterController lo sigue pasando. --%>
        <c:url value="/registro" var="registerUrl">
            <c:if test="${not empty redirectTo}"><c:param name="redirect" value="${redirectTo}"/></c:if>
        </c:url>
        <div class="auth-tabs">
            <span class="pill pill-active auth-tab"><c:out value="${tabLoginLabel}"/></span>
            <a class="pill auth-tab" href="${registerUrl}"><c:out value="${tabRegisterLabel}"/></a>
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
                    <c:if test="${not empty redirectTo}">
                        <input type="hidden" name="redirect" value="${fn:escapeXml(redirectTo)}"/>
                    </c:if>
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
                <c:if test="${verifyError}">
                    <p class="alert"><spring:message code="auth.login.verifyErrorMessage"/></p>
                </c:if>
                <c:if test="${passwordReset}">
                    <p class="alert"><spring:message code="auth.login.passwordResetMessage"/></p>
                </c:if>
                <c:if test="${resent}">
                    <p class="alert"><spring:message code="auth.login.resentMessage"/></p>
                </c:if>
                <c:if test="${unverified}">
                    <p class="alert"><spring:message code="auth.login.unverifiedMessage"/></p>
                    <spring:message code="auth.login.resendButton" var="resendButtonLabel"/>
                    <form class="auth-inline-form" action="<c:url value='/login/reenviar-confirmacion'/>" method="post" novalidate="novalidate">
                        <input type="hidden" name="email" value="${fn:escapeXml(unverifiedEmail)}"/>
                        <paw:button text="${resendButtonLabel}"/>
                    </form>
                </c:if>
                <c:if test="${suspended}">
                    <p class="alert"><spring:message code="auth.login.suspendedMessage"/></p>
                </c:if>

                <spring:message code="auth.login.emailLabel" var="emailLabel"/>
                <spring:message code="auth.login.passwordLabel" var="passwordLabel"/>
                <spring:message code="auth.login.submitButton" var="submitButtonLabel"/>
                <form action="<c:url value='/login'/>" method="post" novalidate="novalidate">
                    <c:if test="${not empty redirectTo}">
                        <input type="hidden" name="redirect" value="${fn:escapeXml(redirectTo)}"/>
                    </c:if>
                    <paw:input name="email" label="${emailLabel}" type="email"/>
                    <paw:input name="password" label="${passwordLabel}" type="password"/>
                    <p class="auth-forgot-password">
                        <spring:message code="auth.login.forgotPasswordLink" var="forgotPasswordLabel"/>
                        <a href="<c:url value='/login/recuperar'/>"><c:out value="${forgotPasswordLabel}"/></a>
                    </p>
                    <paw:button text="${submitButtonLabel}" cssClass="btn-accent"/>
                </form>

                <p class="auth-footer">
                    <spring:message code="auth.login.noAccount"/>
                    <spring:message code="auth.login.registerLink" var="registerLinkLabel"/>
                    <a href="${registerUrl}"><c:out value="${registerLinkLabel}"/></a>
                </p>
            </c:otherwise>
        </c:choose>
    </div>
</main>

</body>
</html>
