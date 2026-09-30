// Estado de un reporte de evento (ver EventReport). Se guarda con name() en
// event_report.status, igual que el resto de los enums de la app.
package ar.edu.itba.paw.model;

public enum ReportStatus {
    PENDING,
    // El admin le pidio al creador que corrija algo en vez de descartar o dar
    // de baja directo (ver ModerationService.requestChanges()): no es un
    // estado final, sigue "abierto" hasta que un admin lo revise de nuevo.
    CHANGES_REQUESTED,
    DISMISSED,
    ACTIONED
}
