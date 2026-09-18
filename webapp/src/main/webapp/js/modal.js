// Popups genericos: cualquier elemento con data-open-modal="idDelModal" lo abre,
// cualquier elemento con data-close-modal (adentro del modal) lo cierra. No hay
// cierre al clickear el fondo: mas simple, y evita el problema de que closest()
// tambien "encuentre" el fondo al clickear adentro del contenido.
document.addEventListener("click", function (event) {
    const opener = event.target.closest("[data-open-modal]");
    if (opener) {
        const modal = document.getElementById(opener.dataset.openModal);
        if (modal) {
            modal.hidden = false;
        }
        return;
    }
    const closer = event.target.closest("[data-close-modal]");
    if (closer) {
        const modal = closer.closest(".modal-overlay");
        if (modal) {
            modal.hidden = true;
        }
    }
});
