// Evita doble click: al mandar cualquier form de la pagina, deshabilita su boton de submit.
document.addEventListener("submit", function (event) {
    var button = event.target.querySelector("button[type=submit]");
    if (button) {
        button.disabled = true;
    }
});
