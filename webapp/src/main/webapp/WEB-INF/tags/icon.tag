<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="name" required="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<%-- Iconos de linea simples (estilo Feather), monocromos via currentColor: heredan
     el color del texto que los rodea, sin depender de como cada SO/navegador
     dibuja los emoji. --%>
<c:choose>
    <c:when test="${name == 'calendar'}">
        <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="5" width="18" height="16" rx="2"/>
            <line x1="16" y1="3" x2="16" y2="7"/>
            <line x1="8" y1="3" x2="8" y2="7"/>
            <line x1="3" y1="10" x2="21" y2="10"/>
        </svg>
    </c:when>
    <c:when test="${name == 'clock'}">
        <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="9"/>
            <polyline points="12 7 12 12 16 14"/>
        </svg>
    </c:when>
    <c:when test="${name == 'tag'}">
        <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20.59 13.41 13.42 20.58a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82Z"/>
            <circle cx="6.5" cy="6.5" r="0.5" fill="currentColor"/>
        </svg>
    </c:when>
    <c:when test="${name == 'people'}">
        <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
        </svg>
    </c:when>
    <c:when test="${name == 'pin'}">
        <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0Z"/>
            <circle cx="12" cy="10" r="3"/>
        </svg>
    </c:when>
</c:choose>
