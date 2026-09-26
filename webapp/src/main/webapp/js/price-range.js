// Slider de precio de dos puntas (tipo Airbnb) en el filtro de /eventos: dos
// <input type="range"> superpuestos (ver price-range CSS), sincronizados con
// los inputs numericos de siempre, que son los que realmente viajan al form.
// Arrastrar el slider escribe en el numero; escribir en el numero mueve el
// slider: cualquiera de los dos caminos deja el mismo resultado.
document.addEventListener("DOMContentLoaded", function () {
    var wrap = document.querySelector("[data-price-range]");
    if (!wrap) {
        return;
    }
    var minRange = wrap.querySelector("[data-price-range-min]");
    var maxRange = wrap.querySelector("[data-price-range-max]");
    var fill = wrap.querySelector("[data-price-range-fill]");
    var minNumber = document.getElementById("precioMin");
    var maxNumber = document.getElementById("precioMax");
    var sliderMax = +minRange.max;
    // Ancho del thumb en css (::-webkit-slider-thumb / ::-moz-range-thumb):
    // el CENTRO de un <input type=range> no viaja de 0% a 100%, viaja de
    // "medio thumb" a "100% menos medio thumb" (el circulo no puede salirse
    // de la pista). Sin este ajuste la barra de relleno queda descentrada
    // respecto de las bolitas (mas notorio cerca de los extremos).
    var THUMB = 18;

    // Traduce una fraccion 0..1 a la posicion real (en css calc) del centro
    // del thumb para esa fraccion.
    function thumbCenter(fraction) {
        return "calc((100% - " + THUMB + "px) * " + fraction + " + " + (THUMB / 2) + "px)";
    }

    function updateFill() {
        var minFraction = +minRange.value / sliderMax;
        var maxFraction = +maxRange.value / sliderMax;
        fill.style.left = thumbCenter(minFraction);
        // "right" se mide desde el otro borde: misma formula, con la fraccion
        // espejada (1 - maxFraction).
        fill.style.right = thumbCenter(1 - maxFraction);
    }

    // Que el handle de min nunca cruce al de max, y viceversa: mismo criterio
    // que Airbnb, un handle empuja al otro en vez de cruzarlo.
    minRange.addEventListener("input", function () {
        if (+minRange.value > +maxRange.value) {
            minRange.value = maxRange.value;
        }
        // 0 en el slider es "sin minimo", igual de valido que dejar el campo vacio.
        minNumber.value = minRange.value;
        updateFill();
    });

    maxRange.addEventListener("input", function () {
        if (+maxRange.value < +minRange.value) {
            maxRange.value = minRange.value;
        }
        // La punta de arriba pegada del todo a la derecha es "sin maximo": vaciar
        // el campo (no escribir el tope del slider) para no limitar la busqueda a
        // ese numero si en realidad hay eventos mas caros.
        maxNumber.value = +maxRange.value >= sliderMax ? "" : maxRange.value;
        updateFill();
    });

    // Tambien al reves: escribir un numero a mano mueve el slider (acotado a su
    // rango visual; el numero en si no queda limitado, solo el dibujo del handle).
    minNumber.addEventListener("input", function () {
        minRange.value = minNumber.value === "" ? 0 : Math.min(+minNumber.value, sliderMax);
        updateFill();
    });
    maxNumber.addEventListener("input", function () {
        maxRange.value = maxNumber.value === "" ? sliderMax : Math.min(+maxNumber.value, sliderMax);
        updateFill();
    });

    updateFill();
});
