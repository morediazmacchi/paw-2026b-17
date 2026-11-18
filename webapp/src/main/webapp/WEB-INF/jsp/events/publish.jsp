<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout titleCode="event.publish.title">
<jsp:attribute name="head">
    <%-- Formulario: despues de mandarlo, "Volver" desde la pagina siguiente lo saltea (ver back-link.js). --%>
    <meta name="paw-history-skip" content="true"/>
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
          integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin=""/>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
            integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
    <script src="<c:url value='/js/quick-options.js'/>"></script>
    <script src="<c:url value='/js/digits-only.js'/>"></script>
    <script src="<c:url value='/js/event-location-map.js'/>"></script>
    <script src="<c:url value='/js/image-picker.js'/>"></script>
    <%-- data-open-modal/data-close-modal del almanaque de fecha (ver event-wizard.js). --%>
    <script src="<c:url value='/js/modal.js'/>"></script>
    <script src="<c:url value='/js/event-wizard.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<main class="page page-narrow">
    <div class="wizard-card">
        <h1><spring:message code="event.publish.heading"/></h1>

        <%-- Cuerpo del wizard: compartido con editar.jsp, ver _eventWizardFields.jspf. --%>
        <c:url var="formAction" value="/eventos/publicar"/>
        <c:url var="cancelHref" value="/eventos"/>
        <spring:message code="event.publish.submitButton" var="submitButtonLabel"/>
        <%@ include file="_eventWizardFields.jspf" %>
    </div>
</main>
</jsp:body>
</paw:layout>
