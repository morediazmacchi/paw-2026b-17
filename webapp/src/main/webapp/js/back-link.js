// Links "Volver" / flechita para atras: llevan a la pagina de donde se vino (el evento, la
// bandeja, el perfil...), no a un lugar fijo. Se marcan con data-history-back en el <a>.
//
// El href del link queda de plan B: si no hay pagina previa del sitio (se abrio la URL directo,
// o viene de afuera), no hay a donde volver y el link funciona como un link comun.
// Sin JavaScript tambien funciona igual, solo que va siempre al href.
//
// Por que no alcanza un solo history.back(): varias acciones de la pagina del evento (preguntar,
// inscribirse, reportar...) hacen POST + redirect de vuelta a esa MISMA url (/eventos/14,
// /eventos/14#preguntas, etc.), y cada redirect agrega una entrada nueva al historial del
// navegador aunque sea "la misma pagina" a los ojos de quien navega. Un history.back() que no
// supiera esto volveria a esa misma pagina (con otro query/hash) en vez de a la pagina anterior
// de verdad -- por eso sigue retrocediendo mientras el pathname de destino no cambie.
(function () {
    "use strict";

    // Tope de seguridad: nunca deberia hacer falta retroceder tantas veces para salir de una
    // sola pagina, pero evita un loop infinito si por algun motivo el pathname nunca cambia.
    var MAX_STEPS = 15;

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
        if (!cameFromSite || window.history.length <= 1) {
            return;
        }
        event.preventDefault();

        var startPath = window.location.pathname;
        var stepsLeft = MAX_STEPS;
        var timeoutId = null;

        function cleanup() {
            window.removeEventListener("popstate", onPopState);
            clearTimeout(timeoutId);
        }

        function onPopState() {
            stepsLeft -= 1;
            if (window.location.pathname !== startPath || stepsLeft <= 0) {
                cleanup();
                return;
            }
            window.history.back();
        }

        window.addEventListener("popstate", onPopState);
        // Si history.back() no tiene a donde ir (se llego al principio del historial del tab),
        // popstate nunca dispara: este timeout evita dejar el listener colgado para siempre.
        timeoutId = setTimeout(cleanup, 2000);
        window.history.back();
    });
})();
