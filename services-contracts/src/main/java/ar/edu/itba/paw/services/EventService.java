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

    // Hasta cuantos años a futuro se puede programar un evento. El form ya lo valida para dar feedback;
    // create() y update() lo vuelven a chequear (la fecha tampoco puede ser pasada).
    int MAX_YEARS_AHEAD = 2;

    // Largo maximo del motivo con el que un admin rechaza un evento (reject() devuelve INVALID si lo supera o esta vacio).
    int MAX_REJECT_REASON_LENGTH = 500;

    /**
     * Crea un evento nuevo de ese creador a partir de un DTO ya validado (ver EventForm.toDTO()):
     * el service arma el Event final (con el mail del creador, que busca por su id) y lo persiste, sin
     * que el caller tenga que tocar el modelo de dominio. Devuelve ademas cual es el proximo paso del flujo
     * (EventCreation.isNeedsInvitations()).
     *
     * @throws InvalidEventException si la fecha es pasada o a mas de {@link #MAX_YEARS_AHEAD} años, si no se
     *         eligio ninguna categoria, si alguna no existe, o si la base rechaza un dato.
     * @throws InvalidImageException si se suben mas de Event.MAX_IMAGES imagenes o alguna es invalida.
     */
    @Transactional(rollbackFor = Exception.class)
    EventCreation create(long creatorId, EventDTO dto);
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
    /**
     * Busca en el listado /eventos. Pagina la respuesta: criteria.getPage() puede caer a la ultima pagina
     * con resultados si pedis una que no existe.
     * El orden que se aplica lo decide el service a partir del pedido (criteria.getSort(), null = no
     * pidio ninguno): RECOMENDADOS solo existe para quien tiene historial de asistencia (criteria.getViewerId()).
     * Sin pedir orden, quien tiene historial recibe RECOMENDADOS y el resto FECHA_ASC; RECOMENDADOS pedido
     * sin historial cae a FECHA_ASC. El resultado trae el orden efectivo y los ordenes a ofrecer (sin
     * RECOMENDADOS si no tiene historial). Con RECOMENDADOS el orden lo calcula EventSimilarityService.
     */
    @Transactional(readOnly = true)
    EventSearchResult search(EventSearchCriteria criteria);

    /**
     * "Mis eventos": una pagina de los eventos del creador (mismo orden y criterio de pagina que
     * findPageByCreatorId()), cada uno con sus conteos de inscripciones. Los pendientes solo se cuentan en
     * un evento que pide aprobacion (si no, vienen en null) y los confirmados solo en uno no cancelado (si
     * esta cancelado, 0). Los conteos son UNA consulta por tipo para toda la pagina, no una por evento.
     */
    @Transactional(readOnly = true)
    PageResult<EventWithRegistrationStats> findMyEventsPage(long creatorId, int page);

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
     * Panel de admin "Eventos": una pagina de TODOS los eventos (cualquier estado de aprobacion,
     * visibilidad o cancelacion, de cualquier creador), mas recientes primero, con sus conteos de
     * inscripciones (mismo criterio que findMyEventsPage: pendientes solo si pide aprobacion,
     * confirmados solo si no esta cancelado). Es la unica forma de moderar algo que todavia no es
     * publico o que ya se dio de baja: search() nunca los muestra.
     */
    @Transactional(readOnly = true)
    PageResult<EventWithRegistrationStats> findAllEventsPage(int page);

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
     * @throws EventCancelledException si el evento ya esta cancelado.
     * @throws EventRejectedException si un admin lo rechazo (el rechazo es definitivo: no se edita).
     * @throws InvalidEventException si el cupo nuevo es menor a los lugares ya ocupados, la fecha es pasada o a mas
     *         de {@link #MAX_YEARS_AHEAD} años, o no queda ninguna categoria.
     * @throws InvalidImageException si se suben mas de Event.MAX_IMAGES imagenes o alguna es invalida.
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
