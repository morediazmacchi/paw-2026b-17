// Contrato de servicio para Event. Implementacion: EventServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.model.AdminEventSort;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
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

    // Largo maximo del mensaje con el que un admin pide cambios (requestChanges() devuelve INVALID si lo
    // supera o esta vacio). Mismo limite que ModerationService.MAX_CHANGES_MESSAGE_LENGTH (pedir cambios
    // sobre un reporte), por consistencia entre las dos pantallas de "pedir cambios" de la app.
    int MAX_CHANGES_MESSAGE_LENGTH = 1000;

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
     * Si se busco texto (criteria.getText(), ver SearchTerms), el orden por defecto es RELEVANCIA, tambien para quien
     * tiene historial; RELEVANCIA pedido sin texto se trata como "no pidio ninguno", y solo se ofrece cuando hay texto.
     */
    @Transactional(readOnly = true)
    EventSearchResult search(EventSearchCriteria criteria);

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
     * visibilidad o cancelacion, de cualquier creador), mas recientes primero. Es la unica forma de moderar algo
     * que todavia no es publico o que ya se dio de baja: search() nunca los muestra. Sus conteos de inscripciones
     * los agrega ModerationService.findAllEventsPage().
     *
     * {@code titleQuery} filtra por titulo (contiene, sin distinguir mayusculas); null o en blanco = sin filtro.
     * {@code sort} null = el mas recientemente creado primero.
     */
    @Transactional(readOnly = true)
    PageResult<Event> findAllEventsPage(String titleQuery, AdminEventSort sort, int page);

    /**
     * Edita un evento existente. No cambia quien lo creo ni cuando se creo.
     * Si dto.getNewImages() viene vacio, se mantiene la galeria que el evento
     * ya tenia; si no, las nuevas imagenes se suben (dentro de esta misma
     * transaccion) y reemplazan toda la galeria vieja.
     *
     * Que quien lo edita sea el creador NO se valida aca: lo decide @PreAuthorize en el
     * controller (ver EventSecurity.isOwner). Si el evento estaba en CHANGES_REQUESTED (ver
     * requestChanges()), un guardado exitoso lo manda de nuevo a PENDING: es la forma de
     * "reenviar a revision" (EventUpdateOutcome.isResubmittedForReview() lo confirma).
     *
     * @throws EventNotFoundException si el evento no existe.
     * @throws EventCancelledException si el evento ya esta cancelado.
     * @throws EventRejectedException si un admin lo rechazo (el rechazo es definitivo: no se edita).
     * @throws InvalidEventException si el cupo nuevo es menor a los lugares ya ocupados, la fecha es pasada o a mas
     *         de {@link #MAX_YEARS_AHEAD} años, o no queda ninguna categoria.
     * @throws InvalidImageException si se suben mas de Event.MAX_IMAGES imagenes o alguna es invalida.
     *
     * Avisa que el evento cambio con un {@link EventUpdatedEvent} (dentro de esta misma transaccion). Lo escuchan
     * RegistrantsEventListener (en esta transaccion: la bandeja de los confirmados y hacer avanzar la lista de espera
     * si subio el cupo) y MassNotifier (el mail a los confirmados, en segundo plano, despues del commit y de a
     * tandas). Asi EventService no depende de RegistrationService, que si depende de este.
     */
    @Transactional(rollbackFor = Exception.class)
    EventUpdateOutcome update(long eventId, EventDTO dto);

    /**
     * Cancela (soft-delete) un evento existente y avisa a sus inscriptos (con un {@link EventCancelledEvent}: la
     * bandeja dentro de esta transaccion y los mails en segundo plano despues del commit, ver update()). Que quien lo cancela sea el creador NO se valida aca: lo decide
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
     * recien ahi se vuelve visible publicamente. Avisa al creador y, con un {@link EventApprovedEvent}, a quienes
     * siguen al organizador (la bandeja dentro de esta transaccion; los mails en segundo plano, despues del commit
     * y de a tandas, ver MassNotifier).
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
     * Un admin no aprueba ni rechaza un evento pendiente: le pide cambios (ver
     * EventApprovalStatus.CHANGES_REQUESTED). A diferencia de reject(), esto NO es definitivo: el
     * evento sigue existiendo y editable, y vuelve solo a la cola de pendientes cuando el creador
     * lo edita (ver update()). Avisa al creador con el mensaje (mismo mecanismo que
     * ModerationService.requestChanges() para reportes: mail + bandeja, InboxMessageType.EVENT_CHANGES_REQUESTED).
     * Que un admin no pueda hacer esto sobre su propio evento NO se valida aca: lo decide
     * @PreAuthorize en AdminController (ver AdminSecurity.canModerateEvent).
     */
    @Transactional(rollbackFor = Exception.class)
    EventApprovalResult requestChanges(long eventId, String message);

    /**
     * Banner de /eventos (ver EventController.eventos()): los eventos con mas inscripciones
     * CONFIRMADAS en este momento, de mas a menos (a lo sumo "limit"). Ya vienen filtrados a
     * publicos/aprobados/activos/futuros, igual que search(); un evento sin ninguna inscripcion
     * confirmada no entra, asi que puede venir menos de "limit" o ninguno. Si dos empatan va primero
     * el que ocurre antes. Se ven siempre, tambien los de quien navega.
     */
    @Transactional(readOnly = true)
    List<Event> findMostRegistered(int limit);

    /**
     * El evento con la fila bloqueada (SELECT ... FOR UPDATE) hasta que termine la transaccion de quien llama:
     * mientras se chequea y se cambia algo que depende del cupo, nadie mas se anota. Orden de locks de toda la app:
     * primero la fila del evento, despues las de registration. Vacio si no existe.
     */
    @Transactional(rollbackFor = Exception.class)
    Optional<Event> findByIdForUpdate(long id);

    /**
     * Cuantos lugares tiene ocupados el evento: inscripciones CONFIRMADAS mas invitaciones sin usar (ni los pedidos
     * pendientes ni la lista de espera ocupan cupo). Es la definicion unica de "ocupado": la usan el cupo, la lista
     * de espera y la edicion del cupo.
     */
    @Transactional(readOnly = true)
    long occupiedSpots(long eventId);

    /**
     * Cuantos eventos del organizador aparecen en su perfil publico y TODAVIA no pasaron (fecha >= now): publicos,
     * aprobados y no cancelados. "now" lo fija quien llama para usar el mismo en los dos grupos (proximos /
     * realizados).
     */
    @Transactional(readOnly = true)
    long countUpcomingPublicByCreator(long creatorId, Date now);

    /** Igual que countUpcomingPublicByCreator(), pero los que ya pasaron (fecha < now). */
    @Transactional(readOnly = true)
    long countPastPublicByCreator(long creatorId, Date now);

    /**
     * Una pagina de los eventos que cuenta countUpcomingPublicByCreator() (el mas proximo primero). {@code total} es
     * lo que esa cuenta devolvio: lo pasa quien llama porque ya lo necesitaba (el numero de la pestaña) y asi no se
     * cuenta dos veces. Una pagina fuera de rango cae a la ultima; sin eventos, la 1 vacia (y no consulta nada).
     */
    @Transactional(readOnly = true)
    PageResult<Event> findUpcomingPublicByCreator(long creatorId, Date now, int page, int pageSize, long total);

    /**
     * Igual que findUpcomingPublicByCreator(), pero los que cuenta countPastPublicByCreator() (el mas reciente
     * primero).
     */
    @Transactional(readOnly = true)
    PageResult<Event> findPastPublicByCreator(long creatorId, Date now, int page, int pageSize, long total);

    /**
     * Cuantos eventos cumplen esos filtros (los mismos que veria alguien en /eventos, mismo SQL), sin traerlos.
     * Es lo que usa la busqueda guardada para saber si hay novedades. No aplica orden ni pagina.
     */
    @Transactional(readOnly = true)
    long countMatching(EventSearchCriteria criteria);

    /**
     * Una pagina de los eventos que cumplen esos filtros, con el orden y la pagina que traiga criteria (mismo SQL que
     * /eventos pero sin las reglas de orden de search(): RECOMENDADOS necesita historial y no se resuelve aca).
     */
    @Transactional(readOnly = true)
    List<Event> findMatching(EventSearchCriteria criteria);

    /** Ids de eventos aprobados, no cancelados, que empiezan entre "from" y "until" y a los que todavia no se les mando el recordatorio de 24 hs. */
    @Transactional(readOnly = true)
    List<Long> findIdsNeedingReminder(Date from, Date until);

    /**
     * "Reclama" el recordatorio de ese evento (marca que ya se mando): true si lo reclamo ahora, false si otra
     * corrida ya lo habia reclamado. Es lo que impide mandarlo dos veces.
     */
    @Transactional(rollbackFor = Exception.class)
    boolean markReminderSent(long eventId, Date when);
}
