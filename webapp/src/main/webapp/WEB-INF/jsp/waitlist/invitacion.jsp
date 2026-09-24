<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="waitlist.invitation.pageTitle"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>

<paw:navbar>
    <spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>"><c:out value="${backToEventsLabel}"/></a>
</paw:navbar>

<main class="page page-narrow">
    <c:choose>

        <%-- 1) Acabamos de contestar: gana sobre cualquier otro estado, porque
              despues de aceptar la invitacion figura "ya usada" y el cartel
              tiene que decir "listo", no "ya habias contestado". --%>
        <c:when test="${not empty resultado}">
            <h1><spring:message code="waitlist.invitation.pageTitle"/></h1>
            <p class="alert">
                <spring:message code="waitlist.result.${resultado}"
                                text="${resultado}" htmlEscape="true"/>
            </p>
            <c:if test="${not empty event}">
                <a class="btn btn-medium btn-accent" href="<c:url value='/eventos/${event.id}'/>">
                    <spring:message code="waitlist.invitation.viewEvent"/>
                </a>
            </c:if>
        </c:when>

        <%-- 2) Esta cuenta no tiene invitacion a este evento: link reenviado, otra cuenta, o invitacion ya borrada. --%>
        <c:when test="${empty event}">
            <h1><spring:message code="waitlist.invitation.pageTitle"/></h1>
            <p class="empty-state"><spring:message code="waitlist.invitation.notFound"/></p>
            <a class="btn btn-medium" href="<c:url value='/eventos'/>">
                <spring:message code="common.nav.backToEvents"/>
            </a>
        </c:when>

        <%-- 3) Los tres motivos por los que ya no se puede contestar. --%>
        <c:when test="${not pending or eventPassed or expired}">
            <h1><c:out value="${event.title}"/></h1>
            <p class="empty-state">
                <c:choose>
                    <c:when test="${not pending}"><spring:message code="waitlist.invitation.alreadyUsed"/></c:when>
                    <c:when test="${eventPassed}"><spring:message code="waitlist.invitation.eventPassed"/></c:when>
                    <c:otherwise><spring:message code="waitlist.invitation.expired"/></c:otherwise>
                </c:choose>
            </p>
            <a class="btn btn-medium" href="<c:url value='/eventos/${event.id}'/>">
                <spring:message code="waitlist.invitation.viewEvent"/>
            </a>
        </c:when>

        <%-- 4) El caso bueno: todavia puede decidir. --%>
        <c:otherwise>
            <h1><spring:message code="waitlist.invitation.heading"/></h1>
            <p><spring:message code="waitlist.invitation.intro"/></p>

            <div class="form-card">
                <h2><c:out value="${event.title}"/></h2>
                <p>
                    <fmt:formatDate value="${event.eventDate}" pattern="EEEE dd/MM/yyyy HH:mm"/>&nbsp;hs
                    &middot; <c:out value="${event.address}"/>
                </p>
                <p class="detail-rsvp-note">
                    <fmt:formatDate value="${invitation.expiresAt}" pattern="dd/MM/yyyy HH:mm" var="deadline"/>
                    <spring:message code="waitlist.invitation.deadline" arguments="${deadline}" htmlEscape="true"/>
                </p>

                <%-- Dos formularios separados: cada boton es su propio POST.
                     El GET de arriba no cambio nada; estos si. --%>
                <div class="label-row">
                    <form action="<c:url value='/invitacion/${eventId}/aceptar'/>" method="post" novalidate="novalidate">
                        <spring:message code="waitlist.invitation.accept" var="acceptLabel"/>
                        <paw:button text="${acceptLabel}" cssClass="btn-accent" />
                    </form>
                    <form action="<c:url value='/invitacion/${eventId}/rechazar'/>" method="post" novalidate="novalidate">
                        <spring:message code="waitlist.invitation.decline" var="declineLabel"/>
                        <paw:button text="${declineLabel}" cssClass="btn-danger" />
                    </form>
                </div>
            </div>
        </c:otherwise>

    </c:choose>
</main>

</body>
</html>
