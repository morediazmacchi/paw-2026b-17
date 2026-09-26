// Cambiar contraseña (perfil): mientras escriben "confirmar contraseña"
// avisamos ahi mismo si todavia no coincide con "nueva contraseña", sin
// esperar al submit ni pegarle al servidor para enterarse.
document.addEventListener("input", function (event) {
    if (event.target.id !== "newPassword" && event.target.id !== "confirmarPassword") {
        return;
    }
    var form = event.target.closest(".account-fields-form-single");
    var error = document.getElementById("confirmarPasswordMismatchError");
    if (!form || !error) {
        return;
    }
    var newPassword = form.querySelector("#newPassword");
    var confirmarPassword = form.querySelector("#confirmarPassword");
    // Todavia no escribio nada en "confirmar": no hay nada que comparar todavia.
    error.hidden = !confirmarPassword.value || newPassword.value === confirmarPassword.value;
});

// Ultimo control antes de mandarlo, por si nunca dispararon el "input" de
// arriba (ej: autocompletado del navegador sin evento de teclado).
document.addEventListener("submit", function (event) {
    if (!event.target.classList.contains("account-fields-form-single")) {
        return;
    }
    var newPassword = event.target.querySelector("#newPassword");
    var confirmarPassword = event.target.querySelector("#confirmarPassword");
    if (!newPassword || !confirmarPassword || newPassword.value === confirmarPassword.value) {
        return;
    }
    event.preventDefault();
    var error = document.getElementById("confirmarPasswordMismatchError");
    if (error) {
        error.hidden = false;
    }
    confirmarPassword.focus();
    // disable-on-submit.js ya deshabilito el boton al dispararse este mismo
    // evento (corre antes, registrado primero): como cancelamos el envio,
    // hay que devolverselo o queda inutilizable para reintentar.
    var button = event.target.querySelector("button[type=submit]");
    if (button) {
        button.disabled = false;
    }
});
