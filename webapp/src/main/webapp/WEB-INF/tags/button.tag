<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="text" required="true" %>
<%@ attribute name="size" required="false" %>
<%@ attribute name="cssClass" required="false" %>
<%@ attribute name="disabled" required="false" type="java.lang.Boolean" %>
<%@ attribute name="type" required="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:set var="btnSize" value="${empty size ? 'medium' : size}" />
<c:set var="btnClass" value="btn btn-${btnSize} ${cssClass}" />
<c:set var="btnType" value="${empty type ? 'submit' : type}" />

<button type="${btnType}" class="${btnClass}" <c:if test="${disabled}">disabled="disabled"</c:if>><c:out value="${text}" /></button>
