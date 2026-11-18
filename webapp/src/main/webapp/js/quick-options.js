// Atajos "Gratis" y "Sin limite" del formulario de publicar.
//
// El pill NO manda nada propio al servidor. Lo que hace es:
//   - escribir un 0 en el campo de siempre (precio / cupoMaximo)
//   - esconder ese campo y mostrar un cartel con el texto del atajo
//
// El atributo hidden esconde el input pero NO impide que su valor viaje en el
// POST (para eso haria falta disabled). Asi el servidor recibe exactamente lo
// mismo que si la persona escribiera el 0 a mano, y no hubo que tocar EventForm,
// las validaciones ni el controller.
//
// "Sin limite" (cupo) es distinto de "Gratis" (precio): un precio en 0 siempre
// significa gratis, lo haya escrito la persona o el atajo -- no hay ambiguedad.
// Un CUPO en 0 no tiene un significado valido si se escribe a mano (no se puede
// organizar un evento para 0 personas), asi que el atajo prende ADEMAS un campo
// oculto aparte (data-flag-target, ver input.tag/ValidCapacity) que dice "este 0
// es el atajo, no un numero real". Sin ese flag prendido, el servidor rechaza el "0".
//
// Sin JavaScript los pills no hacen nada y el formulario funciona igual que antes.
document.addEventListener("DOMContentLoaded", function () {
    var toggles = document.querySelectorAll(".chip-toggle");
    for (var i = 0; i < toggles.length; i++) {
        setUp(toggles[i]);
    }

    function setUp(toggle) {
        var input = document.getElementById(toggle.getAttribute("data-target"));
        if (!input) {
            return;
        }
        var display = input.parentNode.querySelector(".preset-display");
        var flagTargetId = toggle.getAttribute("data-flag-target");
        var flagInput = flagTargetId ? document.getElementById(flagTargetId) : null;

        function activate() {
            input.value = "0";
            input.hidden = true;
            if (flagInput) {
                flagInput.value = "true";
            }
            if (display) {
                display.hidden = false;
            }
            toggle.classList.add("is-on");
            toggle.setAttribute("aria-pressed", "true");
        }

        function deactivate() {
            input.value = "";
            input.hidden = false;
            if (flagInput) {
                flagInput.value = "false";
            }
            if (display) {
                display.hidden = true;
            }
            toggle.classList.remove("is-on");
            toggle.setAttribute("aria-pressed", "false");
            input.focus();
        }

        // Si el formulario volvio con errores y el atajo ya estaba activo (editar un evento sin
        // limite, o reenvio con el atajo prendido), arranca activado para no perder la eleccion.
        // Sin flag (precio): alcanza con que el campo ya valga 0. Con flag (cupo): tiene que estar
        // prendido de verdad -- un "0" escrito a mano (el flag queda en "false" o vacio) NO reactiva
        // el atajo solo, para que la persona vea el error al lado del numero que escribio.
        if (flagInput ? flagInput.value === "true" : input.value === "0") {
            activate();
        }

        toggle.addEventListener("click", function () {
            if (toggle.classList.contains("is-on")) {
                deactivate();
            } else {
                activate();
            }
        });
    }
});
