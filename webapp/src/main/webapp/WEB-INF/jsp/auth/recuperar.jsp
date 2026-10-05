<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="auth.forgotPassword.title"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</head>
<body>

<paw:navbar>
    <spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>"><c:out value="${backToEventsLabel}"/></a>
</paw:navbar>

<main class="page page-narrow">
    <div class="auth-card">
        <h1><spring:message code="auth.forgotPassword.heading"/></h1>
        <p class="auth-subheading"><spring:message code="auth.forgotPassword.subheading"/></p>

        <c:choose>
            <c:when test="${sent}">
                <p class="alert alert-success"><spring:message code="auth.forgotPassword.sentMessage"/></p>
            </c:when>
            <c:otherwise>
                <spring:message code="auth.forgotPassword.emailLabel" var="emailLabel"/>
                <spring:message code="auth.forgotPassword.submitButton" var="submitButtonLabel"/>
                <form action="<c:url value='/login/recuperar'/>" method="post" novalidate="novalidate">
                    <paw:input name="email" label="${emailLabel}" type="email"
                               value="${forgotPasswordForm.email}" errorMessage="${fieldErrors.email}"/>
                    <paw:button text="${submitButtonLabel}" cssClass="btn-accent"/>
                </form>
            </c:otherwise>
        </c:choose>

        <p class="auth-footer">
            <spring:message code="auth.forgotPassword.backToLogin" var="backToLoginLabel"/>
            <a href="<c:url value='/login'/>"><c:out value="${backToLoginLabel}"/></a>
        </p>
    </div>
</main>

</body>
</html>
