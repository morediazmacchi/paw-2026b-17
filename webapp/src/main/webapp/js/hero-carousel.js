// Rota el banner destacado del listado cada 5 segundos. Si hay 0 o 1 evento
// destacado no hay nada que rotar, no hace nada.
document.addEventListener("DOMContentLoaded", function () {
    var slides = document.querySelectorAll(".hero-slide");
    var dots = document.querySelectorAll(".hero-dot");
    if (slides.length < 2) {
        return;
    }

    var current = 0;

    function show(index) {
        slides[current].classList.remove("hero-slide-active");
        if (dots[current]) {
            dots[current].classList.remove("hero-dot-active");
        }
        current = index;
        slides[current].classList.add("hero-slide-active");
        if (dots[current]) {
            dots[current].classList.add("hero-dot-active");
        }
    }

    dots.forEach(function (dot, i) {
        dot.addEventListener("click", function () {
            show(i);
        });
    });

    setInterval(function () {
        show((current + 1) % slides.length);
    }, 5000);
});
