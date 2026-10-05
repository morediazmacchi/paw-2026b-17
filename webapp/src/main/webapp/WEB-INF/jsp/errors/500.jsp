<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="error.serverError.title">
<paw:navbar backToEvents="true"/>

<main class="page page-narrow">
    <div class="error-state">
        <div class="error-state-icon error-state-icon-danger"><paw:icon name="close"/></div>
        <h1><spring:message code="error.serverError.heading"/></h1>
        <p class="error-state-message"><spring:message code="error.serverError.message"/></p>
        <a class="btn btn-medium btn-accent" href="<c:url value='/eventos'/>"><spring:message code="error.serverError.button"/></a>
    </div>
</main>
</paw:layout>
