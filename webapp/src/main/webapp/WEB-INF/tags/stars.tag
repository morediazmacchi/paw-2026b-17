<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="rating" required="true" type="java.lang.Double" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>

<%-- 5 iconos, llenos hasta "rating" (redondeado hacia abajo, sin medias
     estrellas: alcanza para un promedio o una calificacion entera). --%>
<span class="stars">
    <c:forEach var="i" begin="1" end="5">
        <c:choose>
            <c:when test="${i <= rating}">
                <paw:icon name="star-filled"/>
            </c:when>
            <c:otherwise>
                <paw:icon name="star"/>
            </c:otherwise>
        </c:choose>
    </c:forEach>
</span>
