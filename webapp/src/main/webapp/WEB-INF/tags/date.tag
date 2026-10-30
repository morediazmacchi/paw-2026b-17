<%@ tag language="java" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="value" required="true" type="java.util.Date" %>
<%@ attribute name="format" required="true" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%-- Una fecha con el formato del idioma del pedido. El patron no esta escrito aca ni en
     cada JSP: sale de la clave format.<format> de messages*.properties (en espanol
     dd/MM/yyyy y "hs", en ingles MM/dd/yyyy y AM/PM). Uso:

       <paw:date value="${message.createdAt}" format="dateTime"/>

     format: date, dateTime, dateTimeTable, weekdayDateTime, longDate, longDateTime,
     shortDateTime, time o timeShort. Si hace falta el texto en una variable (por ejemplo,
     para pasarlo como argumento de un spring:message), se envuelve en un c:set con cuerpo. --%>
<spring:message code="format.${format}" var="pattern"/>
<fmt:formatDate value="${value}" pattern="${pattern}"/>
