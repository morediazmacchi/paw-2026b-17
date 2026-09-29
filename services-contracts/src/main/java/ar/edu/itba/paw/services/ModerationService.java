// Contrato de servicio para moderacion de eventos (reportes). Implementacion:
// ModerationServiceImpl (modulo services). La suspension/promocion de USUARIOS
// vive en UserService, no aca: son acciones sobre el ciclo de vida de la
// cuenta, no sobre un reporte puntual.
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.User;

import java.util.List;

public interface ModerationService {

    /**
     * Reporta un evento. OWN_EVENT si quien reporta es su creador (no tiene
     * sentido reportarse a si mismo); ALREADY_REPORTED si esa misma persona ya
     * tenia un reporte pendiente sobre este evento (no se crea uno nuevo, no
     * tiene sentido duplicar la cola); si no, CREATED.
     */
    ReportResult report(long eventId, User reporter, String reason);

    /** Cola de moderacion: los reportes pendientes, mas viejo primero (FIFO). */
    List<EventReport> listPending();

    /**
     * Descarta un reporte sin tocar el evento: no era para tanto. NOT_FOUND si
     * no existe o ya estaba resuelto (otro admin ya actuo mientras se revisaba).
     */
    ReportResolutionResult dismiss(long reportId, long adminUserId);

    /**
     * Da de baja el evento del reporte (mismo mecanismo que EventService.cancel(),
     * pero marcado como removido por moderacion, con el motivo del reporte) y
     * resuelve el reporte como ACTIONED. Cualquier otro reporte pendiente sobre
     * el mismo evento se resuelve junto con este (ya no tiene sentido seguir
     * mostrandolo en la cola). suspendOrganizer: si ademas hay que suspender al
     * creador del evento en el mismo paso (ver UserService.suspend()).
     */
    ReportResolutionResult removeEvent(long reportId, long adminUserId, boolean suspendOrganizer);
}
