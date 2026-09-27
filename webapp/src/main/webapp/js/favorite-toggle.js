// Corazon de favorito (listado y detalle): togglea con fetch en vez de un
// submit normal, asi la pagina no recarga ni salta el scroll para arriba.
//
// Optimistic update: el corazon cambia AL TOQUE, antes de que conteste el
// servidor -- se siente instantaneo. Si el pedido termina fallando (el
// servidor devuelve un error, o ni siquiera contesta), se revierte solo al
// estado de antes y se avisa con un toast chico. Antes fallaba en silencio:
// sin revertir el icono ni avisar nada, quedaba mostrando un estado que en
// realidad no se guardo.
document.addEventListener("submit", function (event) {
    var form = event.target;
    if (!form.classList.contains("js-favorite-toggle")) {
        return;
    }
    event.preventDefault();

    var button = form.querySelector("button");
    var wasActive = button.classList.contains("card-favorite-btn-active");
    var willBeActive = !wasActive;
    var url = willBeActive ? form.dataset.addUrl : form.dataset.removeUrl;

    applyFavoriteState(form, button, willBeActive);
    button.disabled = true;

    fetch(url, { method: "POST", body: new FormData(form) })
        .then(function (response) {
            if (!response.ok) {
                applyFavoriteState(form, button, wasActive);
                showFavoriteError(form.dataset.errorLabel);
            }
        })
        .catch(function () {
            // No hubo respuesta (sin conexion, servidor caido): mismo tratamiento.
            applyFavoriteState(form, button, wasActive);
            showFavoriteError(form.dataset.errorLabel);
        })
        .finally(function () {
            button.disabled = false;
        });
});

function applyFavoriteState(form, button, active) {
    button.classList.toggle("card-favorite-btn-active", active);
    button.setAttribute("aria-label", active ? form.dataset.removeLabel : form.dataset.addLabel);
    form.action = active ? form.dataset.removeUrl : form.dataset.addUrl;
    form.querySelector(".favorite-icon-outline").hidden = active;
    form.querySelector(".favorite-icon-filled").hidden = !active;
}

// Un solo toast compartido por toda la pagina (no uno por card: en una
// grilla con muchas cards no hay donde poner un mensaje fijo por cada una
// sin romper el layout). Se crea la primera vez que hace falta y se reusa.
var favoriteToast = null;

function showFavoriteError(message) {
    if (!favoriteToast) {
        favoriteToast = document.createElement("div");
        favoriteToast.className = "favorite-toast";
        document.body.appendChild(favoriteToast);
    }
    favoriteToast.textContent = message;
    favoriteToast.classList.add("favorite-toast-visible");
    window.clearTimeout(favoriteToast.hideTimeout);
    favoriteToast.hideTimeout = window.setTimeout(function () {
        favoriteToast.classList.remove("favorite-toast-visible");
    }, 3000);
}
