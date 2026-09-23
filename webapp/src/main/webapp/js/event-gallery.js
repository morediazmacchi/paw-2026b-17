// Galeria del detalle de un evento: tocar una miniatura la pone como portada
// (la imagen grande de arriba) en vez de abrir la foto en otra pagina. Sin JS
// las miniaturas siguen siendo links a /image/{id}, asi que la pagina
// funciona igual.
document.addEventListener("DOMContentLoaded", function () {
    var hero = document.querySelector(".detail-hero-img");
    var thumbs = document.querySelectorAll(".detail-gallery-link");
    if (!hero || thumbs.length === 0) {
        return;
    }
    Array.prototype.forEach.call(thumbs, function (link) {
        link.addEventListener("click", function (event) {
            event.preventDefault();
            hero.src = link.getAttribute("href");
            Array.prototype.forEach.call(thumbs, function (other) {
                other.classList.toggle("detail-gallery-link-active", other === link);
            });
        });
    });
});
