// Contador "n / max" debajo de cada campo que tenga maxlength.
//
// El navegador ya impide pasar del maximo, pero lo hace en silencio: quien
// pega un texto largo ve que se corta y no entiende por que. Este contador
// muestra cuanto le queda, y se pone en rojo al llegar al tope.
//
// Se aplica a los input y textarea con maxlength (ver input.tag y los
// textarea de las vistas). Se salta:
//  - password: el contador de una contrasena no aporta nada y rompe el boton
//    de mostrar/ocultar.
//  - search (la barra de busqueda de /eventos): el tope existe, pero un contador pegado a un buscador es ruido.
//  - digits-only (cupo y precio, data-digits-only): son numeros, no texto.
//  - la textarea espejo de las categorias (data-category-extra-hidden): la
//    llena event-wizard.js con las respuestas de las preguntas, nunca la escribe
//    la persona, asi que un contador ahi quedaria en 0 para siempre.
// Sin JavaScript el campo funciona igual, solo que sin contador.
(function () {
    "use strict";

    function setUp(field) {
        var max = field.maxLength;
        // maxLength vale -1 cuando el atributo no esta: no hay nada que contar.
        if (!(max > 0)) {
            return;
        }

        var counter = document.createElement("span");
        counter.className = "char-counter";
        counter.setAttribute("aria-live", "polite");
        field.insertAdjacentElement("afterend", counter);

        function update() {
            var length = field.value.length;
            counter.textContent = length + " / " + max;
            counter.classList.toggle("is-full", length >= max);
        }

        field.addEventListener("input", update);
        update();
    }

    function init() {
        var fields = document.querySelectorAll(
            "input[maxlength]:not([type=password]):not([type=search]):not([data-digits-only]), " +
            "textarea[maxlength]:not([data-category-extra-hidden])");
        for (var i = 0; i < fields.length; i++) {
            setUp(fields[i]);
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
