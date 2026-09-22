// Mapa de solo lectura en el detalle de un evento (ver detail.jsp): muestra
// el pin que el creador marco al publicar/editar (event-location-map.js).
// No hay interaccion mas alla de mover/hacer zoom en el mapa mismo -- nada
// que clickear para mover el pin, a diferencia del mapa del wizard.
document.addEventListener("DOMContentLoaded", function () {
    var mapContainer = document.getElementById("detail-map");
    if (!mapContainer || typeof L === "undefined") {
        return;
    }

    var lat = parseFloat(mapContainer.getAttribute("data-lat"));
    var lon = parseFloat(mapContainer.getAttribute("data-lon"));
    if (isNaN(lat) || isNaN(lon)) {
        return;
    }

    var map = L.map(mapContainer, {scrollWheelZoom: false}).setView([lat, lon], 16);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        attribution: "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>",
        maxZoom: 19
    }).addTo(map);
    L.marker([lat, lon]).addTo(map);
});
