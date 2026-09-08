<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title><spring:message code="demo.tags.title"/></title>
</head>
<body>

<spring:message code="demo.tags.saveButton" var="saveButtonLabel"/>
<spring:message code="demo.tags.cancelButton" var="cancelButtonLabel"/>
<spring:message code="demo.tags.deleteButton" var="deleteButtonLabel"/>
<spring:message code="demo.tags.laptopName" var="laptopName"/>
<spring:message code="demo.tags.mouseName" var="mouseName"/>
<spring:message code="demo.tags.emailLabel" var="emailLabel"/>
<spring:message code="demo.tags.ageLabel" var="ageLabel"/>
<spring:message code="demo.tags.passwordLabel" var="passwordLabel"/>

<h1><spring:message code="demo.tags.buttonsHeading"/></h1>
<paw:button text="${saveButtonLabel}" />
<paw:button text="${cancelButtonLabel}" size="small" cssClass="secondary" />
<paw:button text="${deleteButtonLabel}" size="large" disabled="${true}" />

<h1><spring:message code="demo.tags.cardsHeading"/></h1>
<paw:card id="1" nombre="${laptopName}" precio="${599.99}" fecha="${hoy}" />
<paw:card id="2" nombre="${mouseName}" precio="${15.5}" fecha="${hoy}" />

<h1><spring:message code="demo.tags.inputsHeading"/></h1>
<paw:input name="email" label="${emailLabel}" type="email" />
<paw:input name="edad" label="${ageLabel}" type="number"  />
<paw:input name="password" label="${passwordLabel}" type="password" />

</body>
</html>
