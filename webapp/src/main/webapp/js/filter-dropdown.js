// Cierra los desplegables de filtros (<details class="filter-dropdown">) al clickear
// afuera. Los popups de calendario (.modal-overlay) no cuentan como "afuera": si no,
// elegir una fecha cerraria el desplegable que la contiene.
document.addEventListener("click", function (event) {
    if (event.target.closest(".modal-overlay")) {
        return;
    }
    document.querySelectorAll("details.filter-dropdown[open]").forEach(function (dropdown) {
        if (!dropdown.contains(event.target)) {
            dropdown.open = false;
        }
    });
});
