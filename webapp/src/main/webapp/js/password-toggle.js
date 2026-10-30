// El "ojito" de los campos de contraseña (paw:input type="password"): alterna
// entre type="password"/"text" y muestra el icono contrario (dos iconos, uno
// oculto con "hidden" segun el estado).
document.addEventListener("click", function (event) {
    const toggle = event.target.closest("[data-toggle-password]");
    if (!toggle) {
        return;
    }
    const input = document.getElementById(toggle.dataset.togglePassword);
    if (!input) {
        return;
    }
    const willShow = input.type === "password";
    input.type = willShow ? "text" : "password";
    toggle.querySelector(".password-icon-show").hidden = willShow;
    toggle.querySelector(".password-icon-hide").hidden = !willShow;
});
