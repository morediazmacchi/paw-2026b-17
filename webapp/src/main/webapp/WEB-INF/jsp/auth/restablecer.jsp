<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="auth.resetPassword.title">
<jsp:attribute name="head">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
    <script src="<c:url value='/js/password-toggle.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<main class="page page-narrow">
    <div class="auth-card">
        <c:choose>
            <c:when test="${tokenValid}">
                <h1><spring:message code="auth.resetPassword.heading"/></h1>

                <spring:message code="auth.resetPassword.newPasswordLabel" var="newPasswordLabel"/>
                <spring:message code="auth.resetPassword.confirmPasswordLabel" var="confirmPasswordLabel"/>
                <spring:message code="auth.resetPassword.submitButton" var="submitButtonLabel"/>
                <form action="<c:url value='/login/restablecer'/>" method="post" novalidate="novalidate">
                    <%-- fn:escapeXml, no c:out: esto es el VALOR de un atributo, no texto visible
                         entre tags. Sin esto, un token con comillas/markup podia cerrar el
                         atributo e inyectar HTML/JS (XSS reflejado via el POST de abajo, que
                         reimprime el token tal cual vino en el body). --%>
                    <input type="hidden" name="token" value="${fn:escapeXml(resetPasswordForm.token)}"/>
                    <paw:input name="password" maxLength="72" label="${newPasswordLabel}" type="password"
                               errorMessage="${fieldErrors.password}"/>
                    <paw:input name="confirmarPassword" maxLength="72" label="${confirmPasswordLabel}" type="password"
                               errorMessage="${fieldErrors.confirmarPassword}"/>
                    <paw:button text="${submitButtonLabel}" cssClass="btn-accent"/>
                </form>
            </c:when>
            <c:otherwise>
                <h1><spring:message code="auth.resetPassword.invalidHeading"/></h1>
                <p class="alert"><spring:message code="auth.resetPassword.invalidMessage"/></p>
                <p class="auth-footer">
                    <spring:message code="auth.resetPassword.requestNewLink" var="requestNewLinkLabel"/>
                    <a href="<c:url value='/login/recuperar'/>"><c:out value="${requestNewLinkLabel}"/></a>
                </p>
            </c:otherwise>
        </c:choose>
    </div>
</main>
</jsp:body>
</paw:layout>
