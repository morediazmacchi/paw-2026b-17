<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="name" required="true" %>
<%@ attribute name="label" required="false" %>
<%@ attribute name="type" required="false" %>
<%@ attribute name="errorMessage" required="false" %>
<%@ attribute name="required" required="false" type="java.lang.Boolean" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:set var="inputType" value="${empty type ? 'text' : type}" />
<c:set var="isRequired" value="${empty required ? true : required}" />

<div class="form-group">
    <c:if test="${not empty label}">
        <label for="${name}"><c:out value="${label}" /></label>
    </c:if>

    <c:choose>
        <c:when test="${inputType == 'email'}">
            <input type="email" id="${name}" name="${name}" <c:if test="${isRequired}">required="required"</c:if>
                   pattern="[^@\s]+@[^@\s]+\.[^@\s]+" />
        </c:when>
        <c:when test="${inputType == 'number'}">
            <input type="number" id="${name}" name="${name}" <c:if test="${isRequired}">required="required"</c:if> step="any" />
        </c:when>
        <c:when test="${inputType == 'password'}">
            <input type="password" id="${name}" name="${name}" <c:if test="${isRequired}">required="required"</c:if> minlength="8" />
        </c:when>
        <c:when test="${inputType == 'date'}">
            <input type="date" id="${name}" name="${name}" <c:if test="${isRequired}">required="required"</c:if> />
        </c:when>
        <c:when test="${inputType == 'time'}">
            <input type="time" id="${name}" name="${name}" <c:if test="${isRequired}">required="required"</c:if> />
        </c:when>
        <c:when test="${inputType == 'datetime-local'}">
            <input type="datetime-local" id="${name}" name="${name}" <c:if test="${isRequired}">required="required"</c:if> />
        </c:when>
        <c:otherwise>
            <input type="text" id="${name}" name="${name}" <c:if test="${isRequired}">required="required"</c:if> />
        </c:otherwise>
    </c:choose>

    <c:if test="${not empty errorMessage}">
        <span class="error-message"><c:out value="${errorMessage}" /></span>
    </c:if>
</div>
