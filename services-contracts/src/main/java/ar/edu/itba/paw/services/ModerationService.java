// Contrato de servicio para moderacion de eventos (reportes). Implementacion:
// ModerationServiceImpl (modulo services). La suspension/promocion de USUARIOS
// vive en UserService, no aca: son acciones sobre el ciclo de vida de la
// cuenta, no sobre un reporte puntual.
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.User;

public interface ModerationService {

    /**
     * Reporta un evento. OWN_EVENT si quien reporta es su creador (no tiene
     * sentido reportarse a si mismo); ALREADY_REPORTED si esa misma persona ya
     * tenia un reporte pendiente sobre este evento (no se crea uno nuevo, no
     * tiene sentido duplicar la cola); si no, CREATED.
     */
    ReportResult report(long eventId, User reporter, String reason);

    /**
     * Cola de moderacion: los reportes pendientes, mas viejo primero (FIFO), de
     * a una pagina. Una pagina mayor a la ultima devuelve la ultima, una menor
     * a 1 la primera; sin reportes, la pagina 1 vacia.
     */
    PageResult<EventReport> findPendingPage(int page);

    /**
     * Panel de seguimiento: los reportes con cambios pedidos (ver
     * requestChanges()), esperando que un admin los revise de nuevo, mas
     * viejo primero (FIFO), de a una pagina (mismo criterio de pagina fuera de
     * rango que findPendingPage()).
     */
    PageResult<EventReport> findChangesRequestedPage(int page);

    /**
     * El admin le pide al creador que corrija algo en el evento, en vez de
     * descartar el reporte o darlo de baja directo: le avisa (mail + bandeja)
     * con el mensaje que escribio, y el reporte pasa a CHANGES_REQUESTED
     * (ver listChangesRequested()) hasta que un admin lo revise de nuevo y lo
     * descarte o lo accione (dismiss()/removeEvent(), que tambien aceptan un
     * reporte en este estado). NOT_FOUND si el reporte no existe o ya no
     * estaba PENDING (ya se le habian pedido cambios, o ya estaba resuelto).
     * OWN_EVENT si quien modera es el creador del evento reportado (conflicto
     * de intereses: no puede resolver un reporte sobre su propio contenido).
     */
    ReportResolutionResult requestChanges(long reportId, long adminUserId, String message);

    /**
     * Descarta un reporte sin tocar el evento: no era para tanto (o, si tenia
     * cambios pedidos, el creador ya corrigio). NOT_FOUND si no existe o ya
     * no estaba abierto (otro admin ya actuo mientras se revisaba). OWN_EVENT
     * si quien modera es el creador del evento reportado.
     */
    ReportResolutionResult dismiss(long reportId, long adminUserId);

    /**
     * Da de baja el evento del reporte (mismo mecanismo que EventService.cancel(),
     * pero marcado como removido por moderacion, con el motivo del reporte) y
     * resuelve el reporte como ACTIONED. Cualquier otro reporte abierto (PENDING
     * o CHANGES_REQUESTED) sobre el mismo evento se resuelve junto con este (ya
     * no tiene sentido seguir mostrandolo en la cola o en el seguimiento).
     * suspendOrganizer: si ademas hay que suspender al creador del evento en el
     * mismo paso (ver UserService.suspend()). NOT_FOUND si el reporte no existe
     * o ya no estaba abierto; OWN_EVENT si quien modera es el creador del
     * evento reportado (asi tampoco puede suspenderse a si mismo via
     * suspendOrganizer).
     */
    ReportResolutionResult removeEvent(long reportId, long adminUserId, boolean suspendOrganizer);
}
