// Una pagina del panel "Reportes y seguimiento" con lo necesario para mostrarla: los reportes (ya filtrados
// por texto/pestaña, cada uno con su evento y las cuentas involucradas ya cargados: report.getEvent(),
// getReporter() y getChangesRequestedBy()) y los contadores de las 5 pestañas (sin filtrar por texto). Lo devuelve
// ModerationService.searchQueue().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.ReportStatusCounts;

public class ReportQueuePage {

    private final PageResult<EventReport> reports;
    private final ReportStatusCounts counts;

    public ReportQueuePage(final PageResult<EventReport> reports, final ReportStatusCounts counts) {
        this.reports = reports;
        this.counts = counts;
    }

    public PageResult<EventReport> getReports() { return reports; }

    public ReportStatusCounts getCounts() { return counts; }
}
