// Contrato de persistencia para EventReport. Implementacion: EventReportJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.ReportStatus;

import java.util.List;
import java.util.Optional;

public interface EventReportDao {
    EventReport create(EventReport report);

    Optional<EventReport> findById(long id);

    // Cola de moderacion: los PENDING, mas viejo primero (ver AdminController).
    List<EventReport> findPending();

    boolean existsPendingByEventAndReporter(long eventId, long reporterUserId);

    // Resuelve UN reporte puntual (descartado o accionado). No toca los que ya
    // estaban resueltos: quien llama solo actualiza si sigue PENDING.
    void resolve(long reportId, ReportStatus status, long resolvedByUserId);

    // Al dar de baja un evento, cualquier OTRO reporte pendiente sobre el mismo
    // evento tambien queda resuelto (ACTIONED): ya no tiene sentido que la cola
    // siga mostrando un evento que ya se removio.
    void resolveAllPendingForEvent(long eventId, ReportStatus status, long resolvedByUserId);
}
