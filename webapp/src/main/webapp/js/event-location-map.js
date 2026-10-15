// Mapa de "confirmar el lugar" en publicar/editar evento (paso 3 del wizard,
// ver publish.jsp/editar.jsp). Conecta la direccion en texto con el mapa
// (antes eran dos campos totalmente independientes), en dos modos EXCLUYENTES
// que la persona elige a mano con los radio #modo-direccion-geo/-manual
// (nunca se pasa de uno a otro solo):
//
// MODO "con geolocalizacion" (default): el boton "Buscar direccion"
// geocodifica #address contra Nominatim (el geocoder gratuito de
// OpenStreetMap, mismo proveedor que las tiles de abajo) y trae hasta 5
// candidatos -- nunca se toma el primero a ciegas, se muestran todos para que
// la persona elija el que es. Al elegir uno se completan #latitude/#longitude Y
// se DEDUCE el barrio a partir del "suburb" de esa sugerencia puntual
// (normalizado y comparado contra los 48 barrios de window.PAW_BARRIOS, que
// la JSP arma desde Barrio.values() -- una sola fuente de verdad, no una
// lista duplicada a mano aca). El mapa es de solo lectura en este modo.
// Si no se puede deducir el barrio (pasa con parques/reservas grandes: no
// tienen "suburb" en Nominatim), el pin se pone igual pero el barrio NO se
// toca solo -- se avisa nada mas. Si la persona quiere resolverlo, tiene que
// pasarse ella misma al otro modo (nunca es automatico).
//
// MODO "marcar en el mapa": un click en el mapa pone el pin Y geocodifica
// AL REVES ese punto contra Nominatim (/reverse, en vez de /search) para
// completar #address sola -- mismo criterio de barrio que el modo de
// arriba (se deduce del "suburb", y si no se puede, no se toca el <select>,
// la persona lo elige a mano). El <select id="neighborhood"> queda siempre
// habilitado en este modo por si Nominatim no identifica el barrio, o para
// corregirlo. Si falla la geocodificacion inversa (sin conexion, Nominatim
// caido), el pin igual queda puesto -- la direccion se completa a mano,
// ni mejor ni peor que antes de que existiera esto.
//
// #location-manual (hidden) guarda si el pin ACTUAL se puso a mano o se geocodifico
// -- lo pisa la accion real que lo puso ahi (click en el mapa, o elegir una
// sugerencia), no el radio de modo: cambiar de modo sin tocar el mapa/buscar
// de nuevo no reescribe esto, para no mentir sobre el origen del pin que ya
// estaba puesto. El detalle del evento lo usa para avisar "puede no ser
// exacto" (ver Event.locationManual).
document.addEventListener("DOMContentLoaded", function () {
    var mapContainer = document.getElementById("location-map");
    if (!mapContainer || typeof L === "undefined") {
        return;
    }

    var latInput = document.getElementById("latitude");
    var lonInput = document.getElementById("longitude");
    var pinManualInput = document.getElementById("location-manual");
    var addressInput = document.getElementById("address");
    var searchButton = document.getElementById("geocode-address");
    var statusEl = document.getElementById("geocode-status");
    var suggestionsEl = document.getElementById("geocode-suggestions");
    var barrioSelect = document.getElementById("neighborhood");
    var barrioDisplay = document.getElementById("neighborhood-display");
    var barrioPill = document.getElementById("neighborhood-pill");
    var barrioPillHint = document.getElementById("barrio-pill-hint");
    var geoSearchPanel = document.getElementById("geo-search-panel");
    var manualPanel = document.getElementById("manual-panel");
    var mapHintGeo = document.getElementById("map-hint-geo");
    var mapHintManual = document.getElementById("map-hint-manual");
    var modeGeoRadio = document.getElementById("modo-direccion-geo");
    var modeManualRadio = document.getElementById("modo-direccion-manual");

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
    // Solo true en modo "marcar en el mapa": ahi, y solo ahi, un click en el
    // mapa mueve el pin. Arranca segun cual de los dos radio este marcado
    // al cargar la pagina (por ej. al reabrir "Editar" un evento que ya tenia
    // el pin puesto a mano).
    var manualPinEnabled = !!(modeManualRadio && modeManualRadio.checked);

    function setPin(lat, lon) {
        // 6 decimales: mismo redondeo que la columna NUMERIC(9,6) de la base
        // (ver schema.sql), asi lo que se ve en el mapa es lo que se guarda.
        latInput.value = lat.toFixed(6);
        lonInput.value = lon.toFixed(6);
        var latlng = {lat: lat, lng: lon};
        if (marker) {
            marker.setLatLng(latlng);
        } else {
            marker = L.marker(latlng).addTo(map);
        }
        map.setView(latlng, PIN_ZOOM);
    }

    // Geocodificacion INVERSA (lat/lon -> direccion), para que marcar en el
    // mapa complete #address sola en vez de pedirla tipeada aparte -- si no,
    // no tiene sentido ofrecer las dos formas de cargar el lugar. Mismo
    // formato de respuesta que /search (result.address), asi que reusa
    // formatCleanAddress()/applyBarrioMatch() de abajo.
    function reverseGeocodeAndFill(lat, lon) {
        var url = "https://nominatim.openstreetmap.org/reverse"
            + "?format=jsonv2&addressdetails=1&lat=" + lat + "&lon=" + lon;
        fetch(url)
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("reverse geocode failed: " + response.status);
                }
                return response.json();
            })
            .then(function (result) {
                if (!result || !result.address) {
                    return;
                }
                addressInput.value = formatCleanAddress(result.address, result.display_name);
                applyBarrioMatch(result.address.suburb, barrioSelect, barrioDisplay);
            })
            .catch(function () {
                // Sin conexion o Nominatim caido: el pin ya quedo puesto igual,
                // la persona completa la direccion a mano, como antes de esto.
            });
    }

    map.on("click", function (event) {
        if (!manualPinEnabled) {
            return;
        }
        if (pinManualInput) {
            pinManualInput.value = "true";
        }
        setPin(event.latlng.lat, event.latlng.lng);
        reverseGeocodeAndFill(event.latlng.lat, event.latlng.lng);
    });

    // Cambia que controles se ven segun el modo elegido. Nunca toca el pin,
    // el barrio ya elegido, ni #location-manual: solo visibilidad + si el mapa
    // responde a un click.
    function applyMode(isManual) {
        manualPinEnabled = isManual;
        if (geoSearchPanel) {
            geoSearchPanel.hidden = isManual;
        }
        if (manualPanel) {
            manualPanel.hidden = !isManual;
        }
        // El campo de direccion es uno solo: vive en el desplegable abierto
        // ("Buscar" o "Escribir"). Mover el nodo no toca su name ni su valor.
        var panel = isManual ? manualPanel : geoSearchPanel;
        var addressWrap = addressInput ? addressInput.closest(".form-group") : null;
        if (panel && addressWrap) {
            panel.insertBefore(addressWrap, panel.firstChild);
        }
        if (barrioDisplay) {
            barrioDisplay.hidden = isManual;
        }
        if (barrioSelect) {
            barrioSelect.hidden = !isManual;
        }
        // Pill de solo lectura (modo geolocalizacion) vs <select> (modo texto
        // plano): el wrapper del pill y su ayuda van juntos con barrioDisplay,
        // nunca visibles a la vez que el <select>.
        if (barrioPill) {
            barrioPill.hidden = isManual;
        }
        if (barrioPillHint) {
            barrioPillHint.hidden = isManual;
        }
        if (mapHintGeo) {
            mapHintGeo.hidden = isManual;
        }
        if (mapHintManual) {
            mapHintManual.hidden = !isManual;
        }
    }

    if (modeGeoRadio && modeManualRadio) {
        modeGeoRadio.addEventListener("change", function () {
            if (modeGeoRadio.checked) {
                applyMode(false);
            }
        });
        modeManualRadio.addEventListener("change", function () {
            if (modeManualRadio.checked) {
                applyMode(true);
            }
        });
    }

    // Los dos encabezados ("Buscar ubicacion" / "Escribir ubicacion", ver
    // _eventWizardFields.jspf) no son modos nuevos: solo eligen el radio de
    // arriba y disparan el mismo "change" que usan los listeners de modo.
    var geoToggle = document.getElementById("geo-mode-toggle");
    var manualToggle = document.getElementById("manual-mode-toggle");
    function syncModeToggles() {
        if (geoToggle && modeGeoRadio) {
            geoToggle.setAttribute("aria-expanded", modeGeoRadio.checked ? "true" : "false");
        }
        if (manualToggle && modeManualRadio) {
            manualToggle.setAttribute("aria-expanded", modeManualRadio.checked ? "true" : "false");
        }
    }
    function chooseMode(radio) {
        radio.checked = true;
        (radio === modeGeoRadio ? modeManualRadio : modeGeoRadio).checked = false;
        radio.dispatchEvent(new Event("change"));
        syncModeToggles();
    }
    if (geoToggle && modeGeoRadio && modeManualRadio) {
        geoToggle.addEventListener("click", function () { chooseMode(modeGeoRadio); });
    }
    if (manualToggle && geoToggle && modeGeoRadio && modeManualRadio) {
        manualToggle.addEventListener("click", function () { chooseMode(modeManualRadio); });
        syncModeToggles();
    }

    if (modeGeoRadio && modeManualRadio) {
        applyMode(modeManualRadio.checked);
    }

    if (searchButton && addressInput && barrioSelect) {
        setUpAddressSearch({
            button: searchButton,
            addressInput: addressInput,
            statusEl: statusEl,
            suggestionsEl: suggestionsEl,
            barrioSelect: barrioSelect,
            barrioDisplay: barrioDisplay,
            pinManualInput: pinManualInput,
            setPin: setPin
        });
    }

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

