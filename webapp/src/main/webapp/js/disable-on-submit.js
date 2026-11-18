// Evita doble click: al mandar un form de la pagina, deshabilita sus botones de submit.
//
// Se deshabilitan un instante DESPUES (setTimeout 0), no en el mismo momento: el navegador arma lo que se manda
// recien cuando termina este evento, y un boton deshabilitado no viaja con el form. Hay botones que llevan el dato
// (el de idioma de la navbar es name="lang" value="es" / value="en"): si se deshabilitaba antes, ese dato se perdia
// y el servidor respondia 400. Pasaba justo al elegir el primer boton del form ("Español"), o sea al cambiar de
// idioma por segunda vez.
(function () {
    "use strict";

    var MARK = "data-disabled-on-submit";

    document.addEventListener("submit", function (event) {
        if (event.defaultPrevented) {
            return;
        }
        var form = event.target;
        window.setTimeout(function () {
            var buttons = form.querySelectorAll("button[type=submit], input[type=submit]");
            for (var i = 0; i < buttons.length; i++) {
                if (!buttons[i].disabled) {
                    buttons[i].disabled = true;
                    buttons[i].setAttribute(MARK, "true");
                }
            }
        }, 0);
    });

    // Si se vuelve con "atras" a una pagina que quedo guardada en el navegador, sus botones siguen deshabilitados
    // desde el envio anterior: se habilitan de nuevo.
    window.addEventListener("pageshow", function (event) {
        if (!event.persisted) {
            return;
        }
        var buttons = document.querySelectorAll("[" + MARK + "]");
        for (var i = 0; i < buttons.length; i++) {
            buttons[i].disabled = false;
            buttons[i].removeAttribute(MARK);
        }
    });
})();
