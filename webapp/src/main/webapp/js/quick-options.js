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

        function activate() {
            input.value = "0";
            input.hidden = true;
            if (display) {
                display.hidden = false;
            }
            toggle.classList.add("is-on");
            toggle.setAttribute("aria-pressed", "true");
        }

        function deactivate() {
            input.value = "";
            input.hidden = false;
            if (display) {
                display.hidden = true;
            }
            toggle.classList.remove("is-on");
            toggle.setAttribute("aria-pressed", "false");
            input.focus();
        }

        // Si el formulario volvio con errores y ese campo ya valia 0, el pill
        // arranca activado para no perder lo que la persona habia elegido.
        if (input.value === "0") {
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
