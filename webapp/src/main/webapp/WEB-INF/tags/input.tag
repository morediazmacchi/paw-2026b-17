<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="name" required="true" %>
<%@ attribute name="label" required="false" %>
<%@ attribute name="type" required="false" %>
<%@ attribute name="errorMessage" required="false" %>
<%@ attribute name="required" required="false" type="java.lang.Boolean" %>
<%@ attribute name="min" required="false" %>
<%@ attribute name="value" required="false" %>
<%@ attribute name="maxLength" required="false" %>
<%-- Texto gris de ejemplo que se ve mientras el campo esta vacio (ej: "10000"). --%>
<%@ attribute name="placeholder" required="false" %>
<%-- true = solo digitos (cupo, precio en pesos enteros): ver js/digits-only.js.
     Se dibuja como texto y no como type="number" porque el navegador deja
     escribir "1.000" o "1e3" en un campo numerico y recien el servidor lo rechaza. --%>
<%@ attribute name="digitsOnly" required="false" type="java.lang.Boolean" %>
<%-- Texto de un atajo opcional al lado de la etiqueta (ej: "Gratis", "Sin limite").
     Si no se pasa, no se dibuja ningun boton y el campo queda como siempre. --%>
<%@ attribute name="toggleText" required="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>

<c:set var="inputType" value="${empty type ? 'text' : type}" />
<c:set var="isRequired" value="${empty required ? true : required}" />

<div class="form-group">
    <div class="label-row">
        <c:if test="${not empty label}">
            <label for="${name}"><c:out value="${label}" /></label>
        </c:if>
        <c:if test="${not empty toggleText}">
            <button type="button" class="chip-toggle" data-target="${name}"
                    aria-pressed="false"><c:out value="${toggleText}" /></button>
        </c:if>
    </div>

    <c:choose>
        <c:when test="${digitsOnly}">
            <input type="text" id="${name}" name="${name}" value="${fn:escapeXml(value)}"
                   inputmode="numeric" autocomplete="off" data-digits-only="true"
                   <c:if test="${isRequired}">required="required"</c:if>
                   <c:if test="${not empty maxLength}">maxlength="${maxLength}"</c:if>
                   <c:if test="${not empty placeholder}">placeholder="${fn:escapeXml(placeholder)}"</c:if> />
        </c:when>
        <c:when test="${inputType == 'email'}">
            <input type="email" id="${name}" name="${name}" value="${fn:escapeXml(value)}"
                   <c:if test="${isRequired}">required="required"</c:if>
                   pattern="[^@\s]+@[^@\s]+\.[^@\s]+" <c:if test="${not empty maxLength}">maxlength="${maxLength}"</c:if> />
        </c:when>
        <c:when test="${inputType == 'number'}">
            <input type="number" id="${name}" name="${name}" value="${fn:escapeXml(value)}"
                   <c:if test="${isRequired}">required="required"</c:if>
                   <c:if test="${not empty min}">min="${min}"</c:if> step="any" />
        </c:when>
        <c:when test="${inputType == 'password'}">
            <spring:message code="common.showPassword" var="showPasswordLabel"/>
            <div class="password-field">
                <input type="password" id="${name}" name="${name}" <c:if test="${isRequired}">required="required"</c:if> minlength="8" <c:if test="${not empty maxLength}">maxlength="${maxLength}"</c:if> />
                <button type="button" class="password-toggle" data-toggle-password="${name}" aria-label="${showPasswordLabel}">
                    <span class="password-icon-show"><paw:icon name="eye"/></span>
                    <span class="password-icon-hide" hidden><paw:icon name="eye-off"/></span>
                </button>
            </div>
        </c:when>
        <c:when test="${inputType == 'date'}">
            <input type="date" id="${name}" name="${name}" value="${fn:escapeXml(value)}"
                   <c:if test="${isRequired}">required="required"</c:if> />
        </c:when>
        <c:when test="${inputType == 'time'}">
            <input type="time" id="${name}" name="${name}" value="${fn:escapeXml(value)}"
                   <c:if test="${isRequired}">required="required"</c:if> />
        </c:when>
        <c:when test="${inputType == 'datetime-local'}">
            <input type="datetime-local" id="${name}" name="${name}" value="${fn:escapeXml(value)}"
                   <c:if test="${isRequired}">required="required"</c:if> />
        </c:when>
        <c:otherwise>
            <input type="text" id="${name}" name="${name}" value="${fn:escapeXml(value)}"
                   <c:if test="${isRequired}">required="required"</c:if> <c:if test="${not empty maxLength}">maxlength="${maxLength}"</c:if>
                   <c:if test="${not empty placeholder}">placeholder="${fn:escapeXml(placeholder)}"</c:if> />
        </c:otherwise>
    </c:choose>

    <c:if test="${not empty toggleText}">
        <%-- Lo que se ve en lugar del campo cuando el atajo esta activo. El input
             de arriba sigue existiendo, oculto, con el 0 adentro: el atributo
             hidden esconde pero NO impide que el valor viaje en el POST. --%>
        <div class="preset-display" hidden><c:out value="${toggleText}" /></div>
    </c:if>

    <c:if test="${not empty errorMessage}">
        <span class="error-message"><c:out value="${errorMessage}" /></span>
    </c:if>
</div>
