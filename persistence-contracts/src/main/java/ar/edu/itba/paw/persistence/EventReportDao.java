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

    // Panel de seguimiento: los CHANGES_REQUESTED, mas viejo primero (mismo
    // criterio FIFO que findPending()).
    List<EventReport> findChangesRequested();

    // "Abierto" = PENDING o CHANGES_REQUESTED (ver EventReport.isOpen()): un
    // reporte con cambios pedidos tambien bloquea un reporte nuevo de la misma
    // persona, no tiene sentido duplicar la cola mientras se espera esa revision.
    boolean existsPendingByEventAndReporter(long eventId, long reporterUserId);

    /**
     * El admin le pide al creador que corrija algo en vez de descartar o dar
     * de baja directo: pasa a CHANGES_REQUESTED con el mensaje del admin (ver
     * findChangesRequested()). Solo actua si seguia PENDING (mismo criterio
     * que resolve(): no pisa un reporte que ya tenia un pedido de cambios, o
     * que ya estaba resuelto).
     */
    boolean requestChanges(long reportId, String message, long adminUserId);

    // Resuelve UN reporte puntual (descartado o accionado). No toca los que ya
    // estaban resueltos: quien llama solo actualiza si seguia abierto
    // (PENDING o CHANGES_REQUESTED).
    boolean resolve(long reportId, ReportStatus status, long resolvedByUserId);

    // Al dar de baja un evento, cualquier OTRO reporte abierto (PENDING o
    // CHANGES_REQUESTED) sobre el mismo evento tambien queda resuelto
    // (ACTIONED): ya no tiene sentido seguir mostrandolo en la cola o en el
    // panel de seguimiento, el evento ya se removio.
    int resolveAllPendingForEvent(long eventId, ReportStatus status, long resolvedByUserId);
}
