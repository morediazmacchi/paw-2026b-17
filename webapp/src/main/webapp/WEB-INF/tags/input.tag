<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="name" required="true" %>
<%@ attribute name="label" required="false" %>
<%@ attribute name="type" required="false" %>
<%@ attribute name="errorMessage" required="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:set var="inputType" value="${empty type ? 'text' : type}" />

<div class="form-group">
    <c:if test="${not empty label}">
        <label for="${name}"><c:out value="${label}" /></label>
    </c:if>

    <c:choose>
        <c:when test="${inputType == 'email'}">
            <input type="email" id="${name}" name="${name}" required="required"
                   pattern="[^@\s]+@[^@\s]+\.[^@\s]+" />
        </c:when>
        <c:when test="${inputType == 'number'}">
            <input type="number" id="${name}" name="${name}" required="required" step="any" />
        </c:when>
        <c:when test="${inputType == 'password'}">
            <input type="password" id="${name}" name="${name}" required="required" minlength="8" />
        </c:when>
        <c:otherwise>
            <input type="text" id="${name}" name="${name}" required="required" />
        </c:otherwise>
    </c:choose>

    <c:if test="${not empty errorMessage}">
        <span class="error-message"><c:out value="${errorMessage}" /></span>
    </c:if>
</div>
