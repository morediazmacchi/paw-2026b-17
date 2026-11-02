// Contrato de servicio para Event. Implementacion: EventServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// @Transactional aca documenta la garantia que promete cada metodo; el proxy
// de verdad lo arma Spring sobre EventServiceImpl (ver RegistrationService
// para el mismo criterio).
public interface EventService {
    /**
     * Crea un evento nuevo a partir de un DTO ya validado (ver EventForm.toDTO()):
     * el service arma el Event final y lo persiste, sin que el caller tenga que
     * tocar el modelo de dominio.
     */
    @Transactional(rollbackFor = Exception.class)
    Event create(EventDTO dto);
    @Transactional(readOnly = true)
    Optional<Event> findById(long id);

    /**
     * Lo minimo del evento para decidir un permiso (organizador, visibilidad, aprobacion): una consulta
     * de 4 columnas, sin cargar el Event. Lo usan las reglas de EventSecurity y AdminSecurity.
     */
    Optional<EventAccessSummary> findAccessById(long id);

    /**
     * Atajo de findById() para quien prefiere un 404 directo en vez de manejar
     * el Optional el mismo (ver EventController.detail(),
     * EventRegistrationController.unregister()): la decision de que "no existe"
     * es un 404 le corresponde al service, no a cada controller armando su
     * propio orElseThrow().
     *
     * @throws EventNotFoundException si el evento no existe.
     */
    @Transactional(readOnly = true)
    default Event findByIdOrThrow(final long id) {
        return findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }

    // Una sola consulta para varios ids, para armar un mapa id->Event sin un
    // findById() por cada uno (ver AdminController.reportQueueView(),
    // EventController.addCreatorReputation()).
    @Transactional(readOnly = true)
    List<Event> findByIds(List<Long> ids);
    // Pagina la respuesta: criteria.getPage() puede caer a la ultima pagina con
    // resultados si pedis una que no existe. Con sort RECOMENDADOS y un viewerId, el
    // orden lo calcula EventSimilarityService; sin viewerId cae a FECHA_ASC.
    @Transactional(readOnly = true)
    PageResult<Event> search(EventSearchCriteria criteria);

    // "Mis eventos": los creados por este usuario, de a una pagina. Primero los
    // que todavia no pasaron (el mas proximo primero) y despues los pasados (el
    // mas reciente primero). Una pagina mayor a la ultima devuelve la ultima,
    // una menor a 1 la primera; sin eventos, la pagina 1 vacia.
    @Transactional(readOnly = true)
    PageResult<Event> findPageByCreatorId(long creatorId, int page);

    /** Para el badge de "Mis eventos" (ver ProfileController): sin traer las filas. */
    @Transactional(readOnly = true)
    long countByCreatorId(long creatorId);

    /**
     * Edita un evento existente. No cambia quien lo creo ni cuando se creo.
     * Si dto.getNewImages() viene vacio, se mantiene la galeria que el evento
     * ya tenia; si no, las nuevas imagenes se suben (dentro de esta misma
     * transaccion) y reemplazan toda la galeria vieja.
     *
     * Que quien lo edita sea el creador NO se valida aca: lo decide @PreAuthorize en el
     * controller (ver EventSecurity.isOwner).
     *
     * @throws EventNotFoundException si el evento no existe.
     * @throws EventCancelledException si el evento ya esta cancelado (se cancelo justo
     *         entre medio: quien llama ya deberia haber chequeado esto antes, ver
     *         EventAccessService#getEditableEvent).
     * @throws InvalidEventException si el cupo nuevo es menor a los lugares ya ocupados.
     */
    @Transactional(rollbackFor = Exception.class)
    EventUpdateOutcome update(long eventId, EventDTO dto);

    /**
     * Cancela (soft-delete) un evento existente y avisa por mail a cada
     * inscripto confirmado. Que quien lo cancela sea el creador NO se valida aca: lo decide
     * @PreAuthorize en el controller (ver EventSecurity.isOwner).
     *
     * @throws EventNotFoundException si el evento no existe.
     */
    @Transactional(rollbackFor = Exception.class)
    EventCancelResult cancel(long eventId);

    /**
     * Baja por moderacion (ver ModerationService): mismo efecto que cancel()
     * para quien esta anotado (mismos avisos a los inscriptos), pero sin
     * chequeo de ownership (lo pide un admin, no el creador) y ademas le
     * avisa al creador con el motivo del reporte.
     */
    @Transactional(rollbackFor = Exception.class)
    EventCancelResult removeByAdmin(long eventId, String reason);

    /**
     * Un admin revisa y aprueba un evento recien creado (ver EventApprovalStatus):
     * recien ahi se vuelve visible publicamente. Avisa al creador.
     * Que un admin no pueda aprobar/rechazar su propio evento (conflicto de intereses) NO se valida
     * aca: lo decide @PreAuthorize en AdminController (ver AdminSecurity.canModerateEvent).
     */
    @Transactional(rollbackFor = Exception.class)
    EventApprovalResult approve(long eventId);

    /**
     * Un admin rechaza un evento recien creado: queda invisible para siempre
     * (no hay reenvio a revision), igual que uno dado de baja por moderacion.
     * Avisa al creador con el motivo.
     */
    @Transactional(rollbackFor = Exception.class)
    EventApprovalResult reject(long eventId, String reason);

    /**
     * Cola de aprobacion del admin: los eventos PENDING, mas viejo primero
     * (FIFO), de a una pagina. Mismo criterio de pagina fuera de rango que
     * findPageByCreatorId() (una mayor a la ultima da la ultima, etc.).
     */
    @Transactional(readOnly = true)
    PageResult<Event> findPendingApprovalPage(int page);

    /**
     * Banner de /eventos (ver EventController.eventos()): los eventos que un
     * admin destaco, mas reciente primero. Ya vienen filtrados a
     * publicos/activos/futuros, igual que search(). Se ven siempre, tambien
     * los de quien navega.
     */
    @Transactional(readOnly = true)
    List<Event> findFeatured(int limit);

    /** Un admin marca o desmarca este evento para el banner (ver AdminController). */
    @Transactional(rollbackFor = Exception.class)
    boolean setFeatured(long eventId, boolean featured);
}
