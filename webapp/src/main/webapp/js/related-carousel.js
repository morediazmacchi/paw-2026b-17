// Flechas de los costados de "Eventos similares a este" (detail.jsp): mueven la fila de cards de a
// una "pantalla". Cada flecha se muestra solo si hay cards escondidas para ese lado: con pocas
// cards que entran todas, no aparece ninguna. Sin JS la fila igual se desliza de costado.
document.addEventListener("DOMContentLoaded", function () {
    var carousel = document.querySelector("[data-related-carousel]");
    if (!carousel) {
        return;
    }
    var list = carousel.querySelector("[data-related-list]");
    var prev = carousel.querySelector("[data-related-prev]");
    var next = carousel.querySelector("[data-related-next]");
    var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    function update() {
        var max = list.scrollWidth - list.clientWidth;
        prev.hidden = list.scrollLeft <= 1;
        next.hidden = list.scrollLeft >= max - 1;
    }

    function move(direction) {
        list.scrollBy({ left: direction * list.clientWidth, behavior: reduceMotion ? "auto" : "smooth" });
    }

    prev.addEventListener("click", function () { move(-1); });
    next.addEventListener("click", function () { move(1); });
    list.addEventListener("scroll", update);
    window.addEventListener("resize", update);
    update();
});
