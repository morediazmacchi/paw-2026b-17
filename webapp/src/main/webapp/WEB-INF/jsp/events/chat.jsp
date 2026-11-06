<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<paw:layout title="${event.title}">
<jsp:attribute name="head">
    <script src="${pageContext.request.contextPath}/js/event-chat.js" defer></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<spring:message code="chat.sendError" var="sendErrorText"/>
<spring:message code="chat.removedNote" var="removedText"/>

<main class="chat-page">
    <header class="chat-header">
        <spring:message code="chat.back" var="chatBackLabel" htmlEscape="true"/>
        <a class="chat-back" href="<c:url value='/eventos/${event.id}'/>" data-history-back aria-label="${chatBackLabel}">
            <paw:icon name="chevron-left"/>
        </a>
        <div class="chat-header-title">
            <h1><c:out value="${event.title}"/></h1>
            <p><spring:message code="chat.subtitle"/></p>
        </div>
        <div class="chat-actions">
            <a class="chat-pill" href="<c:url value='/eventos/${event.id}'/>"><spring:message code="chat.viewEvent"/></a>
            <form action="<c:url value='/eventos/${event.id}/chat/salir'/>" method="post" novalidate="novalidate">
                <button type="submit" class="chat-pill chat-pill-leave"><spring:message code="chat.leave"/></button>
            </form>
        </div>
    </header>

    <div class="chat-box" id="chat-box"
         data-poll-url="<c:url value='/eventos/${event.id}/chat/mensajes'/>"
         data-last-id="${lastId}"
         data-error-text="<c:out value='${sendErrorText}'/>"
         data-removed-text="<c:out value='${removedText}'/>">
        <p class="chat-empty" id="chat-empty"${empty messages ? '' : ' hidden'}>
            <spring:message code="chat.empty"/>
        </p>
        <div id="chat-messages"><jsp:include page="/WEB-INF/jsp/events/chatMessages.jsp"/></div>
    </div>

    <c:choose>
        <c:when test="${chatClosed}">
            <p class="alert chat-closed"><spring:message code="chat.closedNote"/></p>
        </c:when>
        <c:otherwise>
            <p class="chat-error" id="chat-error" role="alert" hidden></p>
            <form class="chat-composer" id="chat-form" action="<c:url value='/eventos/${event.id}/chat/mensajes'/>" method="post">
                <spring:message code="chat.inputPlaceholder" var="placeholder"/>
                <textarea name="text" id="chat-input" rows="1" maxlength="1000" required="required"
                          placeholder="<c:out value='${placeholder}'/>" autocomplete="off"></textarea>
                <spring:message code="chat.send" var="sendLabel"/>
                <button type="submit" class="btn btn-accent chat-send" aria-label="<c:out value='${sendLabel}'/>"
                        title="<c:out value='${sendLabel}'/>">
                    <%-- Avioncito de papel (el texto "Enviar" queda como aria-label para lectores de pantalla) --%>
                    <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true">
                        <path d="M2.4 20.6 22 12 2.4 3.4l-.01 6.67L16 12 2.39 13.93z"/>
                    </svg>
                </button>
            </form>
        </c:otherwise>
    </c:choose>

    <%-- Los datos del evento, con el mismo bloque que usa el detalle (fecha, hora, precio,
         cupo, estacionamiento). --%>
    <h2 class="detail-section-heading chat-meta-heading"><spring:message code="event.detail.aboutHeading"/></h2>
    <div class="detail-meta-strip chat-meta-strip">
        <div class="detail-meta-item">
            <span class="detail-meta-icon"><paw:icon name="calendar"/></span>
            <div>
                <span class="detail-meta-label"><spring:message code="event.detail.dateLabelSmall"/></span>
                <span class="detail-meta-value"><paw:date value="${event.eventDate}" format="longDate"/></span>
            </div>
        </div>
        <div class="detail-meta-item">
            <span class="detail-meta-icon"><paw:icon name="clock"/></span>
            <div>
                <span class="detail-meta-label"><spring:message code="event.detail.timeLabelSmall"/></span>
                <span class="detail-meta-value"><paw:date value="${event.eventDate}" format="time"/></span>
            </div>
        </div>
        <div class="detail-meta-item">
            <span class="detail-meta-icon"><paw:icon name="tag"/></span>
            <div>
                <span class="detail-meta-label"><spring:message code="event.detail.priceLabelSmall"/></span>
                <span class="detail-meta-value">
                    <c:choose>
                        <c:when test="${event.free}"><spring:message code="event.price.free"/></c:when>
                        <c:otherwise><paw:price value="${event.price}"/></c:otherwise>
                    </c:choose>
                </span>
            </div>
        </div>
        <div class="detail-meta-item">
            <span class="detail-meta-icon"><paw:icon name="people"/></span>
            <div>
                <span class="detail-meta-label"><spring:message code="event.detail.capacityLabelSmall"/></span>
                <span class="detail-meta-value">
                    <c:choose>
                        <c:when test="${not empty event.maxCapacity}"><spring:message code="event.detail.capacityValue" arguments="${event.maxCapacity}"/></c:when>
                        <c:otherwise><spring:message code="event.detail.capacityUnlimited"/></c:otherwise>
                    </c:choose>
                </span>
            </div>
        </div>
        <div class="detail-meta-item">
            <span class="detail-meta-icon"><paw:icon name="car"/></span>
            <div>
                <span class="detail-meta-label"><spring:message code="event.detail.parkingLabelSmall"/></span>
                <span class="detail-meta-value">
                    <c:choose>
                        <c:when test="${event.parking}"><spring:message code="common.yes"/></c:when>
                        <c:otherwise><spring:message code="common.no"/></c:otherwise>
                    </c:choose>
                </span>
            </div>
        </div>
    </div>
</main>
</jsp:body>
</paw:layout>
