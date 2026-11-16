// Si la URL trae ?abierto=<id> (lo pone AdminController.verReporte() al volver de marcar un reporte
// como visto), abre de una el modal de seguimiento de ese reporte. defer: corre despues de armado el DOM,
// no hace falta esperar a DOMContentLoaded.
const openReportMarker = document.querySelector("[data-open-report]");
const openReportId = openReportMarker ? openReportMarker.dataset.openReport : "";
if (openReportId) {
    const modal = document.getElementById("seguimientoModal-" + openReportId);
    if (modal) {
        modal.hidden = false;
    }
}
