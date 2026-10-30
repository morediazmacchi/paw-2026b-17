<%@ tag language="java" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="value" required="true" type="java.lang.Number" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%-- Un precio en pesos, como lo escribe cada idioma. El numero lo formatea fmt:formatNumber
     segun el idioma del pedido (1.500 / 1,500) y donde va el simbolo lo dice la clave
     format.price de messages*.properties ("${0}" en espanol, "ARS {0}" en ingles), en vez de
     un "$" pegado a mano en cada JSP. Uso: <paw:price value="${event.price}"/> --%>
<fmt:formatNumber value="${value}" type="number" maxFractionDigits="0" var="amount"/>
<spring:message code="format.price" htmlEscape="true"><spring:argument value="${amount}"/></spring:message>
