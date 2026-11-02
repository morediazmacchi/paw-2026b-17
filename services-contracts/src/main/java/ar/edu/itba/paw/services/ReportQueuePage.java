// Una pagina de la cola de moderacion con lo necesario para mostrarla: los reportes, y los eventos
// reportados y los usuarios involucrados por id. Lo devuelve ModerationService.findPendingQueue() /
// findChangesRequestedQueue().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.User;

import java.util.Map;

public class ReportQueuePage {

    private final PageResult<EventReport> reports;
    private final Map<Long, Event> eventsById;
    private final Map<Long, User> reportersById;
    private final Map<Long, User> requestedByUsersById;

    public ReportQueuePage(final PageResult<EventReport> reports, final Map<Long, Event> eventsById,
                           final Map<Long, User> reportersById, final Map<Long, User> requestedByUsersById) {
        this.reports = reports;
        this.eventsById = eventsById;
        this.reportersById = reportersById;
        this.requestedByUsersById = requestedByUsersById;
    }

    public PageResult<EventReport> getReports() { return reports; }
    public Map<Long, Event> getEventsById() { return eventsById; }
    public Map<Long, User> getReportersById() { return reportersById; }

    // Los admins que pidieron cambios (solo en el panel de seguimiento; vacio en la cola de pendientes).
    public Map<Long, User> getRequestedByUsersById() { return requestedByUsersById; }
}
