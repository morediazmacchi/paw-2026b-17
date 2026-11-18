<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="text" required="true" %>
<%@ attribute name="size" required="false" %>
<%@ attribute name="cssClass" required="false" %>
<%@ attribute name="disabled" required="false" type="java.lang.Boolean" %>
<%@ attribute name="type" required="false" %>
<%-- id del modal a abrir (ver modal.js): si se pasa, el boton es type="button" (no manda ningun
     form) y queda igual que uno armado a mano con data-open-modal, pero con el mismo tag que el
     resto de los botones de la pantalla. --%>
<%@ attribute name="openModal" required="false" %>
<%-- true = este boton cierra el modal en el que esta (data-close-modal, ver modal.js), por ejemplo
     el "Volver"/"Cancelar" de un modal de confirmacion: mismo tag que el boton que confirma, para
     que los dos tengan exactamente el mismo formato. --%>
<%@ attribute name="closeModal" required="false" type="java.lang.Boolean" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:set var="btnSize" value="${empty size ? 'medium' : size}" />
<c:set var="btnClass" value="btn btn-${btnSize} ${cssClass}" />
<c:set var="btnType" value="${(not empty openModal or closeModal) ? 'button' : (empty type ? 'submit' : type)}" />

<button type="${btnType}" class="${btnClass}"
        <c:if test="${not empty openModal}">data-open-modal="${openModal}"</c:if>
        <c:if test="${closeModal}">data-close-modal</c:if>
        <c:if test="${disabled}">disabled="disabled"</c:if>><c:out value="${text}" /></button>
