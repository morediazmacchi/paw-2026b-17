<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="fieldId" required="true" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>

<%-- Radios ocultos + labels con el icono de estrella: el truco es que el orden
     en el HTML va 5,4,3,2,1 y CSS lo da vuelta (flex-direction: row-reverse)
     para que se vea 1..5 de izquierda a derecha. Asi el selector "~" (hermanos
     que vienen DESPUES en el HTML) ilumina las estrellas de la seleccionada
     hacia la izquierda, que es justo lo que se ve a la derecha visualmente.
     Todas usan el icono "relleno": el color (gris vs acento) lo pone el CSS
     segun :hover/:checked, no hace falta cambiar de icono por JS. --%>
<div class="star-picker">
    <input type="radio" id="${fieldId}-star5" name="rating" value="5" required="required"/>
    <label for="${fieldId}-star5"><paw:icon name="star-filled"/></label>
    <input type="radio" id="${fieldId}-star4" name="rating" value="4" required="required"/>
    <label for="${fieldId}-star4"><paw:icon name="star-filled"/></label>
    <input type="radio" id="${fieldId}-star3" name="rating" value="3" required="required"/>
    <label for="${fieldId}-star3"><paw:icon name="star-filled"/></label>
    <input type="radio" id="${fieldId}-star2" name="rating" value="2" required="required"/>
    <label for="${fieldId}-star2"><paw:icon name="star-filled"/></label>
    <input type="radio" id="${fieldId}-star1" name="rating" value="1" required="required"/>
    <label for="${fieldId}-star1"><paw:icon name="star-filled"/></label>
</div>
<label class="star-picker-zero">
    <input type="radio" id="${fieldId}-star0" name="rating" value="0" required="required"/>
    <spring:message code="auth.registrations.zeroRatingOption"/>
</label>
