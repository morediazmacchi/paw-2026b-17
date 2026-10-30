<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<%-- Fragmento (sin layout): lo incluye chat.jsp para los mensajes iniciales y lo devuelve
     tal cual EventChatController.messages() para el polling. Todo lo que escribio un
     usuario va con c:out: el nombre y el texto se escapan. --%>
<c:forEach var="message" items="${messages}">
    <c:set var="mine" value="${message.userId == currentUserId}"/>
    <div class="chat-msg${mine ? ' chat-msg-mine' : ''}" data-id="${message.id}">
        <c:if test="${not mine}">
            <span class="chat-msg-author"><c:out value="${message.senderName}"/></span>
        </c:if>
        <p class="chat-msg-body"><c:out value="${message.body}"/></p>
        <span class="chat-msg-time"><paw:date value="${message.createdAt}" format="timeShort"/></span>
    </div>
</c:forEach>
