// Campos que solo admiten digitos (cupo y precio en pesos enteros).
//
// Se marcan con data-digits-only="true" (ver input.tag, atributo digitsOnly).
// Problema que resuelve: quien escribe "1.000" para decir mil mandaba ese
// texto al servidor, que lo rechazaba recien al apretar "Publicar" con un
// error que no explicaba nada. Ahora el punto, la coma, la "e" o el signo
// directamente no entran, y si se pega "1.000" queda "1000".
//
// No se limpia el valor completo al escribir: un evento ya guardado con
// centavos (ej: "1500.5") se abre tal cual y no se altera si la persona no
// toca ese campo. Sin JavaScript el campo funciona igual y es el servidor el
// que valida (ver EventForm).
document.addEventListener("DOMContentLoaded", function () {
    var fields = document.querySelectorAll("input[data-digits-only]");
    for (var i = 0; i < fields.length; i++) {
        setUp(fields[i]);
    }

    function onlyDigits(text) {
        return String(text).replace(/\D/g, "");
    }

    function setUp(field) {
        // Teclado fisico: se frena la tecla antes de que escriba algo. Se
        // dejan pasar las combinaciones (Ctrl/Cmd + A, C, V...) y las teclas
        // de control (borrar, flechas, tab, enter), que llegan con nombre largo.
        field.addEventListener("keydown", function (event) {
            if (event.ctrlKey || event.metaKey || event.altKey) {
                return;
            }
            if (event.key && event.key.length === 1 && !/[0-9]/.test(event.key)) {
                event.preventDefault();
            }
        });

        // Teclados de celular (que a veces mandan key "Unidentified"), arrastrar
        // texto y autocorrector: antes de insertar se mira que es lo que entra.
        field.addEventListener("beforeinput", function (event) {
            if (event.inputType && event.inputType.indexOf("insert") === 0
                    && typeof event.data === "string" && /\D/.test(event.data)) {
                event.preventDefault();
            }
        });

        // Pegar: "1.000" o " 1 000 " se transforma en "1000".
        field.addEventListener("paste", function (event) {
            var clipboard = event.clipboardData || window.clipboardData;
            if (!clipboard) {
                return;
            }
            event.preventDefault();
            var digits = onlyDigits(clipboard.getData("text"));
            if (digits === "") {
                return;
            }
            var start = field.selectionStart;
            var end = field.selectionEnd;
            if (typeof start !== "number") {
                field.value = digits;
            } else {
                field.setRangeText(digits, start, end, "end");
            }
            if (field.maxLength > 0 && field.value.length > field.maxLength) {
                field.value = field.value.slice(0, field.maxLength);
            }
            field.dispatchEvent(new Event("input", { bubbles: true }));
        });
    }
});
