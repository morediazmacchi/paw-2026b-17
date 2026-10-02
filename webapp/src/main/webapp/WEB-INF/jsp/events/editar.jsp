<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="event.edit.title"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
          integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin=""/>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
            integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
    <script src="<c:url value='/js/quick-options.js'/>"></script>
    <script src="<c:url value='/js/event-location-map.js'/>"></script>
    <script src="<c:url value='/js/image-picker.js'/>"></script>
    <%-- data-open-modal/data-close-modal del almanaque de fecha (ver event-wizard.js). --%>
    <script src="<c:url value='/js/modal.js'/>"></script>
    <script src="<c:url value='/js/event-wizard.js'/>"></script>
</head>
<body>

<paw:navbar>
    <spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>"><c:out value="${backToEventsLabel}"/></a>
</paw:navbar>

<main class="page page-narrow">
    <div class="wizard-card">
        <h1><spring:message code="event.edit.heading"/></h1>

        <%-- Mismo wizard de 5 pasos que publish.jsp, con los campos precargados
             con los valores actuales del evento (eventForm viene armado por
             EventController.editarForm()/editarFormWithErrors()). Cuerpo
             compartido con publish.jsp, ver _eventWizardFields.jspf. --%>
        <c:url var="formAction" value="/eventos/${eventId}/editar"/>
        <c:url var="cancelHref" value="/perfil/eventos"/>
        <spring:message code="event.edit.submitButton" var="submitButtonLabel"/>
        <spring:message code="event.edit.currentImageHint" var="currentImageHintLabel"/>
        <%@ include file="_eventWizardFields.jspf" %>
    </div>
</main>

</body>
</html>
