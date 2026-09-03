<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>git clone https://mdiazmacchi@bitbucket.org/itba/paw-2026b-17.git
    <meta charset="UTF-8">
    <title>Demo custom tags</title>
</head>
<body>

<h1>Botones</h1>
<paw:button text="Guardar" />
<paw:button text="Cancelar" size="small" cssClass="secondary" />
<paw:button text="Eliminar" size="large" disabled="${true}" />

<h1>Cards</h1>
<paw:card nombre="Notebook Lenovo" precio="${599.99}" fecha="${hoy}" />
<paw:card nombre="Mouse inalámbrico" precio="${15.5}" fecha="${hoy}" />

<h1>Inputs</h1>
<paw:input name="email" label="Email" type="email" />
<paw:input name="edad" label="Edad" type="number"  />
<paw:input name="password" label="Contraseña" type="password" />

</body>
</html>
