// Links "Volver" / flechita para atras: llevan a la pagina de donde se vino (el evento, la
// bandeja, el perfil...), no a un lugar fijo. Se marcan con data-history-back en el <a>.
//
// El href del link queda de plan B: si no hay pagina previa del sitio (se abrio la URL directo,
// o viene de afuera), no hay a donde volver y el link funciona como un link comun.
// Sin JavaScript tambien funciona igual, solo que va siempre al href.
(function () {
    "use strict";

    document.addEventListener("click", function (event) {
        var link = event.target.closest && event.target.closest("a[data-history-back]");
        if (!link || event.defaultPrevented) {
            return;
        }
        // Ctrl/Cmd/Shift + click (abrir en otra pestana) o click del medio: que haga lo suyo.
        if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) {
            return;
        }
        var cameFromSite = document.referrer.indexOf(window.location.origin) === 0;
        if (cameFromSite && window.history.length > 1) {
            event.preventDefault();
            window.history.back();
        }
    });
})();