// Saca tildes y pasa a mayusculas, para comparar "Núñez" (Barrio.displayName)
// contra "Nunez"/"NUÑEZ"/etc. sin depender de que vengan escritos igual.
// Validado a mano contra los 48 barrios reales (ver commit): el "suburb" que
// devuelve Nominatim para una direccion real siempre matchea el displayName
// del enum una vez normalizados los dos. ̀-ͯ son las marcas
// diacriticas combinantes que deja "NFD" separadas de la letra base.
function normalizeBarrioText(text) {
    return text.normalize("NFD").replace(/[̀-ͯ]/g, "").toUpperCase().trim();
}

// window.PAW_BARRIOS (enumName -> displayName, armado por la JSP desde
// Barrio.values()) invertido y normalizado: "SAN TELMO" -> "SAN_TELMO".
function buildBarrioLookup() {
    var lookup = {};
    var barrios = window.PAW_BARRIOS || {};
    Object.keys(barrios).forEach(function (enumName) {
        lookup[normalizeBarrioText(barrios[enumName])] = enumName;
    });
    return lookup;
}

// Completa el <select> de barrio Y el pill de solo lectura a partir del
// "suburb" que devuelve Nominatim -- mismo formato en /search y /reverse,
// asi que esto lo usan selectSuggestion() y reverseGeocodeAndFill() los dos.
// Si no hay match (parques/reservas grandes sin "suburb"), no toca nada: la
// persona elige el barrio a mano, nunca se pisa en silencio.
function applyBarrioMatch(suburb, barrioSelect, barrioDisplay) {
    var lookup = buildBarrioLookup();
    var matchedEnumName = suburb ? lookup[normalizeBarrioText(suburb)] : undefined;
    if (!matchedEnumName) {
        return false;
    }
    if (barrioSelect) {
        barrioSelect.value = matchedEnumName;
    }
    if (barrioDisplay) {
        barrioDisplay.textContent = window.PAW_BARRIOS[matchedEnumName];
    }
    return true;
}

