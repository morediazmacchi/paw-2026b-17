// Contrato de servicio para moderacion de eventos (reportes). Implementacion:
// ModerationServiceImpl (modulo services). La suspension/promocion de USUARIOS
// vive en UserService, no aca: son acciones sobre el ciclo de vida de la
// cuenta, no sobre un reporte puntual.
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.AdminEventSort;
import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.ReportStatusFilter;
import ar.edu.itba.paw.model.ReportTarget;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

public interface ModerationService {

    // Largos maximos de los textos que escribe quien reporta y quien pide cambios. Los forms ya los validan para
    // dar feedback; los metodos los vuelven a chequear y devuelven INVALID (antes de escribir).
    int MAX_REPORT_REASON_LENGTH = 500;
    int MAX_CHANGES_MESSAGE_LENGTH = 1000;

    /**
     * Reporta un evento. ALREADY_REPORTED si esa misma persona ya
     * tenia un reporte pendiente sobre este evento (no se crea uno nuevo, no
     * tiene sentido duplicar la cola); si no, CREATED. Que quien reporta no sea el
     * organizador lo decide @PreAuthorize en el controller (ver EventSecurity.canInteract).
     */
    ReportResult report(long eventId, long reporterId, String reason);

    /**
     * El panel unificado "Reportes y seguimiento": todos los reportes (cualquier estado), filtrados por texto
     * libre (evento, quien reporto, motivo; null/vacio = sin filtrar) y por pestaña (ver ReportStatusFilter),
     * de a una pagina, el de actividad mas reciente primero (ver EventReport.getLastActivityAt()). Una pagina
     * mayor a la ultima devuelve la ultima, una menor a 1 la primera; sin reportes, la pagina 1 vacia.
     */
    PageResult<EventReport> searchPage(String query, ReportStatusFilter filter, int page);

    /**
     * El panel listo para mostrarse (mismo criterio de pagina que searchPage()): los reportes de la pagina con
     * los eventos reportados, quienes reportaron y los admins que pidieron cambios (cada grupo en una sola
     * consulta), mas los 5 contadores de las pestañas (sin filtrar por texto).
     */
    @Transactional(readOnly = true)
    ReportQueuePage searchQueue(String query, ReportStatusFilter filter, int page);

    /**
     * Un admin abre el detalle de un reporte: si seguia PENDING y nadie lo habia abierto antes, pasa a "en
     * revision" (ver EventReport.isInReview()) y devuelve true. No hace nada (ni falla) si ya estaba visto, si
     * no esta PENDING, o si el reporte no existe: abrir el detalle para mirarlo nunca es un error, solo que en
     * esos casos devuelve false (nada que marcar).
     */
    boolean markViewed(long reportId, long adminUserId);

    /**
     * El admin le pide al creador que corrija algo en el evento, en vez de
     * descartar el reporte o darlo de baja directo: le avisa (mail + bandeja)
     * con el mensaje que escribio, y el reporte pasa a CHANGES_REQUESTED
     * (pestaña "Modificacion solicitada" de searchQueue()) hasta que un admin lo revise de nuevo y lo
     * descarte o lo accione (dismiss()/removeEvent(), que tambien aceptan un
     * reporte en este estado). NOT_FOUND si el reporte no existe o ya no
     * estaba PENDING (ya se le habian pedido cambios, o ya estaba resuelto).
     * Que quien modera no sea el creador del evento reportado (conflicto de intereses) NO se
     * valida aca: lo decide @PreAuthorize en AdminController (ver AdminSecurity.canResolveReport).
     */
    ReportResolutionResult requestChanges(long reportId, long adminUserId, String message);

    /**
     * Descarta un reporte sin tocar el evento: no era para tanto (o, si tenia
     * cambios pedidos, el creador ya corrigio). NOT_FOUND si no existe o ya
     * no estaba abierto (otro admin ya actuo mientras se revisaba).
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
     * o ya no estaba abierto.
     */
    ReportResolutionResult removeEvent(long reportId, long adminUserId, boolean suspendOrganizer);

    /**
     * A que evento apunta el reporte y de quien es (consulta de dos columnas), en CUALQUIER estado
     * del reporte, incluso ya resuelto. Vacio solo si el reporte no existe. Lo usa la capa web
     * (AdminSecurity) para decidir si un admin puede resolverlo.
     */
    @Transactional(readOnly = true)
    Optional<ReportTarget> findReportTarget(long reportId);

    /**
     * Panel de admin "Eventos": una pagina de TODOS los eventos (ver EventService.findAllEventsPage()), cada uno con sus
     * conteos de inscripciones (ver RegistrationService.withRegistrationStats(): pendientes solo si pide aprobacion,
     * confirmados solo si no esta cancelado). {@code titleQuery} filtra por titulo; null o en blanco = sin filtro.
     */
    @Transactional(readOnly = true)
    PageResult<EventWithRegistrationStats> findAllEventsPage(String titleQuery, AdminEventSort sort, int page);
}
