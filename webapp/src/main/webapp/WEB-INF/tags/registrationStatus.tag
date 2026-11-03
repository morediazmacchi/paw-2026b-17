<%@ tag language="java" pageEncoding="UTF-8" body-content="empty" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ attribute name="status" required="true" %>
<%@ attribute name="cssClass" required="false" %>
<%-- Estado de la inscripcion de quien mira (CONFIRMED, PENDING, WAITLISTED, REJECTED): una
     pastilla con el nombre y, al apoyar el mouse, una frase que aclara que significa. --%>
<spring:message code="registration.status.${status}.hint" var="statusHint"/>
<span class="status-pill status-pill-${fn:toLowerCase(status)} ${cssClass}" title="<c:out value='${statusHint}'/>">
    <spring:message code="registration.status.${status}"/>
</span>
