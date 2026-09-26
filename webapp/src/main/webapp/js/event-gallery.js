// Detalle de evento: si hay mas de una foto, tocar la principal o cualquier
// miniatura abre un carrusel (modal generico, ver modal.js) con todas las
// fotos en vez de abrir la imagen sola en otra pagina. Sin JS cada una sigue
// siendo un link a /image/{id}.
document.addEventListener("DOMContentLoaded", function () {
    var photos = Array.prototype.slice.call(document.querySelectorAll("[data-gallery-index]"));
    var modal = document.getElementById("eventGalleryModal");
    if (photos.length === 0 || !modal) {
        return;
    }

    // El data-gallery-index de cada foto (hero + miniaturas, todas apuntan al
    // mismo array de URLs por su indice en event.imageIds) alcanza para armar
    // la lista sin tener que repetirla a mano en otro lado del JSP.
    var urls = [];
    photos.forEach(function (photo) {
        urls[+photo.dataset.galleryIndex] = photo.getAttribute("href");
    });

    var photoEl = modal.querySelector("[data-gallery-photo]");
    var counterEl = modal.querySelector("[data-gallery-counter]");
    var current = 0;

    function show(index) {
        current = (index + urls.length) % urls.length;
        photoEl.src = urls[current];
        counterEl.textContent = (current + 1) + " / " + urls.length;
    }

    // modal.js ya escucha el click de [data-open-modal] y lo muestra; este
    // listener (en el elemento, no delegado) corre antes por el orden de
    // burbujeo, asi que la foto ya esta puesta cuando el modal se hace visible.
    photos.forEach(function (photo) {
        photo.addEventListener("click", function (event) {
            event.preventDefault();
            show(+photo.dataset.galleryIndex);
        });
    });

    modal.querySelector("[data-gallery-prev]").addEventListener("click", function () {
        show(current - 1);
    });
    modal.querySelector("[data-gallery-next]").addEventListener("click", function () {
        show(current + 1);
    });

    document.addEventListener("keydown", function (event) {
        if (modal.hidden) {
            return;
        }
        if (event.key === "ArrowLeft") {
            show(current - 1);
        } else if (event.key === "ArrowRight") {
            show(current + 1);
        } else if (event.key === "Escape") {
            modal.hidden = true;
        }
    });
});
