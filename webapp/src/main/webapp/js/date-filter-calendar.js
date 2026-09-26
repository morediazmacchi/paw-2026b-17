// Almanaque de "Desde"/"Hasta" del filtro de fecha en /eventos: mismo look &
// feel que el de publicar/editar evento (ver event-wizard.js), pero sin hora
// (son los dos extremos de un rango, no la fecha de un evento puntual) y sin
// techo de +2 anios. La unica regla es que "Hasta" no puede ser anterior a
// "Desde" (y viceversa): en vez de validar recien al filtrar, cada almanaque
// deshabilita los dias invalidos segun lo que ya este elegido en el otro.
document.addEventListener("DOMContentLoaded", function () {
    var MONTH_NAMES = ["enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"];

    function pad2(n) { return n < 10 ? "0" + n : "" + n; }
    function isoDate(date) { return date.getFullYear() + "-" + pad2(date.getMonth() + 1) + "-" + pad2(date.getDate()); }
    function sameDay(a, b) { return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate(); }
    function parseIso(value) {
        if (!value) {
            return null;
        }
        var p = value.split("-");
        return new Date(+p[0], +p[1] - 1, +p[2]);
    }

    var today = new Date();
    today.setHours(0, 0, 0, 0);

    // idPrefix: "fechaDesde" o "fechaHasta" -> arma los ids de los elementos que
    // ya estan en list.jsp (...Trigger, el input oculto y ...CalendarModal).
    // getMinDate/getMaxDate son funciones (no valores fijos): se llaman en cada
    // render, asi que siempre miran el valor ACTUAL del otro campo.
    function setupDatePicker(idPrefix, getMinDate, getMaxDate) {
        var trigger = document.getElementById(idPrefix + "Trigger");
        var hidden = document.getElementById(idPrefix);
        var modal = document.getElementById(idPrefix + "CalendarModal");
        if (!trigger || !hidden || !modal) {
            return;
        }
        var triggerText = trigger.querySelector("[data-calendar-trigger-text]");
        var monthLabel = modal.querySelector("[data-calendar-month-label]");
        var daysEl = modal.querySelector("[data-calendar-days]");
        var prevBtn = modal.querySelector("[data-calendar-prev]");
        var nextBtn = modal.querySelector("[data-calendar-next]");

        var initial = parseIso(hidden.value) || today;
        var viewYear = initial.getFullYear();
        var viewMonth = initial.getMonth();

        function updateTriggerText() {
            var chosen = parseIso(hidden.value);
            if (!chosen) {
                triggerText.textContent = "Cualquier fecha";
                triggerText.classList.remove("calendar-trigger-text-filled");
                return;
            }
            triggerText.textContent = chosen.getDate() + " de " + MONTH_NAMES[chosen.getMonth()] + " de " + chosen.getFullYear();
            triggerText.classList.add("calendar-trigger-text-filled");
        }

        function renderCalendar() {
            monthLabel.textContent = MONTH_NAMES[viewMonth] + " " + viewYear;
            var firstOfMonth = new Date(viewYear, viewMonth, 1);
            // getDay(): 0=domingo..6=sabado. Se corre para que 0=lunes..6=domingo.
            var firstWeekday = (firstOfMonth.getDay() + 6) % 7;
            var chosen = parseIso(hidden.value);
            var minDate = getMinDate();
            var maxDate = getMaxDate();

            daysEl.innerHTML = "";
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
                if ((minDate && cellDate < minDate) || (maxDate && cellDate > maxDate)) {
                    button.disabled = true;
                }
                daysEl.appendChild(button);
            }
        }

        daysEl.addEventListener("click", function (event) {
            var button = event.target.closest(".calendar-day");
            if (!button || button.disabled) {
                return;
            }
            hidden.value = button.dataset.calendarDate;
            updateTriggerText();
            modal.hidden = true;
        });

        prevBtn.addEventListener("click", function () {
            var d = new Date(viewYear, viewMonth - 1, 1);
            viewYear = d.getFullYear();
            viewMonth = d.getMonth();
            renderCalendar();
        });

        nextBtn.addEventListener("click", function () {
            var d = new Date(viewYear, viewMonth + 1, 1);
            viewYear = d.getFullYear();
            viewMonth = d.getMonth();
            renderCalendar();
        });

        // Al abrir, siempre mostrar el mes de la fecha ya elegida (si hay una) y
        // recalcular los limites: si mientras tanto se eligio la otra punta, los
        // dias que corresponde deshabilitar pueden haber cambiado.
        trigger.addEventListener("click", function () {
            var chosen = parseIso(hidden.value) || today;
            viewYear = chosen.getFullYear();
            viewMonth = chosen.getMonth();
            renderCalendar();
        });

        updateTriggerText();
        renderCalendar();
    }

    setupDatePicker("fechaDesde",
        function () { return null; },
        function () {
            var hasta = document.getElementById("fechaHasta");
            return hasta ? parseIso(hasta.value) : null;
        });
    setupDatePicker("fechaHasta",
        function () {
            var desde = document.getElementById("fechaDesde");
            return desde ? parseIso(desde.value) : null;
        },
        function () { return null; });
});
