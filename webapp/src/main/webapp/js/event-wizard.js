// Wizard de "Publicar evento": 5 pasos dentro del mismo <form> (un solo POST,
// el controller no cambia). Cada paso es un <div class="wizard-step" hidden>;
// mostrar/ocultar pasos alcanza porque un campo required adentro de un
// ancestro con el atributo "hidden" queda afuera de la validacion nativa del
// form (no bloquea el submit ni el checkValidity de otro paso).
//
// "Siguiente" es type="button" (no dispara la validacion nativa del form solo
// con clickearlo), asi que cada campo del paso actual se valida a mano antes
// de avanzar: eso es lo que evita llegar al paso 5 y recien ahi enterarse de
// que algo del paso 1 estaba mal.
document.addEventListener("DOMContentLoaded", function () {
    var form = document.querySelector(".wizard-form");
    if (!form) {
        return;
    }

    var steps = Array.prototype.slice.call(form.querySelectorAll(".wizard-step"));
    var indicators = Array.prototype.slice.call(document.querySelectorAll("[data-step-indicator]"));
    var cancelBtn = document.querySelector(".wizard-cancel");
    var backBtn = document.querySelector("[data-wizard-back]");
    var nextBtn = document.querySelector("[data-wizard-next]");
    var submitWrap = document.querySelector(".wizard-submit");
    var stepError = document.querySelector(".wizard-step-error");

    function showStepError(message) {
        if (stepError) {
            stepError.textContent = message;
            stepError.hidden = false;
        }
    }

    function hideStepError() {
        if (stepError) {
            stepError.hidden = true;
        }
    }

    function showStep(index) {
        hideStepError();
        steps.forEach(function (step, i) {
            step.hidden = i !== index;
        });
        indicators.forEach(function (dot, i) {
            dot.classList.toggle("wizard-progress-step-active", i === index);
            dot.classList.toggle("wizard-progress-step-done", i < index);
        });
        cancelBtn.hidden = index !== 0;
        backBtn.hidden = index === 0;
        var isLastStep = index === steps.length - 1;
        nextBtn.hidden = isLastStep;
        submitWrap.hidden = !isLastStep;
        if (isLastStep) {
            renderReview();
        }
    }

    // Fecha, hora y minutos van en inputs separados (paso 2), pero el server
    // sigue esperando un solo string "yyyy-MM-ddTHH:mm" en el input oculto
    // fechaEvento (EventForm.fechaEvento no cambio). Hora/minutos son
    // <input type=number> a proposito, no <input type=time>: ese widget
    // depende de la configuracion regional del navegador y en muchas termina
    // mostrando AM/PM de nuevo aunque se le pida 24hs.
    var fechaHidden = form.querySelector("#fechaEvento");
    var fechaDateInput = form.querySelector("#fechaEventoDate");
    var fechaHoursInput = form.querySelector("#fechaEventoHours");
    var fechaMinutesInput = form.querySelector("#fechaEventoMinutes");

    function pad2(n) { return n < 10 ? "0" + n : "" + n; }

    function syncFechaHidden() {
        if (!fechaHidden || !fechaDateInput || !fechaHoursInput || !fechaMinutesInput) {
            return;
        }
        var hasTime = fechaHoursInput.value !== "" && fechaMinutesInput.value !== "";
        fechaHidden.value = fechaDateInput.value && hasTime
                ? fechaDateInput.value + "T" + pad2(+fechaHoursInput.value) + ":" + pad2(+fechaMinutesInput.value)
                : "";
    }

    if (fechaHidden && fechaDateInput && fechaHoursInput && fechaMinutesInput) {
        // Nunca se puede elegir un dia anterior a hoy: se lo decimos al
        // navegador via "min" para que checkValidity() lo rechace solo, sin
        // JS a medida por cada click de "Siguiente".
        var today = new Date();
        fechaDateInput.min = today.getFullYear() + "-" + pad2(today.getMonth() + 1) + "-" + pad2(today.getDate());

        if (fechaHidden.value) {
            var parts = fechaHidden.value.split("T");
            fechaDateInput.value = parts[0] || "";
            var timeParts = (parts[1] || "").split(":");
            fechaHoursInput.value = timeParts[0] ? +timeParts[0] : "";
            fechaMinutesInput.value = timeParts[1] ? +timeParts[1] : "";
        }

        fechaDateInput.addEventListener("change", syncFechaHidden);
        fechaHoursInput.addEventListener("change", syncFechaHidden);
        fechaMinutesInput.addEventListener("change", syncFechaHidden);
    }

    // Que decir cuando un campo no pasa checkValidity(): la burbuja nativa del
    // navegador existe, pero es facil que pase desapercibida (el caso real que
    // motivo esto: una fecha pasada rechazada en silencio). Mejor un mensaje
    // propio, con el mismo estilo de alerta que ya se usa en el resto del sitio.
    function messageForField(field) {
        if (field === fechaDateInput && field.validity.rangeUnderflow) {
            return "La fecha tiene que ser hoy o una fecha futura.";
        }
        if ((field === fechaHoursInput || field === fechaMinutesInput)
                && (field.validity.rangeUnderflow || field.validity.rangeOverflow)) {
            return "El horario no es válido.";
        }
        if (field.validity.valueMissing) {
            return "Completá este campo antes de seguir.";
        }
        // Cualquier otra regla (ej. un patron): el texto que ya trae el propio
        // navegador es mejor que nada, aunque no este traducido.
        return field.validationMessage || "Completá este campo antes de seguir.";
    }

    // Cada campo del paso se valida con la API nativa (checkValidity): "min" en
    // la fecha hace que una fecha pasada ya venga invalida sola, y min/max en
    // hora/minutos hacen lo mismo con un horario fuera de rango.
    function validateStep(index) {
        syncFechaHidden();
        var fields = steps[index].querySelectorAll("input, select, textarea");
        for (var i = 0; i < fields.length; i++) {
            if (!fields[i].checkValidity()) {
                showStepError(messageForField(fields[i]));
                fields[i].focus();
                return false;
            }
        }
        hideStepError();
        return true;
    }

    // Si el form volvio del servidor con errores (una regla que el HTML5
    // required/pattern no puede expresar, ej. "la fecha tiene que ser futura"),
    // hay que abrir directamente el paso donde esta el error: si no, queda
    // escondido en un paso anterior y parece que la pagina no hizo nada.
    function findFirstErrorStep() {
        for (var i = 0; i < steps.length; i++) {
            if (steps[i].querySelector(".error-message")) {
                return i;
            }
        }
        return 0;
    }

    nextBtn.addEventListener("click", function () {
        if (!validateStep(current)) {
            return;
        }
        current = Math.min(current + 1, steps.length - 1);
        showStep(current);
    });

    backBtn.addEventListener("click", function () {
        current = Math.max(current - 1, 0);
        showStep(current);
    });

    // Lo que ya se completo en pasos anteriores, resumido en el paso 5.
    function fieldValue(name) {
        var field = form.querySelector("[name='" + name + "']");
        if (!field) {
            return "";
        }
        // precio/cupoMaximo: si el atajo "Gratis"/"Sin limite" esta activo el
        // input real queda hidden y lo que se ve es el cartel de al lado
        // (quick-options.js); mostrar eso, no el "0" que viaja en el input.
        if (field.hidden) {
            var display = field.parentNode.querySelector(".preset-display");
            if (display) {
                return display.textContent.trim();
            }
        }
        if (field.tagName === "SELECT") {
            return field.selectedIndex >= 0 ? field.options[field.selectedIndex].text.trim() : "";
        }
        return field.value;
    }

    function formatFecha(raw) {
        if (!raw) {
            return "";
        }
        var parsed = new Date(raw);
        if (isNaN(parsed.getTime())) {
            return raw;
        }
        function pad(n) { return n < 10 ? "0" + n : "" + n; }
        return pad(parsed.getDate()) + "/" + pad(parsed.getMonth() + 1) + "/" + parsed.getFullYear()
                + " " + pad(parsed.getHours()) + ":" + pad(parsed.getMinutes());
    }

    function renderReview() {
        setReview("titulo", fieldValue("titulo"));
        setReview("categoria", fieldValue("categoriaId"));
        setReview("fecha", formatFecha(fieldValue("fechaEvento")));

        // El <select> de barrio: fieldValue devuelve el TEXTO de la opcion
        // elegida (util para mostrar), pero para saber si eligio algo hay que
        // mirar el value crudo, no el texto ("Sin especificar" tambien es texto).
        var barrioField = form.querySelector("[name='barrio']");
        var barrioChosen = barrioField && barrioField.value ? fieldValue("barrio") : "";
        var direccion = fieldValue("direccion");
        setReview("direccion", barrioChosen ? direccion + " (" + barrioChosen + ")" : direccion);

        var precioField = form.querySelector("[name='precio']");
        var precioValue = fieldValue("precio");
        setReview("precio", precioField && !precioField.hidden && precioValue ? "$" + precioValue : precioValue);
        setReview("cupo", fieldValue("cupoMaximo"));

        var imageInput = form.querySelector("#image");
        var imageRow = form.querySelector("[data-review-image-row]");
        var imageEl = form.querySelector("[data-review-image]");
        if (imageInput && imageInput.files && imageInput.files[0]) {
            var reader = new FileReader();
            reader.onload = function (event) {
                imageEl.src = event.target.result;
                imageRow.hidden = false;
            };
            reader.readAsDataURL(imageInput.files[0]);
        } else {
            imageRow.hidden = true;
        }
    }

    function setReview(key, value) {
        var el = form.querySelector("[data-review='" + key + "']");
        if (el) {
            el.textContent = value;
        }
    }

    // El nombre elegido del archivo, mostrado adentro del recuadro (paso 4).
    var dropzoneInput = form.querySelector(".wizard-dropzone-input");
    if (dropzoneInput) {
        dropzoneInput.addEventListener("change", function () {
            var filenameEl = form.querySelector(".wizard-dropzone-filename");
            if (dropzoneInput.files && dropzoneInput.files[0]) {
                filenameEl.textContent = dropzoneInput.files[0].name;
                filenameEl.hidden = false;
            } else {
                filenameEl.hidden = true;
            }
        });
    }

    var current = findFirstErrorStep();
    showStep(current);
});
