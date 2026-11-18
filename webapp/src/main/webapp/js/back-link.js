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
//
// Formularios que ya se mandaron (publicar y editar un evento): despues de "Publicar" se llega al evento con un
// redirect, y la entrada anterior del historial es el formulario. "Volver" desde el evento no tiene que caer de nuevo
// en el formulario lleno (que ademas se podria mandar dos veces): las paginas con
// <meta name="paw-history-skip"> se saltean y se sigue retrocediendo. Si antes del formulario no habia nada (se abrio
// directo o desde afuera del sitio), se va al href del link "Volver" en vez de quedarse en el formulario.
// Lo mismo con el boton "atras" del navegador: si se vuelve a un formulario que ya se mando en esta pestana, se lo
// saltea igual (y si no habia nada antes, se va a la direccion que dice el content de ese <meta>).
(function () {
    "use strict";

    // Tope de seguridad: nunca deberia hacer falta retroceder tantas veces para salir de una
    // sola pagina, pero evita un loop infinito si por algun motivo el pathname nunca cambia.
    var MAX_STEPS = 15;
    // Si no hay a donde retroceder, la marca queda guardada: pasado este tiempo se descarta para
    // que no dispare un "atras" por sorpresa mas tarde en la misma pestana.
    var STATE_TTL_MS = 4000;
    var STORAGE_KEY = "pawBackFrom";
    // Cuanto se espera a que history.back() saque al navegador de un formulario salteado antes de ir al href del
    // link "Volver" (no habia pagina anterior a la que retroceder).
    var SKIP_FALLBACK_MS = 800;
    var fallbackTimer = null;
    // Formulario mandado (publicar/editar): {path, t}. Vence solo, para no saltear un formulario mucho despues.
    var SENT_KEY = "pawSentForm";
    var SENT_TTL_MS = 30 * 60 * 1000;

    function isPageToSkip() {
        return document.querySelector('meta[name="paw-history-skip"]') !== null;
    }

    // A donde ir si se saltea el formulario y no hay pagina anterior del sitio (el content del <meta>).
    function skipFallback() {
        var meta = document.querySelector('meta[name="paw-history-skip"]');
        var content = meta ? meta.getAttribute("content") : null;
        return content && content.charAt(0) === "/" ? content : "/";
    }

    function navigationType() {
        var entries = window.performance && window.performance.getEntriesByType
            ? window.performance.getEntriesByType("navigation") : [];
        return entries.length > 0 ? entries[0].type : "";
    }

    function readSent() {
        try {
            return JSON.parse(window.sessionStorage.getItem(SENT_KEY));
        } catch (e) {
            return null;
        }
    }

    function writeSent(sent) {
        try {
            if (sent) {
                window.sessionStorage.setItem(SENT_KEY, JSON.stringify(sent));
            } else {
                window.sessionStorage.removeItem(SENT_KEY);
            }
        } catch (e) {
            // Sin sessionStorage: el "atras" del navegador vuelve al formulario, como en cualquier sitio.
        }
    }

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
    function continueBack(event) {
        var state = readState();
        if (!state) {
            // Sin un "Volver" en curso: si el navegador restauro esta pagina de su cache (atras/adelante
            // NATIVO del navegador, no nuestro link) y es un formulario ya mandado, no se queda mostrando
            // los datos viejos listos para reenviar -- se sigue retrocediendo un paso mas, como si se
            // hubiera saltado con "Volver".
            if (event && event.persisted && isPageToSkip()) {
                document.documentElement.style.visibility = "hidden";
                window.history.back();
            }
            return;
        }
        var stillOnOrigin = window.location.pathname === state.path;
        var skipThisPage = !stillOnOrigin && isPageToSkip();
        if (Date.now() - state.t > STATE_TTL_MS || state.steps <= 1 || (!stillOnOrigin && !skipThisPage)) {
            writeState(null);
            document.documentElement.style.visibility = "";
            return;
        }
        writeState({path: state.path, steps: state.steps - 1, t: Date.now(), fallback: state.fallback});
        // Esta pagina es de paso: se esconde para que no parpadee mientras se sigue retrocediendo.
        document.documentElement.style.visibility = "hidden";
        if (skipThisPage && state.fallback) {
            // Al formulario no se llego desde otra pagina del sitio (se abrio directo, o desde afuera): retroceder
            // sacaria del sitio. Se reemplaza esta entrada por el destino del link "Volver".
            if (document.referrer.indexOf(window.location.origin) !== 0) {
                writeState(null);
                window.location.replace(state.fallback);
                return;
            }
            // Plan B por si history.back() igual no saca de aca: el destino del link "Volver".
            fallbackTimer = window.setTimeout(function () {
                writeState(null);
                window.location.replace(state.fallback);
            }, SKIP_FALLBACK_MS);
        }
        window.history.back();
    }

    // Si la pagina se va (o queda guardada en el cache del navegador), el plan B ya no corresponde.
    window.addEventListener("pagehide", function () {
        if (fallbackTimer !== null) {
            window.clearTimeout(fallbackTimer);
            fallbackTimer = null;
        }
    });

    // Boton "atras"/"adelante" del navegador que cae en un formulario que ya se mando en esta pestana (publicar o
    // editar un evento): no se lo muestra de nuevo lleno (se podria mandar dos veces); se sigue retrocediendo.
    function leaveSentForm(event) {
        if (!isPageToSkip()) {
            return;
        }
        var cameBackOrForward = event.persisted || navigationType() === "back_forward";
        if (!cameBackOrForward) {
            // Se abrio el formulario de nuevo (o es la respuesta con errores de un envio): arranca de cero.
            writeSent(null);
            return;
        }
        var sent = readSent();
        if (!sent || sent.path !== window.location.pathname || Date.now() - sent.t > SENT_TTL_MS) {
            return;
        }
        document.documentElement.style.visibility = "hidden";
        if (document.referrer.indexOf(window.location.origin) !== 0) {
            // No habia una pagina del sitio antes del formulario: retroceder sacaria del sitio.
            window.location.replace(skipFallback());
            return;
        }
        fallbackTimer = window.setTimeout(function () {
            window.location.replace(skipFallback());
        }, SKIP_FALLBACK_MS);
        window.history.back();
    }

    window.addEventListener("pageshow", function (event) {
        if (readState()) {
            continueBack();
        } else {
            leaveSentForm(event);
        }
    });
    window.addEventListener("popstate", continueBack);

    // Se marca que se mando el formulario principal de la pagina (el que se manda a esta misma direccion: no el de
    // idioma ni el de cerrar sesion), si nadie cancelo el envio (por ejemplo, una validacion del navegador).
    document.addEventListener("submit", function (event) {
        if (!isPageToSkip() || event.defaultPrevented) {
            return;
        }
        var form = event.target;
        var action = form && form.getAttribute ? form.getAttribute("action") : null;
        var actionPath = action ? new URL(action, window.location.href).pathname : window.location.pathname;
        if (actionPath === window.location.pathname) {
            writeSent({path: window.location.pathname, t: Date.now()});
        }
    });

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

        writeState({path: window.location.pathname, steps: MAX_STEPS, t: Date.now(), fallback: link.href});
        // Si history.back() no tiene a donde ir (se llego al principio del historial del tab),
        // no cambia nada y la marca vence sola (STATE_TTL_MS).
        window.history.back();
    });
})();
