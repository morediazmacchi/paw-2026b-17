// Paginas de "Mi cuenta" (las que tienen la barra lateral): muestran datos que cambian con lo que hace la persona
// (borrar una lista, cancelar una inscripcion, sacar un favorito, leer la bandeja). Al volver con "Atras", el navegador
// puede mostrar la copia que se guardo de la visita anterior (el back/forward cache, o su cache HTTP) en vez de pedirla
// de nuevo, y aparece lo que ya se borro: por ejemplo, la lista eliminada. Si la pagina se muestra por haber ido para
// atras/adelante, se recarga una vez para traer el estado real del servidor.
//
// No se hace en todo el sitio a proposito: en un formulario (publicar o editar un evento) volver para atras tiene que
// conservar lo que se habia escrito, y recargar lo perderia.
(function () {
    "use strict";

    // Si back-link.js esta retrocediendo varias entradas seguidas ("Volver"), esta pagina es solo de paso: no se
    // recarga, se sigue de largo (ver back-link.js, misma clave).
    function isPassingThrough() {
        try {
            var state = JSON.parse(window.sessionStorage.getItem("pawBackFrom"));
            return state !== null && state.path === window.location.pathname;
        } catch (e) {
            return false;
        }
    }

    // Se cargo por "Atras"/"Adelante" y salio del cache HTTP (transferSize 0: no se pidio al servidor). Si en cambio
    // el navegador ya la pidio de nuevo (la respuesta trae Cache-Control: no-store), esta al dia y no hace falta nada.
    function cameBackFromHttpCache() {
        var entries = window.performance && window.performance.getEntriesByType
            ? window.performance.getEntriesByType("navigation") : [];
        return entries.length > 0 && entries[0].type === "back_forward" && entries[0].transferSize === 0;
    }

    window.addEventListener("pageshow", function (event) {
        // persisted: restaurada del back/forward cache (la copia en memoria de la visita anterior). Despues de la
        // recarga el tipo de navegacion pasa a ser "reload": no se repite.
        if ((event.persisted || cameBackFromHttpCache()) && !isPassingThrough()) {
            window.location.reload();
        }
    });
})();
