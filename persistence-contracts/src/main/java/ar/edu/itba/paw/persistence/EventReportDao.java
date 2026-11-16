// Contrato de persistencia para EventReport. Implementacion: EventReportJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.ReportStatusCounts;
import ar.edu.itba.paw.model.ReportStatusFilter;
import ar.edu.itba.paw.model.ReportTarget;
import ar.edu.itba.paw.model.ReportStatus;

import java.util.List;
import java.util.Optional;

public interface EventReportDao {
    EventReport create(EventReport report);

    Optional<EventReport> findById(long id);

    // A que evento apunta el reporte y de quien es, en una consulta de dos columnas (sin cargar el
    // reporte ni el evento). Vale para un reporte en CUALQUIER estado, incluso ya resuelto; vacio
    // solo si el reporte no existe.
    Optional<ReportTarget> findTargetById(long reportId);

    // Panel unificado "Reportes y seguimiento" (ver ModerationService.searchQueue()): todos los reportes,
    // filtrados por texto libre (evento, quien reporto, motivo) y por pestaña, de actividad mas reciente
    // primero (ver EventReport.getLastActivityAt()). query puede ser null/vacio (sin filtrar por texto). Una
    // pagina (page empieza en 1); countSearch() es el total para armar el paginador.
    List<EventReport> search(String query, ReportStatusFilter filter, int page, int pageSize);

    long countSearch(String query, ReportStatusFilter filter);

    // Los 5 contadores de las pestañas, sin filtrar por texto: una sola consulta con SUM/CASE en vez de 5
    // COUNT separados.
    ReportStatusCounts countStatuses();

    // Primera vez que un admin abre el detalle de este reporte: true si ESTA llamada lo marco (no pisa el
    // momento si ya estaba visto). No hace nada si el reporte no existe.
    boolean markViewed(long reportId, long adminUserId);

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
