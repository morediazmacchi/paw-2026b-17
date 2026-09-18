// Corazon de favorito (listado y detalle): togglea con fetch en vez de un
// submit normal, asi la pagina no recarga ni salta el scroll para arriba.
// El backend no cambia: son los mismos endpoints que siempre, que devuelven
// un redirect normal; fetch lo sigue solo y descartamos el body, total lo
// unico que nos importa es si salio bien.
document.addEventListener("submit", function (event) {
    var form = event.target;
    if (!form.classList.contains("js-favorite-toggle")) {
        return;
    }
    event.preventDefault();

    var button = form.querySelector("button");
    var willBeActive = !button.classList.contains("card-favorite-btn-active");
    var url = willBeActive ? form.dataset.addUrl : form.dataset.removeUrl;

    button.disabled = true;
    fetch(url, { method: "POST", body: new FormData(form) })
        .then(function (response) {
            if (!response.ok) {
                return;
            }
            button.classList.toggle("card-favorite-btn-active", willBeActive);
            button.setAttribute("aria-label", willBeActive ? form.dataset.removeLabel : form.dataset.addLabel);
            form.action = willBeActive ? form.dataset.removeUrl : form.dataset.addUrl;
            form.querySelector(".favorite-icon-outline").hidden = willBeActive;
            form.querySelector(".favorite-icon-filled").hidden = !willBeActive;
        })
        .finally(function () {
            button.disabled = false;
        });
});
