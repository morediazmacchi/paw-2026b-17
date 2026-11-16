// Una pagina del panel "Reportes y seguimiento" con lo necesario para mostrarla: los reportes (ya filtrados
// por texto/pestaña), los eventos reportados y los usuarios involucrados por id, y los contadores de las 5
// pestañas (sin filtrar por texto). Lo devuelve ModerationService.searchQueue().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.ReportStatusCounts;
import ar.edu.itba.paw.model.User;

import java.util.Map;

public class ReportQueuePage {

    private final PageResult<EventReport> reports;
    private final Map<Long, Event> eventsById;
    private final Map<Long, User> reportersById;
    private final Map<Long, User> requestedByUsersById;
    private final ReportStatusCounts counts;

    public ReportQueuePage(final PageResult<EventReport> reports, final Map<Long, Event> eventsById,
                           final Map<Long, User> reportersById, final Map<Long, User> requestedByUsersById,
                           final ReportStatusCounts counts) {
        this.reports = reports;
        this.eventsById = eventsById;
        this.reportersById = reportersById;
        this.requestedByUsersById = requestedByUsersById;
        this.counts = counts;
    }

    public PageResult<EventReport> getReports() { return reports; }
    public Map<Long, Event> getEventsById() { return eventsById; }
    public Map<Long, User> getReportersById() { return reportersById; }

    // Los admins que pidieron cambios, solo de los reportes que tienen uno (los demas no entran al map).
    public Map<Long, User> getRequestedByUsersById() { return requestedByUsersById; }

    public ReportStatusCounts getCounts() { return counts; }
}
