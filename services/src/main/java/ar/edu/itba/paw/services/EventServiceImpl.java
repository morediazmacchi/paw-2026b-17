// Implementa el contrato EventService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.dto.NewImageData;
import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.AdminEventSort;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.EventCategory;
import ar.edu.itba.paw.model.EventCategoryInfo;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventCancelledException;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.model.exception.EventRejectedException;
import ar.edu.itba.paw.model.exception.InvalidEventException;
import ar.edu.itba.paw.model.exception.InvalidImageException;
import ar.edu.itba.paw.persistence.CategoryDao;
import ar.edu.itba.paw.persistence.EventDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class EventServiceImpl implements EventService {

    static final int PAGE_SIZE = 12;

    private final EventDao eventDao;
    private final UserService userService;
    private final CategoryDao categoryDao;
    private final NotificationService notificationService;
    private final RegistrationService registrationService;
    private final InboxService inboxService;
    private final ImageService imageService;
    private final EventSimilarityService eventSimilarityService;
    private final FollowService followService;

    @Autowired
    public EventServiceImpl(final EventDao eventDao, final UserService userService,
                            final CategoryDao categoryDao,
                            final NotificationService notificationService,
                            final RegistrationService registrationService,
                            final InboxService inboxService, final ImageService imageService,
                            final EventSimilarityService eventSimilarityService,
                            final FollowService followService) {
        this.eventDao = eventDao;
        this.userService = userService;
        this.categoryDao = categoryDao;
        this.notificationService = notificationService;
        this.registrationService = registrationService;
        this.inboxService = inboxService;
        this.imageService = imageService;
        this.eventSimilarityService = eventSimilarityService;
        this.followService = followService;
    }

    // Sube cada imagen y devuelve sus ids, en el mismo orden en que se cargaron
    // (ese orden es el de la galeria, ver Event.imageIds -- la primera es la
    // portada). A proposito DENTRO de la transaccion de quien llama (create()/
    // update()): si algo despues falla (otra imagen invalida, una categoria que
    // no existe), Spring deshace esto tambien -- antes esto vivia en el
    // controller, fuera de cualquier transaccion, y una imagen ya subida con
    // exito podia quedar huerfana si el evento despues no se llegaba a crear.
    private List<Long> uploadImages(final List<NewImageData> images) {
        // El form ya lo valida para dar feedback; esto es la defensa del service. Antes de subir nada.
        if (images.size() > Event.MAX_IMAGES) {
            throw new InvalidImageException(InvalidImageException.Reason.TOO_MANY);
        }
        final List<Long> ids = new ArrayList<>();
        for (final NewImageData image : images) {
            final Image saved = imageService.create(image.getData(), image.getContentType(), image.getFileName());
            ids.add(saved.getId());
        }
        return ids;
    }

    @Override
    @Transactional
    public EventCreation create(final long creatorId, final EventDTO dto) {
        Objects.requireNonNull(dto, "dto");
        // El dueno es un objeto User (relacion), no solo su numero; de el sale tambien el mail del creador.
        // Dentro de la transaccion findById lo trae una vez y despues sale de la memoria.
        final User creator = userService.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el usuario creador " + creatorId));
        // Se validan las reglas del evento ANTES de escribir (subir imagenes incluido).
        validateEventDate(dto);
        validateHasCategories(dto);
        final List<Long> imageIds = uploadImages(dto.getNewImages());
        final Event event = dto.toEvent(creator.getEmail(), imageIds);
        event.setCreator(creator);
        addCategories(event, dto.getCategories());

        final Event created;
        try {
            created = eventDao.create(event);
        } catch (final DataIntegrityViolationException e) {
            throw new InvalidEventException(categoryConstraintViolated(e)
                    ? InvalidEventException.Reason.CATEGORY_NOT_FOUND : InvalidEventException.Reason.INVALID_DATA, e);
        }
        notificationService.notifyEventPendingApproval(created, creator.getName());
        return new EventCreation(created, created.isPrivate());
    }
    @Override
    @Transactional(readOnly = true)
    public Optional<Event> findById(final long id) { return eventDao.findById(id); }
    @Override
    @Transactional(readOnly = true)
    public Optional<EventAccessSummary> findAccessById(final long id) { return eventDao.findAccessById(id); }
    @Override
    @Transactional(readOnly = true)
    public List<Event> findByIds(final List<Long> ids) { return eventDao.findByIds(ids); }
    @Override
    @Transactional(readOnly = true)
    public PageResult<Event> findPageByCreatorId(final long creatorId, final int page) {
        final long total = eventDao.countByCreatorId(creatorId);
        final int effectivePage = PageResult.clampPage(page, total, PAGE_SIZE);
        return new PageResult<>(eventDao.findByCreatorId(creatorId, effectivePage, PAGE_SIZE),
                total, effectivePage, PAGE_SIZE);
    }
    @Override
    @Transactional(readOnly = true)
    public long countByCreatorId(final long creatorId) {
        return eventDao.countByCreatorId(creatorId);
    }
    @Override
    @Transactional(readOnly = true)
    public PageResult<EventWithRegistrationStats> findMyEventsPage(final long creatorId, final int page) {
        return withRegistrationStats(findPageByCreatorId(creatorId, page));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<EventWithRegistrationStats> findAllEventsPage(final String titleQuery,
                                                                    final AdminEventSort sort, final int page) {
        // Un texto en blanco es "sin filtro": el DAO solo distingue null.
        final String trimmed = titleQuery == null ? "" : titleQuery.trim();
        final String filter = trimmed.isEmpty() ? null : trimmed;
        final long total = eventDao.countAll(filter);
        final int effectivePage = PageResult.clampPage(page, total, PAGE_SIZE);
        final List<Event> content = eventDao.findAll(filter, sort, effectivePage, PAGE_SIZE);
        return withRegistrationStats(new PageResult<>(content, total, effectivePage, PAGE_SIZE));
    }

    // Le agrega a cada evento de la pagina sus conteos de inscripciones (ver findMyEventsPage()/
    // findAllEventsPage()). Que se cuenta es regla de negocio: pendientes solo si pide aprobacion y
    // confirmados solo si no esta cancelado (uno cancelado no muestra el boton de cancelar). Se
    // filtran los ids y despues es UNA consulta por tipo para toda la pagina, no una por evento.
    private PageResult<EventWithRegistrationStats> withRegistrationStats(final PageResult<Event> events) {
        final List<Long> requiringApprovalIds = new ArrayList<>();
        final List<Long> activeIds = new ArrayList<>();
        for (final Event event : events.getContent()) {
            if (event.isRequiresApproval()) {
                requiringApprovalIds.add(event.getId());
            }
            if (!event.isCancelled()) {
                activeIds.add(event.getId());
            }
        }
        final Map<Long, Long> pendingByEvent = registrationService.countPendingByEvents(requiringApprovalIds);
        final Map<Long, Long> confirmedByEvent = registrationService.countConfirmedByEvents(activeIds);

        final List<EventWithRegistrationStats> content = new ArrayList<>();
        for (final Event event : events.getContent()) {
            final Long pending = event.isRequiresApproval() ? pendingByEvent.getOrDefault(event.getId(), 0L) : null;
            final long confirmed = confirmedByEvent.getOrDefault(event.getId(), 0L);
            content.add(new EventWithRegistrationStats(event, confirmed, pending));
        }
        return new PageResult<>(content, events.getTotalElements(), events.getPage(), events.getPageSize());
    }

    @Override
    @Transactional(readOnly = true)
    public EventSearchResult search(final EventSearchCriteria criteria) {
        final boolean hasHistory = criteria.getViewerId() != null
                && eventSimilarityService.hasAttendanceHistory(criteria.getViewerId());
        final boolean hasText = criteria.hasSearchTerms();
        // RECOMENDADOS solo se ofrece a quien tiene historial: sin el, el orden seria identico a "Mas
        // proximos" y la opcion no diria nada. RELEVANCIA solo si se busco texto: sin texto no hay con que comparar.
        final List<EventSort> availableSorts = new ArrayList<>();
        for (final EventSort option : EventSort.values()) {
            if ((hasHistory || option != EventSort.RECOMENDADOS) && (hasText || option != EventSort.RELEVANCIA)) {
                availableSorts.add(option);
            }
        }
        final EventSort sort = effectiveSort(criteria.getSort(), hasHistory, hasText);
        criteria.setSort(sort);
        return new EventSearchResult(searchPage(criteria), sort, availableSorts);
    }

    // El orden que pidio la persona si se puede aplicar. Si no pidio ninguno (null) o pidio uno que no tiene sentido
    // (RECOMENDADOS sin historial, RELEVANCIA sin texto buscado), se usa el de por defecto: RELEVANCIA si busco texto
    // (lo que buscaba pesa mas que lo que le recomendamos), si no RECOMENDADOS para quien ya asistio a algo y
    // FECHA_ASC para el resto.
    private static EventSort effectiveSort(final EventSort requested, final boolean hasHistory, final boolean hasText) {
        final boolean applicable = requested != null
                && !(requested == EventSort.RECOMENDADOS && !hasHistory)
                && !(requested == EventSort.RELEVANCIA && !hasText);
        if (applicable) {
            return requested;
        }
        if (hasText) {
            return EventSort.RELEVANCIA;
        }
        return hasHistory ? EventSort.RECOMENDADOS : EventSort.FECHA_ASC;
    }

    private PageResult<Event> searchPage(final EventSearchCriteria criteria) {
        criteria.setPageSize(PAGE_SIZE);
        // RECOMENDADOS no es una columna: su puntaje lo calcula la base (EventSimilarityService pide la pagina ya
        // ordenada a EventDao.findRecommendedPage; solo llega aca con historial, y por lo tanto con viewerId).
        if (criteria.getSort() == EventSort.RECOMENDADOS) {
            return eventSimilarityService.searchRecommended(criteria);
        }
        final long totalElements = eventDao.countSearch(criteria);
        final int totalPages = criteria.getPageSize() <= 0 ? 0
                : (int) Math.ceil((double) totalElements / criteria.getPageSize());
        // Si pidieron una pagina que no existe, cae a la ultima con resultados (nunca vacia por eso).
        final int effectivePage = totalPages == 0 ? 1 : Math.min(Math.max(criteria.getPage(), 1), totalPages);
        criteria.setPage(effectivePage);

        final List<Event> content = eventDao.search(criteria);
        return new PageResult<>(content, totalElements, effectivePage, criteria.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventUpdateOutcome update(final long eventId, final EventDTO dto) {
        Objects.requireNonNull(dto, "dto");
        // Con lock (SELECT ... FOR UPDATE), igual que register(): mientras se
        // chequea y se cambia el cupo, nadie se puede anotar. Sin esto, alguien
        // podia confirmarse entre el chequeo y el UPDATE y quedar mas gente que cupo.
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
        if (!existing.isPresent()) {
            throw new EventNotFoundException(eventId);
        }
        if (existing.get().isCancelled()) {
            // Se cancelo justo entre medio: quien llama ya deberia haber chequeado
            // esto antes (ver EventAccessService#getEditableEvent), esto es la
            // defensa de la carrera, no el chequeo principal.
            throw new EventCancelledException(eventId);
        }
        // Un evento rechazado por un admin no se edita nunca (el rechazo es definitivo): misma regla que
        // EventAccessService.getEditableEvent(), que es la que usa el GET. Esto cierra el bypass por codigo.
        if (existing.get().isRejected()) {
            throw new EventRejectedException(eventId);
        }
        validateEventDate(dto);
        validateHasCategories(dto);
        // No alcanza con los confirmados: una invitacion sin contestar o un
        // pedido pendiente tambien tienen su lugar guardado (ver occupiedSpots).
        if (dto.getMaxCapacity() != null && dto.getMaxCapacity() < registrationService.occupiedSpots(eventId)) {
            throw new InvalidEventException(InvalidEventException.Reason.CAPACITY_BELOW_OCCUPIED, "maxCapacity");
        }
        // Si no se subio ninguna foto nueva, se mantiene la galeria que el
        // evento ya tenia; si se subio al menos una, las nuevas reemplazan toda
        // la galeria vieja. Subirlas aca (no en el controller) las deja dentro
        // de esta misma transaccion: si algo de abajo fallara, se deshacen
        // tambien, no quedan huerfanas.
        final List<Long> imageIds = dto.getNewImages().isEmpty()
                ? existing.get().getImageIds()
                : uploadImages(dto.getNewImages());
        final Event changes = dto.toEvent(existing.get().getCreatorEmail(), imageIds);
        changes.setId(eventId);
        addCategories(changes, dto.getCategories());
        eventDao.update(changes);
        // Si estaba en CHANGES_REQUESTED, este guardado ES el "reenviar a revision": vuelve solo a
        // PENDING, sin un boton aparte. No toca changes_requested_message (ver EventDao.resubmitForReview()).
        final boolean resubmitted = existing.get().isChangesRequested();
        if (resubmitted) {
            eventDao.resubmitForReview(eventId);
        }
        // Solo a los confirmados, mismo criterio que cancel(): a alguien
        // pendiente/rechazado/en lista de espera todavia no tiene un lugar
        // asegurado en base al cual le importe que cambio el evento.
        for (final Registration registration : registrationService.findByEventId(eventId)) {
            if (registration.isConfirmed()) {
                notificationService.notifyEventUpdated(changes, registration);
                inboxService.add(registration.getEmail(), InboxMessageType.EVENT_UPDATED, eventId, null, null);
            }
        }
        // Si subio el cupo (o se lo saco), los primeros de la lista de espera entran.
        // Despues del aviso de cambios: a los que entran ahora les llega ya el mail
        // de que estan confirmados, no hace falta ademas contarles que el evento cambio.
        final int promoted = registrationService.promoteFromWaitlist(eventId);
        return new EventUpdateOutcome(changes, promoted, resubmitted);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventCancelResult cancel(final long eventId) {
        // Con for update.
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
        if (!existing.isPresent()) {
            throw new EventNotFoundException(eventId);
        }
        final Event event = existing.get();
        if (event.isCancelled()) {
            return EventCancelResult.ALREADY_CANCELLED;
        }
        eventDao.cancel(eventId);
        notifyRegistrantsOfCancellation(event);
        return EventCancelResult.CANCELLED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventCancelResult removeByAdmin(final long eventId, final String reason) {
        // Con lock, igual que cancel(): si el creador cancelara el suyo justo en
        // el medio, no tiene que pasar que esto lo pise y mande el aviso de
        // "removido por un admin" sobre un evento que ya estaba cancelado.
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
        if (!existing.isPresent()) {
            return EventCancelResult.NOT_FOUND;
        }
        final Event event = existing.get();
        if (event.isCancelled()) {
            return EventCancelResult.ALREADY_CANCELLED;
        }
        eventDao.removeByAdmin(eventId, reason);
        // Mismo aviso a quien estaba anotado que una cancelacion comun (para
        // ellos el efecto es el mismo: el evento ya no va). El creador, en
        // cambio, recibe un aviso aparte y distinto: a el le interesa el motivo.
        notifyRegistrantsOfCancellation(event);
        notificationService.notifyEventRemovedByAdmin(event, reason);
        inboxService.add(event.getCreatorEmail(), InboxMessageType.EVENT_REMOVED_BY_ADMIN, eventId, null, reason);
        return EventCancelResult.CANCELLED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventApprovalResult approve(final long eventId) {
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
        if (!existing.isPresent()) {
            return EventApprovalResult.NOT_FOUND;
        }
        final Event event = existing.get();
        if (!event.isPendingApproval()) {
            return EventApprovalResult.ALREADY_DECIDED;
        }
        eventDao.approve(eventId);
        notificationService.notifyEventApproved(event);
        inboxService.add(event.getCreatorEmail(), InboxMessageType.EVENT_APPROVED, eventId, null, null);
        notifyFollowersOfNewEvent(event);
        return EventApprovalResult.OK;
    }

    // A quien sigue a este organizador (ver FollowService), un aviso de que tiene un evento nuevo. Va aca
    // (no en create()) porque "nuevo" para el publico es recien cuando un admin lo aprueba, no cuando se
    // crea: antes de eso nadie fuera del creador y un admin puede verlo.
    private void notifyFollowersOfNewEvent(final Event event) {
        final Long organizerId = event.getCreatorId();
        if (organizerId == null) {
            return;
        }
        final String organizerName = event.getCreator().getName();
        for (final User follower : followService.findFollowers(organizerId)) {
            inboxService.add(follower.getEmail(), InboxMessageType.FOLLOWED_USER_NEW_EVENT, event.getId(), organizerName, null);
            notificationService.notifyFollowedUserNewEvent(follower, event, organizerName);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventApprovalResult reject(final long eventId, final String reason) {
        if (reason == null || reason.trim().isEmpty() || reason.length() > MAX_REJECT_REASON_LENGTH) {
            return EventApprovalResult.INVALID;
        }
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
        if (!existing.isPresent()) {
            return EventApprovalResult.NOT_FOUND;
        }
        final Event event = existing.get();
        if (!event.isPendingApproval()) {
            return EventApprovalResult.ALREADY_DECIDED;
        }
        eventDao.reject(eventId, reason);
        notificationService.notifyEventRejected(event, reason);
        inboxService.add(event.getCreatorEmail(), InboxMessageType.EVENT_REJECTED, eventId, null, reason);
        return EventApprovalResult.OK;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventApprovalResult requestChanges(final long eventId, final String message) {
        if (message == null || message.trim().isEmpty() || message.length() > MAX_CHANGES_MESSAGE_LENGTH) {
            return EventApprovalResult.INVALID;
        }
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
        if (!existing.isPresent()) {
            return EventApprovalResult.NOT_FOUND;
        }
        final Event event = existing.get();
        if (!event.isPendingApproval()) {
            return EventApprovalResult.ALREADY_DECIDED;
        }
        eventDao.requestChanges(eventId, message);
        // Mismo mecanismo que ModerationService.requestChanges() para reportes: mail con link directo
        // a editar, mas el mensaje en la bandeja (reusa el mismo InboxMessageType -- "un admin te pidio
        // cambios" significa lo mismo aca que en el flujo de reportes).
        notificationService.notifyEventChangesRequested(event, message);
        inboxService.add(event.getCreatorEmail(), InboxMessageType.EVENT_CHANGES_REQUESTED, eventId, null, message);
        return EventApprovalResult.OK;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> findMostRegistered(final int limit) {
        return eventDao.findMostRegistered(limit);
    }

    // La fecha no puede ser pasada ni estar a mas de MAX_YEARS_AHEAD años. El form (EventDateValidator) lo valida
    // para dar feedback; esto es la regla del service. Una fecha null la rechaza la base (NOT NULL).
    private static void validateEventDate(final EventDTO dto) {
        final Date eventDate = dto.getEventDate();
        if (eventDate == null) {
            return;
        }
        final Calendar limit = Calendar.getInstance();
        limit.add(Calendar.YEAR, MAX_YEARS_AHEAD);
        if (eventDate.before(new Date())) {
            throw new InvalidEventException(InvalidEventException.Reason.DATE_IN_PAST, "eventDate");
        }
        if (eventDate.after(limit.getTime())) {
            throw new InvalidEventException(InvalidEventException.Reason.DATE_TOO_FAR, "eventDate");
        }
    }

    // Un evento tiene al menos una categoria (ver tambien CategoriasValidator, que lo valida en el form).
    private static void validateHasCategories(final EventDTO dto) {
        if (dto.getCategories() == null || dto.getCategories().isEmpty()) {
            throw new InvalidEventException(InvalidEventException.Reason.NO_CATEGORY, "categories");
        }
    }

    // insertCategories()/insertImages() (ver EventJdbcDao.create()) son las dos
    // unicas FK que puede violar este insert: category_id contra una categoria
    // que no existe, o image_id contra una imagen que ya no esta (subida y
    // despues borrada entre medio, caso rarisimo). Antes se asumia que
    // CUALQUIER violacion era la categoria; esto distingue mirando el nombre
    // de la constraint en el mensaje de la excepcion (event_category_...),
    // que Postgres arma solo con esa convencion a partir del nombre de tabla
    // y columna.
    // El DTO trae las categorias elegidas como numeros (EventCategoryInfo: id + texto); el evento
    // las guarda como relaciones con objetos Category. Se buscan todas juntas (hay pocas y fijas,
    // ver data.sql) y se arma una EventCategory por cada una. Una que no existe es un dato
    // invalido, igual que antes cuando fallaba la clave foranea.
    private void addCategories(final Event event, final List<EventCategoryInfo> chosen) {
        // (validateHasCategories() ya exigio al menos una antes de llegar aca.)
        if (chosen == null || chosen.isEmpty()) {
            return;
        }
        final Map<Long, Category> byId = new HashMap<>();
        for (final Category category : categoryDao.findAll()) {
            byId.put(category.getId(), category);
        }
        for (final EventCategoryInfo info : chosen) {
            final Category category = byId.get(info.getCategoryId());
            if (category == null) {
                throw new InvalidEventException(InvalidEventException.Reason.CATEGORY_NOT_FOUND, "categories");
            }
            event.addCategory(new EventCategory(event, category, info.getExtraInfo()));
        }
    }

    private static boolean categoryConstraintViolated(final DataIntegrityViolationException e) {
        final Throwable cause = e.getMostSpecificCause();
        final String message = cause == null ? null : cause.getMessage();
        return message != null && message.toLowerCase(java.util.Locale.ROOT).contains("event_category");
    }

    // Mail: solo a los confirmados (son los que tenian un lugar que pierden).
    // Bandeja: tambien a quien estaba en lista de espera o esperando que lo
    // acepten, que si no se enteraria recien al ver el evento. Al rechazado no:
    // ese ya no esperaba nada de este evento. Comun a cancel() y removeByAdmin():
    // para quien esta anotado el motivo (el organizador o un admin) no cambia
    // el aviso que le corresponde.
    private void notifyRegistrantsOfCancellation(final Event event) {
        for (final Registration registration : registrationService.findByEventId(event.getId())) {
            if (registration.isConfirmed()) {
                notificationService.notifyEventCancelled(event, registration);
            }
            if (registration.getStatus() != RegistrationStatus.REJECTED) {
                inboxService.add(registration.getEmail(), InboxMessageType.EVENT_CANCELLED, event.getId(), null, null);
            }
        }
    }
}
