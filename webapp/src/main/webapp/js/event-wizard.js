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
    var backBtn = document.querySelector("[data-wizard-back]");
    var nextBtn = document.querySelector("[data-wizard-next]");
    var submitWrap = document.querySelector(".wizard-submit");
    var stepError = document.querySelector(".wizard-step-error");

    // Apretar Enter en un <input> de texto hace "submit implicito" del form:
    // el navegador busca el primer boton submit, y lo encuentra -- el de
    // "Publicar" sigue en el DOM, solo escondido con [hidden] (ver
    // .wizard-submit mas abajo), y eso no lo saca de la busqueda. Sin este
    // guard, Enter en CUALQUIER campo de CUALQUIER paso manda el form entero
    // con los pasos de mas adelante vacios, y el usuario termina viendo
    // errores de un paso al que nunca llego. Mientras el boton real siga
    // escondido (no es el ultimo paso), Enter en un input no hace nada.
    form.addEventListener("keydown", function (event) {
        if (event.key !== "Enter") {
            return;
        }
        if (event.target.tagName !== "INPUT" || !submitWrap.hidden) {
            return;
        }
        event.preventDefault();
    });

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
    // eventDate (EventForm.eventDate no cambio). Hora/minutos son
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

    // Categorias con preguntas propias (todas menos "Otros", ver publish.jsp):
    // lo que realmente viaja al server sigue siendo un solo extraInfo (no se
    // toco el form ni la base), asi que las 2 respuestas se combinan en
    // "Pregunta: Respuesta" por linea adentro de un textarea oculto.
    var categoryQuestionGroups = Array.prototype.slice.call(
            form.querySelectorAll("[data-category-questions]"));
    categoryQuestionGroups.forEach(function (group) {
        var hidden = group.parentNode.querySelector("[data-category-extra-hidden]");
        var questionInputs = Array.prototype.slice.call(group.querySelectorAll(".wizard-category-question"));
        if (!hidden || questionInputs.length === 0) {
            return;
        }

        // Precarga (volver de un error de validacion, o editar un evento que
        // ya tenia esto guardado): si el texto de siempre tiene exactamente
        // una linea "Pregunta: Respuesta" por cada input, en ese orden, se
        // reparte. Si no matchea (texto viejo, de antes de este cambio, u
        // otro formato) no se toca nada: ese texto se preserva tal cual en
        // el oculto hasta que la persona edite algo a mano.
        var lines = hidden.value ? hidden.value.split("\n") : [];
        var matches = lines.length === questionInputs.length && questionInputs.every(function (input, i) {
            return lines[i].indexOf(input.dataset.question + ": ") === 0;
        });
        if (matches) {
            questionInputs.forEach(function (input, i) {
                input.value = lines[i].slice((input.dataset.question + ": ").length);
            });
        }

        function recombine() {
            hidden.value = questionInputs
                    .filter(function (input) { return input.value.trim() !== ""; })
                    .map(function (input) { return input.dataset.question + ": " + input.value.trim(); })
                    .join("\n");
        }

        questionInputs.forEach(function (input) {
            input.addEventListener("input", recombine);
        });
    });

    var fechaHidden = form.querySelector("#eventDate");
    var fechaDateInput = form.querySelector("#eventDateDate");
    var fechaHoursInput = form.querySelector("#eventDateHours");
    var fechaMinutesInput = form.querySelector("#eventDateMinutes");

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

    // Limites de fecha: nunca antes de hoy, nunca mas de 2 anios a partir de
    // hoy (mismo tope que EventController). Antes esto se lo pasabamos al
    // <input type="date"> nativo via min/max para que el navegador solo
    // rechazara lo invalido; ahora que ese input es hidden (lo reemplaza el
    // almanaque de mas abajo), los mismos limites se usan a mano para
    // deshabilitar los dias fuera de rango en la grilla.
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var minDate = today;
    var maxDate = new Date(today.getFullYear() + 2, today.getMonth(), today.getDate());

    var MONTH_NAMES = ["enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"];

    function isoDate(date) {
        return date.getFullYear() + "-" + pad2(date.getMonth() + 1) + "-" + pad2(date.getDate());
    }

    // Mismo dia (year/month/date), ignorando la hora.
    function sameDay(a, b) {
        return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
    }

    var calendarModal = document.getElementById("eventDateCalendarModal");
    var calendarMonthLabel = document.querySelector("[data-calendar-month-label]");
    var calendarDaysEl = document.querySelector("[data-calendar-days]");
    var calendarPrevBtn = document.querySelector("[data-calendar-prev]");
    var calendarNextBtn = document.querySelector("[data-calendar-next]");
    var fechaTriggerText = document.querySelector("[data-calendar-trigger-text]");
    // Mes que se esta mostrando en el almanaque (arranca en el mes de la
    // fecha ya elegida, si hay una, o en el de hoy).
    var viewYear = today.getFullYear();
    var viewMonth = today.getMonth();

    function updateTriggerText() {
        if (!fechaTriggerText) {
            return;
        }
        if (!fechaDateInput.value) {
            fechaTriggerText.textContent = "Elegí una fecha";
            fechaTriggerText.classList.remove("calendar-trigger-text-filled");
            return;
        }
        var parts = fechaDateInput.value.split("-");
        var chosen = new Date(+parts[0], +parts[1] - 1, +parts[2]);
        fechaTriggerText.textContent = chosen.getDate() + " de " + MONTH_NAMES[chosen.getMonth()] + " de " + chosen.getFullYear();
        fechaTriggerText.classList.add("calendar-trigger-text-filled");
    }

    // Grilla fija de 6 semanas (42 dias), empezando un lunes: alcanza para
    // cualquier mes sin tener que calcular cuantas filas hacen falta, y los
    // dias de mas se ven mas apagados (calendar-day-outside) en vez de vacios.
    function renderCalendar() {
        if (!calendarDaysEl) {
            return;
        }
        if (calendarMonthLabel) {
            calendarMonthLabel.textContent = MONTH_NAMES[viewMonth] + " " + viewYear;
        }
        var firstOfMonth = new Date(viewYear, viewMonth, 1);
        // getDay(): 0=domingo..6=sabado. Se corre para que 0=lunes..6=domingo.
        var firstWeekday = (firstOfMonth.getDay() + 6) % 7;
        var chosen = fechaDateInput.value ? (function () {
            var p = fechaDateInput.value.split("-");
            return new Date(+p[0], +p[1] - 1, +p[2]);
        })() : null;

        calendarDaysEl.innerHTML = "";
        for (var i = 0; i < 42; i++) {
            var cellDate = new Date(viewYear, viewMonth, 1 - firstWeekday + i);
            var button = document.createElement("button");
            button.type = "button";
            button.className = "calendar-day";
            button.textContent = cellDate.getDate();
            button.dataset.calendarDate = isoDate(cellDate);
            if (cellDate.getMonth() !== viewMonth) {
                button.classList.add("calendar-day-outside");
            }
            if (sameDay(cellDate, today)) {
                button.classList.add("calendar-day-today");
            }
            if (chosen && sameDay(cellDate, chosen)) {
                button.classList.add("calendar-day-selected");
            }
            if (cellDate < minDate || cellDate > maxDate) {
                button.disabled = true;
            }
            calendarDaysEl.appendChild(button);
        }

        // No tiene sentido navegar a un mes que va a quedar 100% deshabilitado.
        if (calendarPrevBtn) {
            calendarPrevBtn.disabled = viewYear === minDate.getFullYear() && viewMonth === minDate.getMonth();
        }
        if (calendarNextBtn) {
            calendarNextBtn.disabled = viewYear === maxDate.getFullYear() && viewMonth === maxDate.getMonth();
        }
    }

    if (calendarDaysEl) {
        calendarDaysEl.addEventListener("click", function (event) {
            var button = event.target.closest(".calendar-day");
            if (!button || button.disabled) {
                return;
            }
            fechaDateInput.value = button.dataset.calendarDate;
            syncFechaHidden();
            updateTriggerText();
            if (calendarModal) {
                calendarModal.hidden = true;
            }
        });
    }

    if (calendarPrevBtn) {
        calendarPrevBtn.addEventListener("click", function () {
            var d = new Date(viewYear, viewMonth - 1, 1);
            viewYear = d.getFullYear();
            viewMonth = d.getMonth();
            renderCalendar();
        });
    }

    if (calendarNextBtn) {
        calendarNextBtn.addEventListener("click", function () {
            var d = new Date(viewYear, viewMonth + 1, 1);
            viewYear = d.getFullYear();
            viewMonth = d.getMonth();
            renderCalendar();
        });
    }

    // Al abrir el almanaque, siempre mostrar el mes de la fecha ya elegida
    // (si hay una) en vez de quedarse en el mes de la ultima navegacion.
    var fechaTriggerBtn = document.getElementById("eventDateTrigger");
    if (fechaTriggerBtn) {
        fechaTriggerBtn.addEventListener("click", function () {
            if (fechaDateInput.value) {
                var p = fechaDateInput.value.split("-");
                viewYear = +p[0];
                viewMonth = +p[1] - 1;
            } else {
                viewYear = today.getFullYear();
                viewMonth = today.getMonth();
            }
            renderCalendar();
        });
    }

    if (fechaHidden && fechaDateInput && fechaHoursInput && fechaMinutesInput) {
        if (fechaHidden.value) {
            var parts = fechaHidden.value.split("T");
            fechaDateInput.value = parts[0] || "";
            var timeParts = (parts[1] || "").split(":");
            fechaHoursInput.value = timeParts[0] ? +timeParts[0] : "";
            fechaMinutesInput.value = timeParts[1] ? +timeParts[1] : "";
        }
        updateTriggerText();
        renderCalendar();

        fechaHoursInput.addEventListener("change", syncFechaHidden);
        fechaMinutesInput.addEventListener("change", syncFechaHidden);
    }

    // Que decir cuando un campo no pasa checkValidity(): la burbuja nativa del
    // navegador existe, pero es facil que pase desapercibida (el caso real que
    // motivo esto: una fecha pasada rechazada en silencio). Mejor un mensaje
    // propio, con el mismo estilo de alerta que ya se usa en el resto del sitio.
    function messageForField(field) {
        if ((field === fechaHoursInput || field === fechaMinutesInput)
                && (field.validity.rangeUnderflow || field.validity.rangeOverflow)) {
            return "El horario no es válido.";
        }
        // El <select> de barrio pide "required" en los dos modos de
        // ubicacion (ver publish.jsp/editar.jsp), pero en modo "con
        // geolocalizacion" esta oculto (event-location-map.js lo completa
        // solo al elegir una sugerencia) -- el mensaje generico de abajo
        // ("completa este campo") no sirve ahi, no hay ningun campo vacio a
        // la vista. Pasa cuando todavia no se busco nada, la busqueda no
        // encontro nada, o encontro el lugar pero no pudo identificar el
        // barrio (parques/zonas grandes): en los tres casos hay que buscar
        // de nuevo o pasarse a "texto plano", nunca dejar avanzar el wizard.
        if (field.id === "neighborhood" && field.validity.valueMissing) {
            var modoManualRadio = document.getElementById("modo-direccion-manual");
            if (!modoManualRadio || !modoManualRadio.checked) {
                return "No se pudo localizar la dirección. Revisá el nombre o seleccioná el modo \"texto plano\".";
            }
        }
        if (field.validity.valueMissing) {
            return "Completá este campo antes de seguir.";
        }
        // Cualquier otra regla (ej. un patron): el texto que ya trae el propio
        // navegador es mejor que nada, aunque no este traducido.
        return field.validationMessage || "Completá este campo antes de seguir.";
    }

    // Cada campo del paso se valida con la API nativa (checkValidity): min/max
    // en hora/minutos hacen que un horario fuera de rango ya venga invalido
    // solo. La fecha ya no pasa por ahi (el input real es hidden, un hidden
    // no se valida solo): el almanaque no deja clickear un dia fuera de rango,
    // asi que lo unico que falta chequear a mano es que se haya elegido
    // alguna, igual que con las categorias.
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
        var stepFechaTrigger = steps[index].querySelector("#eventDateTrigger");
        if (stepFechaTrigger && !fechaDateInput.value) {
            showStepError("Elegí una fecha antes de seguir.");
            stepFechaTrigger.focus();
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
        // price/maxCapacity: si el atajo "Gratis"/"Sin limite" esta activo el
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
        setReview("title", fieldValue("title"));
        setReview("categoria", selectedCategoriasText());
        setReview("fecha", formatFecha(fieldValue("eventDate")));

        // El <select> de barrio: fieldValue devuelve el TEXTO de la opcion
        // elegida (util para mostrar), pero para saber si eligio algo hay que
        // mirar el value crudo, no el texto ("Sin especificar" tambien es texto).
        var barrioField = form.querySelector("[name='neighborhood']");
        var barrioChosen = barrioField && barrioField.value ? fieldValue("neighborhood") : "";
        var direccion = fieldValue("address");
        setReview("address", barrioChosen ? direccion + " (" + barrioChosen + ")" : direccion);

        var precioField = form.querySelector("[name='price']");
        var precioValue = fieldValue("price");
        setReview("price", precioField && !precioField.hidden && precioValue ? "$" + precioValue : precioValue);
        setReview("cupo", fieldValue("maxCapacity"));

        // Checkboxes, no inputs de texto: no pasan por fieldValue (esa funcion
        // mira .value, que en un checkbox siempre es "true" este o no tildado).
        setReviewCheckbox("requiresApproval");
        setReviewCheckbox("privateEvent");
        setReviewCheckbox("parking");

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
