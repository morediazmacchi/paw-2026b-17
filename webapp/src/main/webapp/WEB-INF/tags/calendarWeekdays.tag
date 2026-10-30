<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%-- Encabezado del almanaque: las iniciales de los dias, de lunes a domingo (la grilla que
     arman event-wizard.js y date-filter-calendar.js tambien empieza en lunes). Lo usan los
     tres almanaques: Desde/Hasta del filtro de /eventos y la fecha de publicar/editar. --%>
<div class="calendar-weekdays">
    <span><spring:message code="calendar.weekday.mon"/></span><span><spring:message code="calendar.weekday.tue"/></span><span><spring:message code="calendar.weekday.wed"/></span><span><spring:message code="calendar.weekday.thu"/></span><span><spring:message code="calendar.weekday.fri"/></span><span><spring:message code="calendar.weekday.sat"/></span><span><spring:message code="calendar.weekday.sun"/></span>
</div>
