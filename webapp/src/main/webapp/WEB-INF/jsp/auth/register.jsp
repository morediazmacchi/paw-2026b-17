<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="auth.register.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
    <script src="<c:url value='/js/password-toggle.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<main class="page page-narrow">
    <div class="auth-card">
        <spring:message code="auth.login.tabLogin" var="tabLoginLabel"/>
        <spring:message code="auth.login.tabRegister" var="tabRegisterLabel"/>
        <%-- Mismo redirect que trajo del login: si vuelve a "Ingresar", no se pierde. --%>
        <c:url value="/login" var="loginUrl">
            <c:if test="${not empty redirectTo}"><c:param name="redirect" value="${redirectTo}"/></c:if>
        </c:url>
        <div class="auth-tabs">
            <a class="pill auth-tab" href="${loginUrl}"><c:out value="${tabLoginLabel}"/></a>
            <span class="pill pill-active auth-tab"><c:out value="${tabRegisterLabel}"/></span>
        </div>

        <h1><spring:message code="auth.register.heading"/></h1>
        <p class="auth-subheading"><spring:message code="auth.register.subheading"/></p>

        <spring:message code="auth.register.nameLabel" var="nameLabel"/>
        <spring:message code="auth.login.emailLabel" var="emailLabel"/>
        <spring:message code="auth.login.passwordLabel" var="passwordLabel"/>
        <spring:message code="auth.register.confirmPasswordLabel" var="confirmPasswordLabel"/>
        <spring:message code="auth.register.submitButton" var="submitButtonLabel"/>
        <form action="<c:url value='/registro'/>" method="post" novalidate="novalidate">
            <c:if test="${not empty redirectTo}">
                <input type="hidden" name="redirect" value="<c:out value='${redirectTo}'/>"/>
            </c:if>
            <paw:input name="nombre" maxLength="150" label="${nameLabel}" type="text"
                       value="${userForm.nombre}" errorMessage="${fieldErrors.nombre}"/>
            <paw:input name="email" maxLength="150" label="${emailLabel}" type="email"
                       value="${userForm.email}" errorMessage="${fieldErrors.email}"/>
            <paw:input name="password" maxLength="72" label="${passwordLabel}" type="password"
                       errorMessage="${fieldErrors.password}"/>
            <paw:input name="confirmarPassword" maxLength="72" label="${confirmPasswordLabel}" type="password"
                       errorMessage="${fieldErrors.confirmarPassword}"/>
            <paw:button text="${submitButtonLabel}" cssClass="btn-accent"/>
        </form>

        <p class="auth-footer">
            <spring:message code="auth.register.haveAccount"/>
            <spring:message code="auth.login.tabLogin" var="loginLinkLabel"/>
            <a href="${loginUrl}"><c:out value="${loginLinkLabel}"/></a>
        </p>
    </div>
</main>
</jsp:body>
</paw:layout>
