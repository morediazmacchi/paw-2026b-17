<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>

<%-- Distintivo "Tu evento" de las listas de moderacion del admin (eventos pendientes, reportes, seguimiento):
     marca las filas cuyo evento es del admin que mira, que no puede moderar. Quien lo usa decide cuando
     mostrarlo con Event.isCreatedBy / ReportTarget.isCreatedBy (HasCreator), la misma regla que esconde
     los botones. --%>
<span class="status-pill status-pill-own"><spring:message code="admin.ownEventBadge"/></span>
