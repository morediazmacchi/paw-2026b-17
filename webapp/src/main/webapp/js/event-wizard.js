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
    // Categorias (paso 2): tildar/destildar el checkbox muestra/esconde su
    // textarea de informacion adicional al lado. checkValidity() nativo no
    // alcanza aca (a diferencia de los radio buttons, "required" en un
    // checkbox exige ESE checkbox puntual, no "al menos uno del grupo"): la
    // regla "elegi al menos una categoria" se valida a mano en validateStep().
    var categoryCheckboxes = Array.prototype.slice.call(
            form.querySelectorAll(".wizard-category-checkbox"));
    categoryCheckboxes.forEach(function (checkbox) {
        var extra = checkbox.closest(".wizard-category-item").querySelector(".wizard-category-extra");
        checkbox.addEventListener("change", function () {
            if (extra) {
                extra.hidden = !checkbox.checked;
            }
        });
    });

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
        var stepCategoryCheckboxes = Array.prototype.slice.call(
                steps[index].querySelectorAll(".wizard-category-checkbox"));
        if (stepCategoryCheckboxes.length > 0
                && !stepCategoryCheckboxes.some(function (checkbox) { return checkbox.checked; })) {
            showStepError("Elegí al menos una categoría antes de seguir.");
            stepCategoryCheckboxes[0].focus();
            return false;
        }
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

    // Las categorias tildadas, separadas por coma (el texto visible del
    // checkbox, no su value que es el id numerico).
    function selectedCategoriasText() {
        return categoryCheckboxes
                .filter(function (checkbox) { return checkbox.checked; })
                .map(function (checkbox) { return checkbox.closest("label").textContent.trim(); })
                .join(", ");
    }

    function renderReview() {
        setReview("titulo", fieldValue("titulo"));
        setReview("categoria", selectedCategoriasText());
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

        // Checkboxes, no inputs de texto: no pasan por fieldValue (esa funcion
        // mira .value, que en un checkbox siempre es "true" este o no tildado).
        setReviewCheckbox("requiereAprobacion");
        setReviewCheckbox("privado");

        // Solo se previsualiza la primera foto elegida (con un contador al
        // lado si eligio mas de una): mostrar las 5 en el resumen no aporta
        // mucho mas que confirmar que la subida funciono.
        var imageInput = form.querySelector("#images");
        var imageRow = form.querySelector("[data-review-image-row]");
        var imageEl = form.querySelector("[data-review-image]");
        var imageCountEl = form.querySelector("[data-review-image-count]");
        if (imageInput && imageInput.files && imageInput.files[0]) {
            var reader = new FileReader();
            reader.onload = function (event) {
                imageEl.src = event.target.result;
                imageRow.hidden = false;
            };
            reader.readAsDataURL(imageInput.files[0]);
            if (imageCountEl) {
                var extra = imageInput.files.length - 1;
                imageCountEl.textContent = extra > 0 ? "+" + extra : "";
            }
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

    // Version de setReview para checkboxes: el texto sale de data-yes-label /
    // data-no-label (ver publish.jsp), no de un valor calculado con fieldValue.
    function setReviewCheckbox(name) {
        var field = form.querySelector("[name='" + name + "']");
        var el = form.querySelector("[data-review='" + name + "']");
        if (field && el) {
            el.textContent = field.checked
                    ? el.getAttribute("data-yes-label")
                    : el.getAttribute("data-no-label");
        }
    }

    // Los nombres de los archivos elegidos, mostrados adentro del recuadro
    // (paso 4): uno solo, el nombre; mas de uno, "3 fotos elegidas" (no los
    // nombres de las 5, no entran comodos en el recuadro).
    var dropzoneInput = form.querySelector(".wizard-dropzone-input");
    if (dropzoneInput) {
        dropzoneInput.addEventListener("change", function () {
            var filenameEl = form.querySelector(".wizard-dropzone-filename");
            var count = dropzoneInput.files ? dropzoneInput.files.length : 0;
            if (count === 1) {
                filenameEl.textContent = dropzoneInput.files[0].name;
                filenameEl.hidden = false;
            } else if (count > 1) {
                filenameEl.textContent = count + " fotos elegidas";
                filenameEl.hidden = false;
            } else {
                filenameEl.hidden = true;
            }
        });
    }

    var current = findFirstErrorStep();
    showStep(current);
});
