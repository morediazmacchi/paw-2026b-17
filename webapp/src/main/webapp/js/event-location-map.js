// Mapa de "confirmar el lugar" en publicar/editar evento (paso 3 del wizard,
// ver publish.jsp/editar.jsp): un click pone o mueve un pin, que carga los
// inputs ocultos #latitud/#longitud. Es puramente opcional -- la direccion en
// texto sigue siendo el dato obligatorio; si nadie toca el mapa, los dos
// campos viajan vacios y el evento se publica igual que antes de esta feature.
document.addEventListener("DOMContentLoaded", function () {
    var mapContainer = document.getElementById("location-map");
    if (!mapContainer || typeof L === "undefined") {
        return;
    }

    var latInput = document.getElementById("latitud");
    var lonInput = document.getElementById("longitud");

    // Centro de Buenos Aires, para cuando el evento todavia no tiene pin.
    var DEFAULT_LAT = -34.6037;
    var DEFAULT_LON = -58.3816;
    var DEFAULT_ZOOM = 12;
    var PIN_ZOOM = 16;

    var hasPin = latInput.value !== "" && lonInput.value !== "";
    var startLat = hasPin ? parseFloat(latInput.value) : DEFAULT_LAT;
    var startLon = hasPin ? parseFloat(lonInput.value) : DEFAULT_LON;

    var map = L.map(mapContainer).setView([startLat, startLon], hasPin ? PIN_ZOOM : DEFAULT_ZOOM);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        attribution: "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>",
        maxZoom: 19
    }).addTo(map);

    var marker = hasPin ? L.marker([startLat, startLon]).addTo(map) : null;

    function setPin(latlng) {
        // 6 decimales: mismo redondeo que la columna NUMERIC(9,6) de la base
        // (ver schema.sql), asi lo que se ve en el mapa es lo que se guarda.
        latInput.value = latlng.lat.toFixed(6);
        lonInput.value = latlng.lng.toFixed(6);
        if (marker) {
            marker.setLatLng(latlng);
        } else {
            marker = L.marker(latlng).addTo(map);
        }
    }

    map.on("click", function (event) {
        setPin(event.latlng);
    });

    // El mapa nace adentro de un paso escondido del wizard (data-step="3"
    // con el atributo "hidden"): Leaflet mide el contenedor al crearlo, y en
    // ese momento mide 0x0, asi que las tiles quedan mal encuadradas hasta
    // que alguien le avisa que vuelva a medir. Un MutationObserver sobre el
    // atributo "hidden" del paso es ese aviso, sin tener que tocar
    // event-wizard.js (que no sabe nada de este mapa).
    var step = mapContainer.closest(".wizard-step");
    if (step) {
        new MutationObserver(function () {
            if (!step.hidden) {
                map.invalidateSize();
            }
        }).observe(step, {attributes: true, attributeFilter: ["hidden"]});
    }
});
