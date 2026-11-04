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
//
// Ojo: cada una de esas entradas es un DOCUMENTO nuevo (una pagina que se vuelve a cargar), no un
// cambio dentro de la misma pagina. "popstate" solo dispara si el retroceso es dentro del mismo
// documento, asi que un listener armado en el click se perdia en el primer salto y "Volver"
// terminaba en el mismo evento (con el scroll de la visita anterior). Por eso el avance se guarda
// en sessionStorage y cada pagina que se carga (o se restaura del cache) lo retoma.
(function () {
    "use strict";

    // Tope de seguridad: nunca deberia hacer falta retroceder tantas veces para salir de una
    // sola pagina, pero evita un loop infinito si por algun motivo el pathname nunca cambia.
    var MAX_STEPS = 15;
    // Si no hay a donde retroceder, la marca queda guardada: pasado este tiempo se descarta para
    // que no dispare un "atras" por sorpresa mas tarde en la misma pestana.
    var STATE_TTL_MS = 4000;
    var STORAGE_KEY = "pawBackFrom";

    function readState() {
        try {
            return JSON.parse(window.sessionStorage.getItem(STORAGE_KEY));
        } catch (e) {
            return null;
        }
    }

    function writeState(state) {
        try {
            if (state) {
                window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
            } else {
                window.sessionStorage.removeItem(STORAGE_KEY);
            }
        } catch (e) {
            // Sin sessionStorage (modo privado, bloqueado): "Volver" retrocede un paso y listo.
        }
    }

    // Se llama en cada carga/restauracion de pagina y en cada popstate: si hay un "Volver" en
    // curso y seguimos en la pagina de la que salimos, retrocede otra vez; si ya es otra, termina.
    function continueBack() {
        var state = readState();
        if (!state) {
            return;
        }
        if (Date.now() - state.t > STATE_TTL_MS || window.location.pathname !== state.path || state.steps <= 1) {
            writeState(null);
            document.documentElement.style.visibility = "";
            return;
        }
        writeState({path: state.path, steps: state.steps - 1, t: Date.now()});
        // Esta pagina es de paso: se esconde para que no parpadee mientras se sigue retrocediendo.
        document.documentElement.style.visibility = "hidden";
        window.history.back();
    }

    window.addEventListener("pageshow", continueBack);
    window.addEventListener("popstate", continueBack);

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

        writeState({path: window.location.pathname, steps: MAX_STEPS, t: Date.now()});
        // Si history.back() no tiene a donde ir (se llego al principio del historial del tab),
        // no cambia nada y la marca vence sola (STATE_TTL_MS).
        window.history.back();
    });
})();
