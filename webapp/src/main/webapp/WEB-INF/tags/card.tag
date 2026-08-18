<%@ tag language="java" pageEncoding="UTF-8" %>
<%@ attribute name="nombre" required="true" %>
<%@ attribute name="precio" required="true" type="java.lang.Double" %>
<%@ attribute name="fecha" required="true" type="java.util.Date" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<div class="card">
    <h3 class="card-nombre"><c:out value="${nombre}" /></h3>
    <p class="card-precio"><fmt:formatNumber value="${precio}" type="currency" /></p>
    <p class="card-fecha"><fmt:formatDate value="${fecha}" pattern="dd/MM/yyyy" /></p>
</div>
