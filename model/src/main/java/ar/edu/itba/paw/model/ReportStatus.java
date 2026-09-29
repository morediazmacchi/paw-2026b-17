// Estado de un reporte de evento (ver EventReport). Se guarda con name() en
// event_report.status, igual que el resto de los enums de la app.
package ar.edu.itba.paw.model;

public enum ReportStatus {
    PENDING,
    DISMISSED,
    ACTIONED
}