// Arma el texto que reemplaza a lo que la persona tipeo, a partir de los
// componentes de Nominatim (no del display_name entero): road + house_number
// + suburb, sin postcode/ciudad/provincia/pais -- son siempre "Buenos Aires"/
// "Argentina"/etc. en esta app (todo CABA), no aportan nada.
function formatCleanAddress(nominatimAddress, fallbackDisplayName) {
    var road = nominatimAddress.road;
    if (!road) {
        return fallbackDisplayName;
    }
    var streetPart = nominatimAddress.house_number ? road + " " + nominatimAddress.house_number : road;
    return nominatimAddress.suburb ? streetPart + ", " + nominatimAddress.suburb : streetPart;
}

// Geocodifica una direccion contra Nominatim (nominatim.openstreetmap.org),
// acotado a CABA (viewbox + countrycodes) para no matchear una calle
// homonima de otra ciudad/pais -- igual asi puede devolver algo justo fuera
// de CABA (probado a mano), por eso el chequeo de barrio de mas abajo es la
// garantia de verdad, no el viewbox. Solo corre en modo "con geolocalizacion"
// (el boton que la llama esta oculto en modo "texto plano").
function setUpAddressSearch(refs) {
    var CABA_VIEWBOX = "-58.531,-34.530,-58.335,-34.705";
    var searching = false;

    function showStatus(text) {
        if (!refs.statusEl) {
            return;
        }
        refs.statusEl.textContent = text || "";
        refs.statusEl.hidden = !text;
    }

    function clearSuggestions() {
        refs.suggestionsEl.innerHTML = "";
        refs.suggestionsEl.hidden = true;
    }

    function selectSuggestion(result) {
        var lat = parseFloat(result.lat);
        var lon = parseFloat(result.lon);
        if (refs.pinManualInput) {
            refs.pinManualInput.value = "false";
        }
        refs.setPin(lat, lon);
        refs.addressInput.value = formatCleanAddress(result.address || {}, result.display_name);
        clearSuggestions();

        var matched = applyBarrioMatch((result.address || {}).suburb, refs.barrioSelect, refs.barrioDisplay);
        // Si no matcheo, a proposito NO se toca el <select>/el mapa: el pin ya
        // quedo puesto (sirve igual, aunque no sepamos el barrio), pero elegir
        // el barrio a mano y ajustar el lugar es una decision de la persona --
        // se pasa ella misma a modo "marcar en el mapa" si quiere.
        showStatus(matched ? "" : refs.button.dataset.noBarrioLabel);
    }

    // Nominatim puede devolver varios resultados que, una vez limpios (sin
    // postcode), se ven identicos (ej. la misma calle con distinto codigo
    // postal a cada lado, o sin house_number matcheado): sin este dedupe se
    // veian 5 botones iguales por texto para elegir, todos con la misma
    // pinta. Se queda con el primero de cada texto (el mejor rankeado por
    // Nominatim, que ya viene ordenado por relevancia).
    function dedupeByText(results) {
        var seen = {};
        return results.filter(function (result) {
            var text = formatCleanAddress(result.address || {}, result.display_name);
            if (seen[text]) {
                return false;
            }
            seen[text] = true;
            return true;
        });
    }

    function renderSuggestions(results) {
        clearSuggestions();
        var deduped = dedupeByText(results);
        refs.suggestionsEl.hidden = false;
        var heading = document.createElement("li");
        heading.className = "geocode-suggestions-heading";
        heading.textContent = refs.button.dataset.suggestionsHeading;
        refs.suggestionsEl.appendChild(heading);
        deduped.forEach(function (result) {
            var item = document.createElement("li");
            var button = document.createElement("button");
            button.type = "button";
            button.className = "geocode-suggestion";
            var main = document.createElement("span");
            main.className = "geocode-suggestion-main";
            main.textContent = formatCleanAddress(result.address || {}, result.display_name);
            var sub = document.createElement("span");
            sub.className = "geocode-suggestion-sub";
            sub.textContent = refs.suggestionsEl.getAttribute("data-sub-label") || "";
            button.appendChild(main);
            button.appendChild(sub);
            button.addEventListener("click", function () { selectSuggestion(result); });
            item.appendChild(button);
            refs.suggestionsEl.appendChild(item);
        });
    }

    refs.button.addEventListener("click", function () {
        if (searching) {
            return;
        }
        var address = refs.addressInput.value.trim();
        if (!address) {
            refs.addressInput.focus();
            return;
        }

        searching = true;
        refs.button.disabled = true;
        clearSuggestions();
        showStatus(refs.button.dataset.searchingLabel);

        // Busqueda ESTRUCTURADA (street/city/country separados), no "q=" en texto
        // libre: con q= Nominatim a veces ignora el numero de puerta y devuelve
        // la calle entera (un resultado por cada barrio que cruza, ej. "Larrea"
        // separado en Balvanera/Recoleta, sin altura) porque su parser de texto
        // libre no siempre entiende que el numero pegado al nombre de la calle
        // es la altura. structured street=<numero+calle> se lo dice explicito,
        // asi el resultado SI apunta a esa altura puntual (cuando OSM la tiene
        // mapeada; si una calle no tiene ningun numero cargado en el tramo
        // pedido, Nominatim igual puede devolver la calle entera -- no hay
        // forma de evitar eso del lado nuestro, es un limite de los datos).
        var url = "https://nominatim.openstreetmap.org/search"
            + "?format=jsonv2&addressdetails=1&limit=5&countrycodes=ar&bounded=1&viewbox=" + CABA_VIEWBOX
            + "&street=" + encodeURIComponent(address)
            + "&city=" + encodeURIComponent("Ciudad Autónoma de Buenos Aires")
            + "&country=" + encodeURIComponent("Argentina");

        fetch(url)
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("geocode request failed: " + response.status);
                }
                return response.json();
            })
            .then(function (results) {
                if (!results || results.length === 0) {
                    showStatus(refs.button.dataset.notFoundLabel);
                    return;
                }
                // Encontro resultados: borra el "Buscando..." de arriba, si no
                // queda pegado arriba de la lista de sugerencias para siempre.
                showStatus("");
                renderSuggestions(results);
            })
            .catch(function () {
                // Sin conexion, Nominatim caido, rate limit: nunca bloquea
                // publicar el evento, la persona puede cambiar a "texto
                // plano" y cargar la ubicacion a mano si quiere.
                showStatus(refs.button.dataset.errorLabel);
            })
            .finally(function () {
                searching = false;
                refs.button.disabled = false;
            });
    });

    // Enter en el campo de direccion busca, en vez de mandar el <form> entero
    // (ver el guard general en event-wizard.js: esto es ademas, no en lugar de
    // eso -- ese guard nunca sabe que este campo en particular tiene un boton
    // de buscar al lado). Solo dispara en modo geolocalizacion: en modo manual
    // este input vive en #manual-panel y el boton de buscar sigue escondido
    // adentro de #geo-search-panel, no tiene sentido geocodificar ahi.
    refs.addressInput.addEventListener("keydown", function (event) {
        if (event.key !== "Enter") {
            return;
        }
        event.preventDefault();
        var manualRadio = document.getElementById("modo-direccion-manual");
        if (!manualRadio || !manualRadio.checked) {
            refs.button.click();
        }
    });
}
